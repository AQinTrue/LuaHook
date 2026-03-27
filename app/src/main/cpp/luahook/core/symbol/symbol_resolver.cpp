#include "luahook/core/api/symbol.h"

#include "xdl.h"

LhStatus lh_module_base(const char *name, LhAddr *result) {
  if (!name || !result) {
    return LH_ERR_INVALID_ARG;
  }

  void *handle = xdl_open(name, XDL_DEFAULT);
  if (!handle) {
    return LH_ERR_NOT_FOUND;
  }

  xdl_info_t info;
  LhStatus status = LH_ERR_NOT_FOUND;
  if (xdl_info(handle, XDL_DI_DLINFO, &info) == 0 && info.dli_fbase) {
    *result = reinterpret_cast<LhAddr>(info.dli_fbase);
    status = LH_OK;
  }

  xdl_close(handle);
  return status;
}

LhStatus lh_resolve_symbol(const char *module, const char *symbol, LhAddr *result) {
  if (!module || !symbol || !result) {
    return LH_ERR_INVALID_ARG;
  }

  void *handle = xdl_open(module, XDL_DEFAULT);
  if (!handle) {
    return LH_ERR_NOT_FOUND;
  }

  void *address = xdl_sym(handle, symbol, nullptr);
  if (!address) {
    address = xdl_dsym(handle, symbol, nullptr);
  }

  xdl_close(handle);
  if (!address) {
    return LH_ERR_NOT_FOUND;
  }

  *result = reinterpret_cast<LhAddr>(address);
  return LH_OK;
}
