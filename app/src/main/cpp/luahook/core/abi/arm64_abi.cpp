#include "luahook/core/ffi/invoke_internal.h"

#include <cstring>

namespace {

inline uint64_t encode_register_value(LhNativeType type, LhBits bits) {
  switch (type) {
    case LH_TYPE_I32:
      return static_cast<uint64_t>(static_cast<int64_t>(static_cast<int32_t>(bits)));
    case LH_TYPE_U32:
      return static_cast<uint64_t>(static_cast<uint32_t>(bits));
    case LH_TYPE_I64:
      return static_cast<uint64_t>(static_cast<int64_t>(bits));
    case LH_TYPE_U64:
    case LH_TYPE_PTR:
      return bits;
    default:
      return bits;
  }
}

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

inline bool append_stack_slot(LhPackedInvokeArm64 *packed, uint64_t value) {
  if (packed->stack_count >= (sizeof(packed->stack) / sizeof(packed->stack[0]))) {
    return false;
  }
  packed->stack[packed->stack_count++] = value;
  return true;
}

}  // namespace

LhStatus lh_pack_invoke_arm64(const LhInvokeRequest *request, LhPackedInvokeArm64 *packed) {
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
  size_t fpr_index = 0;

  for (size_t i = 0; i < signature->arg_count; ++i) {
    const LhNativeType type = signature->arg_types[i];
    const LhBits bits = request->args[i].bits;

    if (!is_supported_type(type) || type == LH_TYPE_VOID) {
      return LH_ERR_INVALID_ARG;
    }

    if (type == LH_TYPE_F32 || type == LH_TYPE_F64) {
      const uint64_t raw_bits =
          type == LH_TYPE_F32 ? (bits & 0xFFFFFFFFULL) : static_cast<uint64_t>(bits);
      if (fpr_index < 8) {
        packed->fprs[fpr_index++] = raw_bits;
      } else if (!append_stack_slot(packed, raw_bits)) {
        return LH_ERR_INTERNAL;
      }
      continue;
    }

    const uint64_t raw_bits = encode_register_value(type, bits);
    if (gpr_index < 8) {
      packed->gprs[gpr_index++] = raw_bits;
    } else if (!append_stack_slot(packed, raw_bits)) {
      return LH_ERR_INTERNAL;
    }
  }

  return LH_OK;
}

extern "C" LhStatus lh_invoke_arm64(const LhInvokeRequest *request, LhNativeValue *result) {
  if (!request || !result) {
    return LH_ERR_INVALID_ARG;
  }

  LhPackedInvokeArm64 packed;
  LhStatus status = lh_pack_invoke_arm64(request, &packed);
  if (status != LH_OK) {
    return status;
  }

#if defined(__aarch64__)
  lh_invoke_arm64_exec(&packed, result);
  return LH_OK;
#else
  (void)packed;
  return LH_ERR_UNSUPPORTED_ARCH;
#endif
}
