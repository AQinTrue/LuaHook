#pragma once

enum LhArch {
  LH_ARCH_UNKNOWN = 0,
  LH_ARCH_ARM32 = 1,
  LH_ARCH_ARM64 = 2,
};

inline LhArch lh_current_arch() {
#if defined(__aarch64__)
  return LH_ARCH_ARM64;
#elif defined(__arm__)
  return LH_ARCH_ARM32;
#else
  return LH_ARCH_UNKNOWN;
#endif
}
