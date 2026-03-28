#include "luahook/core/hook/hook_internal.h"

namespace {

bool hook_signature_uses_stack_args(const LhNativeSignature *signature) {
  if (!signature || signature->arg_count == 0 || !signature->arg_types) {
    return false;
  }

#if defined(__aarch64__)
  size_t gpr_index = 0;
  size_t fpr_index = 0;

  for (size_t i = 0; i < signature->arg_count; ++i) {
    const LhNativeType type = signature->arg_types[i];
    if (type == LH_TYPE_F32 || type == LH_TYPE_F64) {
      if (fpr_index < 8) {
        ++fpr_index;
        continue;
      }
      return true;
    }

    if (gpr_index < 8) {
      ++gpr_index;
      continue;
    }
    return true;
  }
  return false;
#else
  return false;
#endif
}

}  // namespace

bool lh_hook_is_supported_type(LhNativeType type, bool allow_void) {
  switch (type) {
    case LH_TYPE_VOID:
      return allow_void;
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

LhStatus lh_hook_validate_signature(const LhNativeSignature *signature) {
  if (!signature) {
    return LH_ERR_INVALID_ARG;
  }
  if ((signature->arg_count > 0 && !signature->arg_types) ||
      !lh_hook_is_supported_type(signature->return_type, true)) {
    return LH_ERR_INVALID_ARG;
  }

  for (size_t i = 0; i < signature->arg_count; ++i) {
    if (!lh_hook_is_supported_type(signature->arg_types[i], false)) {
      return LH_ERR_INVALID_ARG;
    }
  }

  // Current hook trampolines only preserve register-passed arguments across the
  // bridge/original-call path. Reject stack-spilling signatures until the
  // trampoline storage model is redesigned to support them safely.
  if (hook_signature_uses_stack_args(signature)) {
    return LH_ERR_INVALID_ARG;
  }

  return LH_OK;
}

bool lh_hook_signatures_equal(const TargetEntry *entry,
                              const LhNativeSignature *signature) {
  if (!entry || !signature || !entry->signature_initialized) {
    return false;
  }
  if (entry->signature.return_type != signature->return_type ||
      entry->signature.arg_count != signature->arg_count ||
      entry->signature.flags != signature->flags) {
    return false;
  }

  for (size_t i = 0; i < signature->arg_count; ++i) {
    if (entry->arg_types[i] != signature->arg_types[i]) {
      return false;
    }
  }
  return true;
}

LhStatus lh_hook_assign_signature(TargetEntry *entry,
                                  const LhNativeSignature *signature) {
  if (!entry || !signature) {
    return LH_ERR_INVALID_ARG;
  }

  if (signature->arg_count == 0) {
    entry->arg_types.clear();
  } else {
    entry->arg_types.assign(signature->arg_types,
                            signature->arg_types + signature->arg_count);
  }
  entry->signature.return_type = signature->return_type;
  entry->signature.arg_types =
      entry->arg_types.empty() ? nullptr : entry->arg_types.data();
  entry->signature.arg_count = entry->arg_types.size();
  entry->signature.flags = signature->flags;
  entry->signature_initialized = true;
  return LH_OK;
}
