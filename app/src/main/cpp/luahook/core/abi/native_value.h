#pragma once

#include "luahook/common/types.h"
#include "luahook/core/abi/native_type.h"

struct LhNativeValue {
  LhNativeType type;
  LhBits bits;
};
