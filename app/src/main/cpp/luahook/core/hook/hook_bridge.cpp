#include "luahook/core/hook/hook_internal.h"

#include <cstring>
#include <iterator>
#include <vector>

#include "luahook/core/abi/arch.h"

namespace {

struct InFlightHookCall {
  std::shared_ptr<TargetEntry> entry;
  std::vector<std::shared_ptr<HookNode>> hooks;
  void *original;
};

thread_local std::vector<InFlightHookCall> g_in_flight_calls;

inline uint32_t read_u32(const uint8_t *base, size_t offset) {
  uint32_t value = 0;
  std::memcpy(&value, base + offset, sizeof(value));
  return value;
}

inline uint64_t read_u64(const uint8_t *base, size_t offset) {
  uint64_t value = 0;
  std::memcpy(&value, base + offset, sizeof(value));
  return value;
}

inline void write_u32(uint8_t *base, size_t offset, uint32_t value) {
  std::memcpy(base + offset, &value, sizeof(value));
}

inline void write_u64(uint8_t *base, size_t offset, uint64_t value) {
  std::memcpy(base + offset, &value, sizeof(value));
}

inline uint64_t encode_arm64_gpr_value(LhNativeType type, LhBits bits) {
  switch (type) {
    case LH_TYPE_I32:
      return static_cast<uint64_t>(static_cast<int64_t>(static_cast<int32_t>(bits)));
    case LH_TYPE_U32:
      return static_cast<uint64_t>(static_cast<uint32_t>(bits));
    case LH_TYPE_I64:
      return static_cast<uint64_t>(static_cast<int64_t>(bits));
    case LH_TYPE_U64:
    case LH_TYPE_PTR:
      return static_cast<uint64_t>(bits);
    default:
      return static_cast<uint64_t>(bits);
  }
}

inline uint32_t encode_arm32_word_value(LhNativeType type, LhBits bits) {
  switch (type) {
    case LH_TYPE_I32:
      return static_cast<uint32_t>(static_cast<int32_t>(bits));
    case LH_TYPE_U32:
    case LH_TYPE_PTR:
    case LH_TYPE_F32:
      return static_cast<uint32_t>(bits);
    default:
      return static_cast<uint32_t>(bits);
  }
}

bool snapshot_in_flight_call(TargetEntry *entry_ptr, InFlightHookCall *record) {
  if (!entry_ptr || !record) {
    return false;
  }

  std::shared_ptr<TargetEntry> entry = entry_ptr->self.lock();
  if (!entry) {
    return false;
  }

  {
    std::lock_guard<std::mutex> lock(entry->engine->mutex);
    record->entry = entry;
    record->hooks = entry->hooks;
    record->original = entry->original;
  }
  return true;
}

bool pop_in_flight_call(TargetEntry *entry_ptr, InFlightHookCall *record) {
  if (!entry_ptr || !record) {
    return false;
  }

  for (auto it = g_in_flight_calls.rbegin(); it != g_in_flight_calls.rend(); ++it) {
    if (it->entry.get() != entry_ptr) {
      continue;
    }
    *record = std::move(*it);
    g_in_flight_calls.erase(std::next(it).base());
    return true;
  }
  return false;
}

#if defined(__aarch64__)

uint64_t arm64_read_gpr(void *ctx_ptr, size_t index) {
  return read_u64(reinterpret_cast<const uint8_t *>(ctx_ptr),
                  LH_HOOK_ARM64_GPR_SAVE_OFFSET + index * sizeof(uint64_t));
}

void arm64_write_gpr(void *ctx_ptr, size_t index, uint64_t value) {
  write_u64(reinterpret_cast<uint8_t *>(ctx_ptr),
            LH_HOOK_ARM64_GPR_SAVE_OFFSET + index * sizeof(uint64_t), value);
}

uint64_t arm64_read_fpr_bits(void *ctx_ptr, size_t index) {
  return read_u64(reinterpret_cast<const uint8_t *>(ctx_ptr),
                  LH_HOOK_ARM64_Q0_Q7_SAVE_OFFSET + index * 16);
}

void arm64_write_fpr_bits(void *ctx_ptr, size_t index, uint64_t value) {
  write_u64(reinterpret_cast<uint8_t *>(ctx_ptr),
            LH_HOOK_ARM64_Q0_Q7_SAVE_OFFSET + index * 16, value);
}

uint64_t arm64_read_stack_slot(void *ctx_ptr, size_t index) {
  return read_u64(reinterpret_cast<const uint8_t *>(ctx_ptr),
                  LH_HOOK_ARM64_STACK_ARG_BASE_OFFSET + index * sizeof(uint64_t));
}

void arm64_write_stack_slot(void *ctx_ptr, size_t index, uint64_t value) {
  write_u64(reinterpret_cast<uint8_t *>(ctx_ptr),
            LH_HOOK_ARM64_STACK_ARG_BASE_OFFSET + index * sizeof(uint64_t), value);
}

void capture_args(const TargetEntry *entry,
                  void *ctx_ptr,
                  std::vector<LhNativeValue> *args) {
  size_t gpr_index = 0;
  size_t fpr_index = 0;
  size_t stack_index = 0;

  for (size_t i = 0; i < entry->signature.arg_count; ++i) {
    const LhNativeType type = entry->signature.arg_types[i];
    uint64_t bits = 0;
    if (type == LH_TYPE_F32 || type == LH_TYPE_F64) {
      bits = fpr_index < 8 ? arm64_read_fpr_bits(ctx_ptr, fpr_index++)
                           : arm64_read_stack_slot(ctx_ptr, stack_index++);
      if (type == LH_TYPE_F32) {
        bits &= 0xFFFFFFFFULL;
      }
    } else {
      bits = gpr_index < 8 ? arm64_read_gpr(ctx_ptr, gpr_index++)
                           : arm64_read_stack_slot(ctx_ptr, stack_index++);
      if (type == LH_TYPE_I32 || type == LH_TYPE_U32) {
        bits &= 0xFFFFFFFFULL;
      }
    }
    (*args)[i] = {type, bits};
  }
}

void apply_args(const TargetEntry *entry,
                const std::vector<LhNativeValue> &args,
                void *ctx_ptr) {
  size_t gpr_index = 0;
  size_t fpr_index = 0;
  size_t stack_index = 0;

  for (size_t i = 0; i < args.size(); ++i) {
    const LhNativeType type = entry->signature.arg_types[i];
    if (type == LH_TYPE_F32 || type == LH_TYPE_F64) {
      const uint64_t bits = type == LH_TYPE_F32 ? (args[i].bits & 0xFFFFFFFFULL)
                                                : static_cast<uint64_t>(args[i].bits);
      if (fpr_index < 8) {
        arm64_write_fpr_bits(ctx_ptr, fpr_index++, bits);
      } else {
        arm64_write_stack_slot(ctx_ptr, stack_index++, bits);
      }
      continue;
    }

    const uint64_t bits = encode_arm64_gpr_value(type, args[i].bits);
    if (gpr_index < 8) {
      arm64_write_gpr(ctx_ptr, gpr_index++, bits);
    } else {
      arm64_write_stack_slot(ctx_ptr, stack_index++, bits);
    }
  }
}

LhNativeValue capture_return_value(const TargetEntry *entry, void *ctx_ptr) {
  const LhNativeType type = entry->signature.return_type;
  if (type == LH_TYPE_F32 || type == LH_TYPE_F64) {
    uint64_t bits = arm64_read_fpr_bits(ctx_ptr, 0);
    if (type == LH_TYPE_F32) {
      bits &= 0xFFFFFFFFULL;
    }
    return {type, bits};
  }
  if (type == LH_TYPE_I32 || type == LH_TYPE_U32) {
    return {type, arm64_read_gpr(ctx_ptr, 0) & 0xFFFFFFFFULL};
  }
  if (type == LH_TYPE_VOID) {
    return {type, 0};
  }
  return {type, arm64_read_gpr(ctx_ptr, 0)};
}

void apply_return_value(const TargetEntry *entry,
                        const LhNativeValue &value,
                        void *ctx_ptr) {
  const LhNativeType type = entry->signature.return_type;
  if (type == LH_TYPE_VOID) {
    return;
  }
  if (type == LH_TYPE_F32 || type == LH_TYPE_F64) {
    arm64_write_fpr_bits(ctx_ptr, 0,
                         type == LH_TYPE_F32 ? (value.bits & 0xFFFFFFFFULL)
                                             : static_cast<uint64_t>(value.bits));
    return;
  }
  arm64_write_gpr(ctx_ptr, 0, encode_arm64_gpr_value(type, value.bits));
}

#elif defined(__arm__) || defined(__thumb__)

uint32_t arm32_read_gpr(void *ctx_ptr, size_t index) {
  return read_u32(reinterpret_cast<const uint8_t *>(ctx_ptr),
                  LH_HOOK_ARM32_GPR_SAVE_OFFSET + index * sizeof(uint32_t));
}

void arm32_write_gpr(void *ctx_ptr, size_t index, uint32_t value) {
  write_u32(reinterpret_cast<uint8_t *>(ctx_ptr),
            LH_HOOK_ARM32_GPR_SAVE_OFFSET + index * sizeof(uint32_t), value);
}

uint32_t arm32_read_stack_word(void *ctx_ptr, size_t index) {
  return read_u32(reinterpret_cast<const uint8_t *>(ctx_ptr),
                  LH_HOOK_ARM32_STACK_ARG_BASE_OFFSET + index * sizeof(uint32_t));
}

void arm32_write_stack_word(void *ctx_ptr, size_t index, uint32_t value) {
  write_u32(reinterpret_cast<uint8_t *>(ctx_ptr),
            LH_HOOK_ARM32_STACK_ARG_BASE_OFFSET + index * sizeof(uint32_t), value);
}

uint64_t arm32_read_pair(void *ctx_ptr, size_t low_index) {
  const uint32_t low = arm32_read_gpr(ctx_ptr, low_index);
  const uint32_t high = arm32_read_gpr(ctx_ptr, low_index + 1);
  return static_cast<uint64_t>(low) | (static_cast<uint64_t>(high) << 32);
}

void arm32_write_pair(void *ctx_ptr, size_t low_index, uint64_t value) {
  arm32_write_gpr(ctx_ptr, low_index,
                  static_cast<uint32_t>(value & 0xFFFFFFFFULL));
  arm32_write_gpr(ctx_ptr, low_index + 1,
                  static_cast<uint32_t>((value >> 32) & 0xFFFFFFFFULL));
}

uint64_t arm32_read_stack_pair(void *ctx_ptr, size_t low_index) {
  const uint32_t low = arm32_read_stack_word(ctx_ptr, low_index);
  const uint32_t high = arm32_read_stack_word(ctx_ptr, low_index + 1);
  return static_cast<uint64_t>(low) | (static_cast<uint64_t>(high) << 32);
}

void arm32_write_stack_pair(void *ctx_ptr, size_t low_index, uint64_t value) {
  arm32_write_stack_word(ctx_ptr, low_index,
                         static_cast<uint32_t>(value & 0xFFFFFFFFULL));
  arm32_write_stack_word(ctx_ptr, low_index + 1,
                         static_cast<uint32_t>((value >> 32) & 0xFFFFFFFFULL));
}

void capture_args(const TargetEntry *entry,
                  void *ctx_ptr,
                  std::vector<LhNativeValue> *args) {
  size_t gpr_index = 0;
  size_t stack_index = 0;

  for (size_t i = 0; i < entry->signature.arg_count; ++i) {
    const LhNativeType type = entry->signature.arg_types[i];
    if (type == LH_TYPE_I64 || type == LH_TYPE_U64 || type == LH_TYPE_F64) {
      if ((gpr_index & 1u) != 0u && gpr_index < 4) {
        ++gpr_index;
      }
      uint64_t bits = 0;
      if (gpr_index + 1 < 4) {
        bits = arm32_read_pair(ctx_ptr, gpr_index);
        gpr_index += 2;
      } else {
        if ((stack_index & 1u) != 0u) {
          ++stack_index;
        }
        bits = arm32_read_stack_pair(ctx_ptr, stack_index);
        stack_index += 2;
      }
      (*args)[i] = {type, bits};
      continue;
    }

    const uint32_t bits = gpr_index < 4 ? arm32_read_gpr(ctx_ptr, gpr_index++)
                                        : arm32_read_stack_word(ctx_ptr, stack_index++);
    (*args)[i] = {type, bits};
  }
}

void apply_args(const TargetEntry *entry,
                const std::vector<LhNativeValue> &args,
                void *ctx_ptr) {
  size_t gpr_index = 0;
  size_t stack_index = 0;

  for (size_t i = 0; i < args.size(); ++i) {
    const LhNativeType type = entry->signature.arg_types[i];
    if (type == LH_TYPE_I64 || type == LH_TYPE_U64 || type == LH_TYPE_F64) {
      if ((gpr_index & 1u) != 0u && gpr_index < 4) {
        ++gpr_index;
      }
      if (gpr_index + 1 < 4) {
        arm32_write_pair(ctx_ptr, gpr_index, args[i].bits);
        gpr_index += 2;
      } else {
        if ((stack_index & 1u) != 0u) {
          ++stack_index;
        }
        arm32_write_stack_pair(ctx_ptr, stack_index, args[i].bits);
        stack_index += 2;
      }
      continue;
    }

    const uint32_t bits = encode_arm32_word_value(type, args[i].bits);
    if (gpr_index < 4) {
      arm32_write_gpr(ctx_ptr, gpr_index++, bits);
    } else {
      arm32_write_stack_word(ctx_ptr, stack_index++, bits);
    }
  }
}

LhNativeValue capture_return_value(const TargetEntry *entry, void *ctx_ptr) {
  const LhNativeType type = entry->signature.return_type;
  if (type == LH_TYPE_VOID) {
    return {type, 0};
  }
  if (type == LH_TYPE_I64 || type == LH_TYPE_U64 || type == LH_TYPE_F64) {
    return {type, arm32_read_pair(ctx_ptr, 0)};
  }
  return {type, arm32_read_gpr(ctx_ptr, 0)};
}

void apply_return_value(const TargetEntry *entry,
                        const LhNativeValue &value,
                        void *ctx_ptr) {
  const LhNativeType type = entry->signature.return_type;
  if (type == LH_TYPE_VOID) {
    return;
  }
  if (type == LH_TYPE_I64 || type == LH_TYPE_U64 || type == LH_TYPE_F64) {
    arm32_write_pair(ctx_ptr, 0, value.bits);
    return;
  }
  arm32_write_gpr(ctx_ptr, 0, encode_arm32_word_value(type, value.bits));
}

#endif

void *bridge_enter_common(void *entry_ptr, void *ctx_ptr) {
  TargetEntry *entry = reinterpret_cast<TargetEntry *>(entry_ptr);
  if (!entry || !ctx_ptr) {
    return nullptr;
  }

  InFlightHookCall record{};
  if (!snapshot_in_flight_call(entry, &record)) {
    return nullptr;
  }
  g_in_flight_calls.push_back(record);

  if (record.hooks.empty()) {
    return record.original;
  }

  std::vector<LhNativeValue> args(record.entry->signature.arg_count);
  if (!args.empty()) {
    capture_args(record.entry.get(), ctx_ptr, &args);
  }

  LhCallFrame frame{
      lh_current_arch(),
      args.empty() ? nullptr : args.data(),
      args.size(),
      {record.entry->signature.return_type, 0},
  };

  for (const std::shared_ptr<HookNode> &node : record.hooks) {
    if (node && node->callbacks.on_enter) {
      node->callbacks.on_enter(node->userdata, &frame);
    }
  }

  if (!args.empty()) {
    apply_args(record.entry.get(), args, ctx_ptr);
  }
  return record.original;
}

void bridge_leave_common(void *entry_ptr, void *ctx_ptr) {
  TargetEntry *entry = reinterpret_cast<TargetEntry *>(entry_ptr);
  if (!entry || !ctx_ptr) {
    return;
  }

  InFlightHookCall record{};
  if (!pop_in_flight_call(entry, &record)) {
    return;
  }

  LhEngine *engine = record.entry ? record.entry->engine : nullptr;
  {
    const std::shared_ptr<TargetEntry> entry_ref = record.entry;
    if (entry_ref && !record.hooks.empty()) {
      std::vector<LhNativeValue> args(entry_ref->signature.arg_count);
      LhCallFrame frame{
          lh_current_arch(),
          args.empty() ? nullptr : args.data(),
          args.size(),
          capture_return_value(entry_ref.get(), ctx_ptr),
      };

      for (auto it = record.hooks.rbegin(); it != record.hooks.rend(); ++it) {
        const std::shared_ptr<HookNode> &node = *it;
        if (node && node->callbacks.on_leave) {
          node->callbacks.on_leave(node->userdata, &frame);
        }
      }

      apply_return_value(entry_ref.get(), frame.return_value, ctx_ptr);
    }
  }

  record.hooks.clear();
  record.entry.reset();
  if (engine) {
    lh_hook_reclaim_retired_entries(engine);
  }
}

}  // namespace

#if defined(__aarch64__)
extern "C" void *lh_hook_bridge_enter_arm64(void *entry_ptr, void *ctx_ptr) {
  return bridge_enter_common(entry_ptr, ctx_ptr);
}

extern "C" void lh_hook_bridge_leave_arm64(void *entry_ptr, void *ctx_ptr) {
  bridge_leave_common(entry_ptr, ctx_ptr);
}
#elif defined(__arm__) || defined(__thumb__)
extern "C" void *lh_hook_bridge_enter_arm32(void *entry_ptr, void *ctx_ptr) {
  return bridge_enter_common(entry_ptr, ctx_ptr);
}

extern "C" void lh_hook_bridge_leave_arm32(void *entry_ptr, void *ctx_ptr) {
  bridge_leave_common(entry_ptr, ctx_ptr);
}
#endif
