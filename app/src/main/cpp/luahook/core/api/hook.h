#pragma once

#include <stddef.h>
#include <stdint.h>

#include "luahook/common/status.h"
#include "luahook/common/types.h"
#include "luahook/core/abi/call_frame.h"
#include "luahook/core/abi/native_signature.h"

#ifdef __cplusplus
extern "C" {
#endif

struct LhEngine;

struct LhHookSpec {
  LhAddr target;
  const LhNativeSignature *signature;
  uint32_t flags;
  void *userdata;
};

struct LhHookHandle {
  uint64_t id;
};

typedef void (*LhEnterCallback)(void *userdata, LhCallFrame *frame);
typedef void (*LhLeaveCallback)(void *userdata, LhCallFrame *frame);

struct LhHookCallbacks {
  LhEnterCallback on_enter;
  LhLeaveCallback on_leave;
};

LhStatus lh_register_hook(LhEngine *engine,
                          const LhHookSpec *spec,
                          const LhHookCallbacks *callbacks,
                          LhHookHandle *out_handle);
LhStatus lh_unregister_hook(LhEngine *engine, LhHookHandle handle);

#ifdef __cplusplus
}
#endif
