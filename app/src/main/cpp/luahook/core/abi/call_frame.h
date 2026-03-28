#pragma once

#include <stddef.h>

#include "luahook/core/abi/arch.h"
#include "luahook/core/abi/native_value.h"

struct LhCallFrame {
  LhArch arch;
  LhNativeValue *args;
  size_t arg_count;
  LhNativeValue return_value;
};
