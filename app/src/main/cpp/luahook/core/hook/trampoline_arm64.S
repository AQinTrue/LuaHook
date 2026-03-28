.text
.align 2

.global lh_hook_arm64_thunk_template_begin
.global lh_hook_arm64_thunk_template_end
.global lh_hook_arm64_thunk_template_entry_literal
.global lh_hook_arm64_thunk_template_trampoline_literal
.global lh_hook_trampoline_common_arm64

.extern lh_hook_bridge_enter_arm64
.extern lh_hook_bridge_leave_arm64

// Frame layout:
//   0x00 .. 0x3f  : x0-x7
//   0x40 .. 0x47  : x8
//   0x48 .. 0x4f  : saved entry pointer
//   0x50 .. 0x5f  : original x29/lr
//   0x60 .. 0xdf  : q0-q7
//   0xe0 .. 0x25f : q8-q31
//   stack args    : current sp + 0x260

lh_hook_arm64_thunk_template_begin:
    ldr x16, 0f
    ldr x17, 1f
    br x17
    .align 3
lh_hook_arm64_thunk_template_entry_literal:
0:
    .8byte 0
lh_hook_arm64_thunk_template_trampoline_literal:
1:
    .8byte 0
lh_hook_arm64_thunk_template_end:

lh_hook_trampoline_common_arm64:
    sub sp, sp, #608

    stp x0, x1, [sp, #0]
    stp x2, x3, [sp, #16]
    stp x4, x5, [sp, #32]
    stp x6, x7, [sp, #48]
    str x8, [sp, #64]
    str x16, [sp, #72]
    stp x29, x30, [sp, #80]
    stp q0, q1, [sp, #96]
    stp q2, q3, [sp, #128]
    stp q4, q5, [sp, #160]
    stp q6, q7, [sp, #192]
    stp q8, q9, [sp, #224]
    stp q10, q11, [sp, #256]
    stp q12, q13, [sp, #288]
    stp q14, q15, [sp, #320]
    stp q16, q17, [sp, #352]
    stp q18, q19, [sp, #384]
    stp q20, q21, [sp, #416]
    stp q22, q23, [sp, #448]
    stp q24, q25, [sp, #480]
    stp q26, q27, [sp, #512]
    stp q28, q29, [sp, #544]
    stp q30, q31, [sp, #576]

    ldr x0, [sp, #72]
    mov x1, sp
    bl lh_hook_bridge_enter_arm64
    mov x9, x0

    ldp x0, x1, [sp, #0]
    ldp x2, x3, [sp, #16]
    ldp x4, x5, [sp, #32]
    ldp x6, x7, [sp, #48]
    ldr x8, [sp, #64]
    ldp q0, q1, [sp, #96]
    ldp q2, q3, [sp, #128]
    ldp q4, q5, [sp, #160]
    ldp q6, q7, [sp, #192]

    blr x9

    str x0, [sp, #0]
    str q0, [sp, #96]

    ldr x0, [sp, #72]
    mov x1, sp
    bl lh_hook_bridge_leave_arm64

    ldr x0, [sp, #0]
    ldr q0, [sp, #96]
    ldp x29, x30, [sp, #80]
    ldp q8, q9, [sp, #224]
    ldp q10, q11, [sp, #256]
    ldp q12, q13, [sp, #288]
    ldp q14, q15, [sp, #320]
    ldp q16, q17, [sp, #352]
    ldp q18, q19, [sp, #384]
    ldp q20, q21, [sp, #416]
    ldp q22, q23, [sp, #448]
    ldp q24, q25, [sp, #480]
    ldp q26, q27, [sp, #512]
    ldp q28, q29, [sp, #544]
    ldp q30, q31, [sp, #576]
    add sp, sp, #608
    ret
