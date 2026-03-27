#include "luahook/core/api/invoke.h"
#include "luahook/core/abi/arch.h"
#include "luahook/core/abi/native_signature.h"
#include "luahook/core/abi/native_value.h"

#if defined(__aarch64__)
extern "C" LhStatus lh_invoke_arm64(const LhInvokeRequest *request, LhNativeValue *result);
#elif defined(__arm__) || defined(__thumb__)
extern "C" LhStatus lh_invoke_arm32(const LhInvokeRequest *request, LhNativeValue *result);
#endif

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

}  // namespace

LhStatus lh_invoke(const LhInvokeRequest *request, LhNativeValue *result) {
  if (!request || !result || !request->signature || request->target == 0) {
    return LH_ERR_INVALID_ARG;
  }

  const LhNativeSignature *signature = request->signature;
  if ((signature->arg_count > 0 && !signature->arg_types) ||
      (signature->arg_count > 0 && !request->args) ||
      !is_supported_type(signature->return_type)) {
    return LH_ERR_INVALID_ARG;
  }

  for (size_t i = 0; i < signature->arg_count; ++i) {
    if (!is_supported_type(signature->arg_types[i]) || signature->arg_types[i] == LH_TYPE_VOID) {
      return LH_ERR_INVALID_ARG;
    }
  }

  result->type = signature->return_type;
  result->bits = 0;

#if defined(__aarch64__)
  return lh_invoke_arm64(request, result);
#elif defined(__arm__) || defined(__thumb__)
  return lh_invoke_arm32(request, result);
#else
  return LH_ERR_UNSUPPORTED_ARCH;
#endif
}
