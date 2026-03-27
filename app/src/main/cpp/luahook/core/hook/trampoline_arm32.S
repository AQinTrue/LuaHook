.syntax unified
.thumb
.text
.align 2

.global lh_hook_arm32_thunk_template_begin
.global lh_hook_arm32_thunk_template_end
.global lh_hook_arm32_thunk_template_entry_literal
.global lh_hook_arm32_thunk_template_trampoline_literal
.global lh_hook_trampoline_common_arm32

.extern lh_hook_bridge_enter_arm32
.extern lh_hook_bridge_leave_arm32

// Frame layout:
//   0x00 .. 0x0f  : r0-r3
//   0x10 .. 0x13  : original sp
//   0x14 .. 0x17  : original r4
//   0x18 .. 0x1b  : original r5
//   0x1c .. 0x1f  : original r6
//   0x20 .. 0x23  : original r7
//   0x24 .. 0x27  : original lr
//   stack args    : current sp + 0x28 (restored to caller sp before original call)

.thumb_func
lh_hook_arm32_thunk_template_begin:
    ldr.w ip, lh_hook_arm32_thunk_template_entry_literal
    ldr.w lr, lh_hook_arm32_thunk_template_trampoline_literal
    bx lr
    nop
    .align 2
lh_hook_arm32_thunk_template_entry_literal:
    .word 0
lh_hook_arm32_thunk_template_trampoline_literal:
    .word 0
lh_hook_arm32_thunk_template_end:

.thumb_func
lh_hook_trampoline_common_arm32:
    sub sp, sp, #40

    str r0, [sp, #0]
    str r1, [sp, #4]
    str r2, [sp, #8]
    str r3, [sp, #12]
    str r4, [sp, #20]
    str r5, [sp, #24]
    str r6, [sp, #28]
    str r7, [sp, #32]
    str lr, [sp, #36]

    mov r5, sp
    add r4, sp, #40
    str r4, [sp, #16]
    mov r6, ip

    mov r0, r6
    mov r1, r5
    bl lh_hook_bridge_enter_arm32
    mov r7, r0

    ldr r0, [r5, #0]
    ldr r1, [r5, #4]
    ldr r2, [r5, #8]
    ldr r3, [r5, #12]
    mov sp, r4
    blx r7

    mov sp, r5
    str r0, [r5, #0]
    str r1, [r5, #4]

    mov r0, r6
    mov r1, r5
    bl lh_hook_bridge_leave_arm32

    ldr r0, [r5, #0]
    ldr r1, [r5, #4]
    ldr r2, [r5, #16]
    ldr ip, [r5, #24]
    ldr r4, [r5, #20]
    ldr r6, [r5, #28]
    ldr r7, [r5, #32]
    ldr lr, [r5, #36]
    mov r5, ip
    mov sp, r2
    bx lr
