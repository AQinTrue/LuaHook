#pragma once

#include <stdint.h>

#include "luahook/core/api/invoke.h"

struct LhPackedInvokeArm64 {
  void *target;
  LhNativeType return_type;
  uint32_t stack_count;
  uint64_t gprs[8];
  uint64_t fprs[8];
  uint64_t stack[32];
};

struct LhPackedInvokeArm32 {
  void *target;
  LhNativeType return_type;
  uint32_t stack_count;
  uint32_t reserved;
  uint32_t gprs[4];
  uint64_t fprs[8];
  uint32_t stack[64];
};

#define LH_PACKED_INVOKE_ARM64_TARGET 0
#define LH_PACKED_INVOKE_ARM64_RETURN_TYPE 8
#define LH_PACKED_INVOKE_ARM64_STACK_COUNT 12
#define LH_PACKED_INVOKE_ARM64_GPRS 16
#define LH_PACKED_INVOKE_ARM64_FPRS 80
#define LH_PACKED_INVOKE_ARM64_STACK 144

#define LH_PACKED_INVOKE_ARM32_TARGET 0
#define LH_PACKED_INVOKE_ARM32_RETURN_TYPE 4
#define LH_PACKED_INVOKE_ARM32_STACK_COUNT 8
#define LH_PACKED_INVOKE_ARM32_RESERVED 12
#define LH_PACKED_INVOKE_ARM32_GPRS 16
#define LH_PACKED_INVOKE_ARM32_FPRS 32
#define LH_PACKED_INVOKE_ARM32_STACK 96

#define LH_NATIVE_VALUE_BITS_OFFSET 8

LhStatus lh_pack_invoke_arm64(const LhInvokeRequest *request, LhPackedInvokeArm64 *packed);
LhStatus lh_pack_invoke_arm32(const LhInvokeRequest *request, LhPackedInvokeArm32 *packed);

#ifdef __cplusplus
extern "C" {
#endif

LhStatus lh_invoke_arm64(const LhInvokeRequest *request, LhNativeValue *result);
LhStatus lh_invoke_arm32(const LhInvokeRequest *request, LhNativeValue *result);

void lh_invoke_arm64_exec(const LhPackedInvokeArm64 *packed, LhNativeValue *result);
void lh_invoke_arm32_exec(const LhPackedInvokeArm32 *packed, LhNativeValue *result);

#ifdef __cplusplus
}
#endif
