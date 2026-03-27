#pragma once

#include "luahook/common/status.h"
#include "luahook/common/types.h"

#ifdef __cplusplus
extern "C" {
#endif

LhStatus lh_module_base(const char *name, LhAddr *result);
LhStatus lh_resolve_symbol(const char *module, const char *symbol, LhAddr *result);
LhStatus lh_find_map_base(const char *module_expr, const char *field, LhAddr *result);

#ifdef __cplusplus
}
#endif
