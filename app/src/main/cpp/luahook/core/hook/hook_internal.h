#pragma once

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "luahook/common/status.h"
#include "luahook/common/types.h"
#include "luahook/core/abi/call_frame.h"
#include "luahook/core/abi/native_signature.h"
#include "luahook/core/api/hook.h"
#include "luahook/core/hook/trampoline_symbols.h"

struct HookNode {
  uint32_t id;
  LhHookCallbacks callbacks;
  void *userdata;
};

struct TargetEntry {
  struct LhEngine *engine;
  LhAddr target;
  LhNativeSignature signature;
  std::vector<LhNativeType> arg_types;
  std::weak_ptr<TargetEntry> self;
  std::vector<std::shared_ptr<HookNode>> hooks;
  void *original;
  void *trampoline;
  void *thunk_block;
  size_t thunk_block_size;
  bool signature_initialized;
  bool installed;
};

struct LhEngine {
  std::mutex mutex;
  uint32_t next_handle;
  std::unordered_map<LhAddr, std::shared_ptr<TargetEntry>> active_targets;
  std::unordered_map<uint64_t, std::shared_ptr<TargetEntry>> handle_index;
  std::vector<std::shared_ptr<TargetEntry>> retired_targets;
};

extern "C" {
void *lh_hook_bridge_enter_arm64(void *entry_ptr, void *ctx_ptr);
void lh_hook_bridge_leave_arm64(void *entry_ptr, void *ctx_ptr);
void *lh_hook_bridge_enter_arm32(void *entry_ptr, void *ctx_ptr);
void lh_hook_bridge_leave_arm32(void *entry_ptr, void *ctx_ptr);
}

bool lh_hook_is_supported_type(LhNativeType type, bool allow_void);
LhStatus lh_hook_validate_signature(const LhNativeSignature *signature);
bool lh_hook_signatures_equal(const TargetEntry *entry,
                              const LhNativeSignature *signature);
LhStatus lh_hook_assign_signature(TargetEntry *entry,
                                  const LhNativeSignature *signature);
void lh_hook_reclaim_retired_entries(LhEngine *engine);
