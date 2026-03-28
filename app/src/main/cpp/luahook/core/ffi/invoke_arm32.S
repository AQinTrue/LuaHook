#if defined(__arm__) || defined(__thumb__)
#include "luahook/core/ffi/invoke_asm.h"
.text
.syntax unified
.align 2
.global lh_invoke_arm32_exec
.type lh_invoke_arm32_exec, %function
lh_invoke_arm32_exec:
  push {r4-r8, r10, r11, lr}
  mov r4, r0
  mov r5, r1
  ldr r6, [r4, #LH_ASM_PACKED_INVOKE_ARM32_TARGET]
  ldr r7, [r4, #LH_ASM_PACKED_INVOKE_ARM32_RETURN_TYPE]
  ldr r8, [r4, #LH_ASM_PACKED_INVOKE_ARM32_STACK_COUNT]
  mov r10, r8, lsl #2
  add r10, r10, #7
  bic r10, r10, #7
  cbz r10, 1f
  sub sp, sp, r10
1:
  cbz r8, 2f
  add r11, r4, #LH_ASM_PACKED_INVOKE_ARM32_STACK
  mov r0, sp
  mov r1, r8
3:
  ldr r2, [r11], #4
  str r2, [r0], #4
  subs r1, r1, #1
  bne 3b
2:
  ldr r0, [r4, #LH_ASM_PACKED_INVOKE_ARM32_GPRS]
  ldr r1, [r4, #LH_ASM_PACKED_INVOKE_ARM32_GPRS + 4]
  ldr r2, [r4, #LH_ASM_PACKED_INVOKE_ARM32_GPRS + 8]
  ldr r3, [r4, #LH_ASM_PACKED_INVOKE_ARM32_GPRS + 12]
  blx r6
  cmp r7, #LH_ASM_TYPE_F64
  beq 4f
  cmp r7, #LH_ASM_TYPE_I64
  beq 4f
  cmp r7, #LH_ASM_TYPE_U64
  beq 4f
  cmp r7, #LH_ASM_TYPE_VOID
  beq 5f
  str r0, [r5, #LH_ASM_NATIVE_VALUE_BITS_OFFSET]
  mov r1, #0
  str r1, [r5, #LH_ASM_NATIVE_VALUE_BITS_OFFSET + 4]
  b 6f
4:
  str r0, [r5, #LH_ASM_NATIVE_VALUE_BITS_OFFSET]
  str r1, [r5, #LH_ASM_NATIVE_VALUE_BITS_OFFSET + 4]
  b 6f
5:
  mov r0, #0
  str r0, [r5, #LH_ASM_NATIVE_VALUE_BITS_OFFSET]
  str r0, [r5, #LH_ASM_NATIVE_VALUE_BITS_OFFSET + 4]
6:
  add sp, sp, r10
  pop {r4-r8, r10, r11, pc}
.size lh_invoke_arm32_exec, .-lh_invoke_arm32_exec
#endif
