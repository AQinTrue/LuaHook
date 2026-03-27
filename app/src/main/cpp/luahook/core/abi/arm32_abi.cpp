#include "luahook/core/ffi/invoke_internal.h"
#include <cstring>
namespace {
inline bool is_supported_type(LhNativeType type) {
  switch (type) {
    case LH_TYPE_VOID:
    case LH_TYPE_I32:
    case LH_TYPE_U32:
    case LH_TYPE_I64:
    case LH_TYPE_U64:
    case LH_TYPE_PTR:
    case LH_TYPE_F32:
    case LH_TYPE_F64:
      return true;
    default:
      return false;
  }
}
inline uint32_t encode_word_value(LhNativeType type, LhBits bits) {
  switch (type) {
    case LH_TYPE_I32:
      return static_cast<uint32_t>(static_cast<int32_t>(bits));
    case LH_TYPE_U32:
    case LH_TYPE_PTR:
    case LH_TYPE_F32:
      return static_cast<uint32_t>(bits);
    default:
      return static_cast<uint32_t>(bits);
  }
}
inline bool append_stack_word(LhPackedInvokeArm32 *packed, uint32_t word) {
  if (packed->stack_count >= (sizeof(packed->stack) / sizeof(packed->stack[0]))) {
    return false;
  }
  packed->stack[packed->stack_count++] = word;
  return true;
}
inline bool align_stack_u64(LhPackedInvokeArm32 *packed) {
  if ((packed->stack_count & 1u) != 0u) {
    return append_stack_word(packed, 0);
  }
  return true;
}
inline bool append_stack_u64(LhPackedInvokeArm32 *packed, uint64_t value) {
  if (!align_stack_u64(packed)) {
    return false;
  }
  const uint32_t low = static_cast<uint32_t>(value & 0xFFFFFFFFULL);
  const uint32_t high = static_cast<uint32_t>((value >> 32) & 0xFFFFFFFFULL);
  return append_stack_word(packed, low) && append_stack_word(packed, high);
}
inline bool append_gpr_or_stack_word(LhPackedInvokeArm32 *packed,
                                     size_t *gpr_index,
                                     uint32_t word) {
  if (*gpr_index < 4) {
    packed->gprs[(*gpr_index)++] = word;
    return true;
  }
  return append_stack_word(packed, word);
}
inline bool append_gpr_or_stack_u64(LhPackedInvokeArm32 *packed,
                                    size_t *gpr_index,
                                    uint64_t value) {
  if (((*gpr_index) & 1u) != 0u && *gpr_index < 4) {
    ++(*gpr_index);
  }
  if (*gpr_index + 1 < 4) {
    packed->gprs[(*gpr_index)++] = static_cast<uint32_t>(value & 0xFFFFFFFFULL);
    packed->gprs[(*gpr_index)++] = static_cast<uint32_t>((value >> 32) & 0xFFFFFFFFULL);
    return true;
  }
  return append_stack_u64(packed, value);
}
}  // namespace
LhStatus lh_pack_invoke_arm32(const LhInvokeRequest *request, LhPackedInvokeArm32 *packed) {
  if (!request || !packed || !request->signature) {
    return LH_ERR_INVALID_ARG;
  }
  const LhNativeSignature *signature = request->signature;
  if ((signature->arg_count > 0 && !signature->arg_types) ||
      (signature->arg_count > 0 && !request->args) ||
      !is_supported_type(signature->return_type)) {
    return LH_ERR_INVALID_ARG;
  }
  std::memset(packed, 0, sizeof(*packed));
  packed->target = reinterpret_cast<void *>(request->target);
  packed->return_type = signature->return_type;
  size_t gpr_index = 0;
  for (size_t i = 0; i < signature->arg_count; ++i) {
    const LhNativeType type = signature->arg_types[i];
    const LhBits bits = request->args[i].bits;
    if (!is_supported_type(type) || type == LH_TYPE_VOID) {
      return LH_ERR_INVALID_ARG;
    }
    if (type == LH_TYPE_I64 || type == LH_TYPE_U64 || type == LH_TYPE_F64) {
      if (!append_gpr_or_stack_u64(packed, &gpr_index, static_cast<uint64_t>(bits))) {
        return LH_ERR_INTERNAL;
      }
      continue;
    }
    if (!append_gpr_or_stack_word(packed, &gpr_index, encode_word_value(type, bits))) {
      return LH_ERR_INTERNAL;
    }
  }
  return LH_OK;
}
extern "C" LhStatus lh_invoke_arm32(const LhInvokeRequest *request, LhNativeValue *result) {
  if (!request || !result) {
    return LH_ERR_INVALID_ARG;
  }

  LhPackedInvokeArm32 packed;
  LhStatus status = lh_pack_invoke_arm32(request, &packed);
  if (status != LH_OK) {
    return status;
  }

#if defined(__arm__) || defined(__thumb__)
  lh_invoke_arm32_exec(&packed, result);
  return LH_OK;
#else
  (void)packed;
  return LH_ERR_UNSUPPORTED_ARCH;
#endif
}
