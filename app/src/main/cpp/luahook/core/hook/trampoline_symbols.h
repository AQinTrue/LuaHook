#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

extern unsigned char lh_hook_arm64_thunk_template_begin[];
extern unsigned char lh_hook_arm64_thunk_template_end[];
extern unsigned char lh_hook_arm64_thunk_template_entry_literal[];
extern unsigned char lh_hook_arm64_thunk_template_trampoline_literal[];

extern unsigned char lh_hook_arm32_thunk_template_begin[];
extern unsigned char lh_hook_arm32_thunk_template_end[];
extern unsigned char lh_hook_arm32_thunk_template_entry_literal[];
extern unsigned char lh_hook_arm32_thunk_template_trampoline_literal[];

extern void lh_hook_trampoline_common_arm64(void);
extern void lh_hook_trampoline_common_arm32(void);

#ifdef __cplusplus
}
#endif

enum : size_t {
  LH_HOOK_ARM64_FRAME_SIZE = 608,
  LH_HOOK_ARM64_GPR_SAVE_OFFSET = 0,
  LH_HOOK_ARM64_Q0_Q7_SAVE_OFFSET = 96,
  LH_HOOK_ARM64_STACK_ARG_BASE_OFFSET = 608,
};

enum : size_t {
  LH_HOOK_ARM32_FRAME_SIZE = 40,
  LH_HOOK_ARM32_GPR_SAVE_OFFSET = 0,
  LH_HOOK_ARM32_ORIG_SP_OFFSET = 16,
  LH_HOOK_ARM32_STACK_ARG_BASE_OFFSET = 40,
};
