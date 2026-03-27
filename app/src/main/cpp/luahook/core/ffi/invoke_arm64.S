#if defined(__aarch64__)
#include "luahook/core/ffi/invoke_asm.h"

.text
.align 2
.global lh_invoke_arm64_exec
.type lh_invoke_arm64_exec, %function
lh_invoke_arm64_exec:
  stp x29, x30, [sp, #-16]!
  stp x19, x20, [sp, #-16]!
  stp x21, x22, [sp, #-16]!
  stp x23, x24, [sp, #-16]!
  mov x29, sp

  mov x19, x0
  mov x20, x1
  ldr x21, [x19, #LH_ASM_PACKED_INVOKE_ARM64_TARGET]
  ldr w22, [x19, #LH_ASM_PACKED_INVOKE_ARM64_RETURN_TYPE]
  ldr w23, [x19, #LH_ASM_PACKED_INVOKE_ARM64_STACK_COUNT]

  add x24, x23, #1
  bic x24, x24, #1
  lsl x24, x24, #3
  cbz x24, 1f
  sub sp, sp, x24
1:
  cbz x23, 2f
  add x9, x19, #LH_ASM_PACKED_INVOKE_ARM64_STACK
  mov x10, sp
  mov x11, x23
3:
  ldr x12, [x9], #8
  str x12, [x10], #8
  subs x11, x11, #1
  b.ne 3b
2:
  ldp x0, x1, [x19, #LH_ASM_PACKED_INVOKE_ARM64_GPRS]
  ldp x2, x3, [x19, #32]
  ldp x4, x5, [x19, #48]
  ldp x6, x7, [x19, #64]

  ldp d0, d1, [x19, #LH_ASM_PACKED_INVOKE_ARM64_FPRS]
  ldp d2, d3, [x19, #96]
  ldp d4, d5, [x19, #112]
  ldp d6, d7, [x19, #128]

  blr x21

  cmp w22, #LH_ASM_TYPE_F32
  b.eq 4f
  cmp w22, #LH_ASM_TYPE_F64
  b.eq 4f
  cmp w22, #LH_ASM_TYPE_VOID
  b.eq 5f
  str x0, [x20, #LH_ASM_NATIVE_VALUE_BITS_OFFSET]
  b 6f
4:
  str d0, [x20, #LH_ASM_NATIVE_VALUE_BITS_OFFSET]
  b 6f
5:
  str xzr, [x20, #LH_ASM_NATIVE_VALUE_BITS_OFFSET]
6:
  add sp, sp, x24
  ldp x23, x24, [sp], #16
  ldp x21, x22, [sp], #16
  ldp x19, x20, [sp], #16
  ldp x29, x30, [sp], #16
  ret

.size lh_invoke_arm64_exec, .-lh_invoke_arm64_exec
#endif
