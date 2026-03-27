#include "luahook/core/api/engine.h"
#include "luahook/core/hook/hook_internal.h"

#include <algorithm>
#include <cstring>
#include <new>

#include <sys/mman.h>
#include <unistd.h>

#include "dobby.h"
#include "luahook/core/abi/arch.h"

namespace {

size_t page_size() {
  static const size_t kPageSize = []() -> size_t {
    long raw = sysconf(_SC_PAGESIZE);
    return raw > 0 ? static_cast<size_t>(raw) : static_cast<size_t>(4096);
  }();
  return kPageSize;
}

size_t align_to_page(size_t size) {
  const size_t kPageSize = page_size();
  return ((size + kPageSize - 1) / kPageSize) * kPageSize;
}

const uint8_t *current_thunk_begin() {
#if defined(__aarch64__)
  return lh_hook_arm64_thunk_template_begin;
#elif defined(__arm__) || defined(__thumb__)
  return lh_hook_arm32_thunk_template_begin;
#else
  return nullptr;
#endif
}

const uint8_t *current_thunk_end() {
#if defined(__aarch64__)
  return lh_hook_arm64_thunk_template_end;
#elif defined(__arm__) || defined(__thumb__)
  return lh_hook_arm32_thunk_template_end;
#else
  return nullptr;
#endif
}

size_t current_entry_literal_offset() {
#if defined(__aarch64__)
  return static_cast<size_t>(lh_hook_arm64_thunk_template_entry_literal -
                             lh_hook_arm64_thunk_template_begin);
#elif defined(__arm__) || defined(__thumb__)
  return static_cast<size_t>(lh_hook_arm32_thunk_template_entry_literal -
                             lh_hook_arm32_thunk_template_begin);
#else
  return 0;
#endif
}

size_t current_trampoline_literal_offset() {
#if defined(__aarch64__)
  return static_cast<size_t>(lh_hook_arm64_thunk_template_trampoline_literal -
                             lh_hook_arm64_thunk_template_begin);
#elif defined(__arm__) || defined(__thumb__)
  return static_cast<size_t>(lh_hook_arm32_thunk_template_trampoline_literal -
                             lh_hook_arm32_thunk_template_begin);
#else
  return 0;
#endif
}

uintptr_t current_common_trampoline_literal() {
#if defined(__aarch64__)
  return reinterpret_cast<uintptr_t>(lh_hook_trampoline_common_arm64);
#elif defined(__arm__) || defined(__thumb__)
  return reinterpret_cast<uintptr_t>(lh_hook_trampoline_common_arm32) | 1u;
#else
  return 0;
#endif
}

LhStatus allocate_trampoline(TargetEntry *entry) {
  if (!entry) {
    return LH_ERR_INVALID_ARG;
  }
  if (entry->trampoline) {
    return LH_OK;
  }

  const uint8_t *begin = current_thunk_begin();
  const uint8_t *end = current_thunk_end();
  const uintptr_t common_literal = current_common_trampoline_literal();
  if (!begin || !end || common_literal == 0) {
    return LH_ERR_UNSUPPORTED_ARCH;
  }

  const size_t thunk_size = static_cast<size_t>(end - begin);
  const size_t entry_literal_offset = current_entry_literal_offset();
  const size_t trampoline_literal_offset = current_trampoline_literal_offset();
  if (thunk_size == 0 || entry_literal_offset + sizeof(uintptr_t) > thunk_size ||
      trampoline_literal_offset + sizeof(uintptr_t) > thunk_size) {
    return LH_ERR_INTERNAL;
  }

  const size_t block_size = align_to_page(thunk_size);
  void *block = mmap(nullptr, block_size, PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (block == MAP_FAILED) {
    return LH_ERR_INTERNAL;
  }

  std::memcpy(block, begin, thunk_size);
#if defined(__aarch64__)
  const uintptr_t entry_literal = reinterpret_cast<uintptr_t>(entry);
  std::memcpy(reinterpret_cast<uint8_t *>(block) + entry_literal_offset,
              &entry_literal, sizeof(entry_literal));
  std::memcpy(reinterpret_cast<uint8_t *>(block) + trampoline_literal_offset,
              &common_literal, sizeof(common_literal));
#elif defined(__arm__) || defined(__thumb__)
  const uint32_t entry_literal =
      static_cast<uint32_t>(reinterpret_cast<uintptr_t>(entry));
  const uint32_t trampoline_literal = static_cast<uint32_t>(common_literal);
  std::memcpy(reinterpret_cast<uint8_t *>(block) + entry_literal_offset,
              &entry_literal, sizeof(entry_literal));
  std::memcpy(reinterpret_cast<uint8_t *>(block) + trampoline_literal_offset,
              &trampoline_literal, sizeof(trampoline_literal));
#endif

  __builtin___clear_cache(reinterpret_cast<char *>(block),
                          reinterpret_cast<char *>(block) + thunk_size);
  if (mprotect(block, block_size, PROT_READ | PROT_EXEC) != 0) {
    munmap(block, block_size);
    return LH_ERR_INTERNAL;
  }

  entry->trampoline = block;
  entry->thunk_block = block;
  entry->thunk_block_size = block_size;
  return LH_OK;
}

void release_trampoline(TargetEntry *entry) {
  if (!entry || !entry->thunk_block) {
    return;
  }
  munmap(entry->thunk_block, entry->thunk_block_size);
  entry->thunk_block = nullptr;
  entry->thunk_block_size = 0;
  entry->trampoline = nullptr;
}

void destroy_entry(TargetEntry *entry) {
  if (!entry) {
    return;
  }
  if (entry->installed) {
    DobbyDestroy(reinterpret_cast<void *>(entry->target));
    entry->installed = false;
  }
  release_trampoline(entry);
}

void reclaim_retired_entries_locked(LhEngine *engine) {
  if (!engine) {
    return;
  }

  auto it = engine->retired_targets.begin();
  while (it != engine->retired_targets.end()) {
    if (!*it) {
      it = engine->retired_targets.erase(it);
      continue;
    }
    if (it->use_count() == 1) {
      destroy_entry(it->get());
      it = engine->retired_targets.erase(it);
      continue;
    }
    ++it;
  }
}

void erase_active_entry_locked(LhEngine *engine,
                               const std::shared_ptr<TargetEntry> &entry) {
  if (!engine || !entry) {
    return;
  }

  auto active_it = engine->active_targets.find(entry->target);
  if (active_it != engine->active_targets.end() &&
      active_it->second.get() == entry.get()) {
    engine->active_targets.erase(active_it);
  }
}

void discard_inactive_entry_locked(LhEngine *engine,
                                   const std::shared_ptr<TargetEntry> &entry) {
  if (!engine || !entry) {
    return;
  }

  erase_active_entry_locked(engine, entry);
  destroy_entry(entry.get());
}

std::shared_ptr<TargetEntry> create_active_entry(LhEngine *engine, LhAddr target) {
  std::shared_ptr<TargetEntry> entry(new (std::nothrow) TargetEntry());
  if (!entry) {
    return nullptr;
  }

  entry->engine = engine;
  entry->target = target;
  entry->signature = {LH_TYPE_VOID, nullptr, 0, 0};
  entry->arg_types.clear();
  entry->self = entry;
  entry->hooks.clear();
  entry->original = nullptr;
  entry->trampoline = nullptr;
  entry->thunk_block = nullptr;
  entry->thunk_block_size = 0;
  entry->signature_initialized = false;
  entry->installed = false;

  engine->active_targets[target] = entry;
  return entry;
}

void retire_entry_locked(LhEngine *engine,
                         const std::shared_ptr<TargetEntry> &entry) {
  if (!engine || !entry) {
    return;
  }

  erase_active_entry_locked(engine, entry);
  engine->retired_targets.push_back(entry);
}

}  // namespace

void lh_hook_reclaim_retired_entries(LhEngine *engine) {
  if (!engine) {
    return;
  }

  std::lock_guard<std::mutex> lock(engine->mutex);
  reclaim_retired_entries_locked(engine);
}

extern "C" LhEngine *lh_engine_create() {
  LhEngine *engine = new (std::nothrow) LhEngine();
  if (!engine) {
    return nullptr;
  }
  engine->next_handle = 1;
  return engine;
}

extern "C" void lh_engine_destroy(LhEngine *engine) {
  if (!engine) {
    return;
  }

  {
    std::lock_guard<std::mutex> lock(engine->mutex);
    for (auto &entry : engine->active_targets) {
      destroy_entry(entry.second.get());
    }
    for (auto &entry : engine->retired_targets) {
      destroy_entry(entry.get());
    }
    engine->handle_index.clear();
    engine->active_targets.clear();
    engine->retired_targets.clear();
  }

  delete engine;
}

extern "C" LhStatus lh_register_hook(LhEngine *engine,
                                      const LhHookSpec *spec,
                                      const LhHookCallbacks *callbacks,
                                      LhHookHandle *out_handle) {
  if (!engine || !spec || !callbacks || !out_handle || spec->target == 0) {
    return LH_ERR_INVALID_ARG;
  }

  LhStatus signature_status = lh_hook_validate_signature(spec->signature);
  if (signature_status != LH_OK) {
    return signature_status;
  }

  std::lock_guard<std::mutex> lock(engine->mutex);
  reclaim_retired_entries_locked(engine);

  bool created = false;
  std::shared_ptr<TargetEntry> entry;
  auto active_it = engine->active_targets.find(spec->target);
  if (active_it == engine->active_targets.end()) {
    entry = create_active_entry(engine, spec->target);
    created = true;
  } else {
    entry = active_it->second;
  }
  if (!entry) {
    return LH_ERR_INTERNAL;
  }

  if (entry->signature_initialized) {
    if (!lh_hook_signatures_equal(entry.get(), spec->signature)) {
      if (created) {
        discard_inactive_entry_locked(engine, entry);
      }
      return LH_ERR_INVALID_ARG;
    }
  } else {
    LhStatus assign_status = lh_hook_assign_signature(entry.get(), spec->signature);
    if (assign_status != LH_OK) {
      if (created) {
        discard_inactive_entry_locked(engine, entry);
      }
      return assign_status;
    }
  }

  LhStatus trampoline_status = allocate_trampoline(entry.get());
  if (trampoline_status != LH_OK) {
    if (entry->hooks.empty() && !entry->installed) {
      discard_inactive_entry_locked(engine, entry);
    }
    return trampoline_status;
  }

  uint32_t handle_id = engine->next_handle++;
  if (handle_id == 0) {
    handle_id = engine->next_handle++;
  }

  std::shared_ptr<HookNode> node(new (std::nothrow) HookNode());
  if (!node) {
    if (entry->hooks.empty() && !entry->installed) {
      discard_inactive_entry_locked(engine, entry);
    }
    return LH_ERR_INTERNAL;
  }
  node->id = handle_id;
  node->callbacks = *callbacks;
  node->userdata = spec->userdata;

  if (entry->hooks.empty() && !entry->installed) {
    dobby_dummy_func_t original = nullptr;
    int rc = DobbyHook(reinterpret_cast<void *>(entry->target),
                       reinterpret_cast<dobby_dummy_func_t>(entry->trampoline),
                       &original);
    if (rc != 0) {
      if (entry->hooks.empty() && !entry->installed) {
        discard_inactive_entry_locked(engine, entry);
      }
      return LH_ERR_HOOK_FAILED;
    }
    entry->original = reinterpret_cast<void *>(original);
    entry->installed = true;
  }

  entry->hooks.push_back(node);
  engine->handle_index[node->id] = entry;
  out_handle->id = node->id;
  reclaim_retired_entries_locked(engine);
  return LH_OK;
}

extern "C" LhStatus lh_unregister_hook(LhEngine *engine, LhHookHandle handle) {
  if (!engine || handle.id == 0) {
    return LH_ERR_INVALID_ARG;
  }

  std::lock_guard<std::mutex> lock(engine->mutex);
  reclaim_retired_entries_locked(engine);

  auto handle_it = engine->handle_index.find(handle.id);
  if (handle_it == engine->handle_index.end()) {
    return LH_ERR_NOT_FOUND;
  }

  std::shared_ptr<TargetEntry> entry = handle_it->second;
  if (!entry) {
    engine->handle_index.erase(handle_it);
    reclaim_retired_entries_locked(engine);
    return LH_ERR_NOT_FOUND;
  }

  auto node_it = std::find_if(entry->hooks.begin(), entry->hooks.end(),
                              [handle](const std::shared_ptr<HookNode> &node) {
                                return node && node->id == handle.id;
                              });
  if (node_it == entry->hooks.end()) {
    engine->handle_index.erase(handle_it);
    reclaim_retired_entries_locked(engine);
    return LH_ERR_NOT_FOUND;
  }

  const bool is_last_hook = entry->hooks.size() == 1;
  if (is_last_hook && entry->installed) {
    int rc = DobbyDestroy(reinterpret_cast<void *>(entry->target));
    if (rc != 0) {
      return LH_ERR_HOOK_FAILED;
    }
    entry->installed = false;
  }

  entry->hooks.erase(node_it);
  engine->handle_index.erase(handle_it);
  if (!is_last_hook) {
    reclaim_retired_entries_locked(engine);
    return LH_OK;
  }

  retire_entry_locked(engine, entry);
  entry.reset();
  reclaim_retired_entries_locked(engine);
  return LH_OK;
}
