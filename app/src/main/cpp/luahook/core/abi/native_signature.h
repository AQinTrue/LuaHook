#pragma once

#include <stddef.h>
#include <stdint.h>

#include "luahook/core/abi/native_type.h"

// Non-owning signature descriptor. The caller owns the argument-type storage.
struct LhNativeSignature {
  LhNativeType return_type;
  const LhNativeType *arg_types;
  size_t arg_count;
  uint32_t flags;
};
