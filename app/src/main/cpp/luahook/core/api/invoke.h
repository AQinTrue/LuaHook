#pragma once

#include <stddef.h>

#include "luahook/common/status.h"
#include "luahook/common/types.h"
#include "luahook/core/abi/native_signature.h"
#include "luahook/core/abi/native_value.h"

#ifdef __cplusplus
extern "C" {
#endif

struct LhInvokeRequest {
  LhAddr target;
  // Borrowed for the duration of the invoke call.
  const LhNativeSignature *signature;
  // Argument storage in signature order; count is defined by signature->arg_count.
  const LhNativeValue *args;
};

LhStatus lh_invoke(const LhInvokeRequest *request, LhNativeValue *result);

#ifdef __cplusplus
}
#endif
