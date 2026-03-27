#pragma once

#include <stddef.h>
#include <stdint.h>

#include "luahook/common/status.h"
#include "luahook/common/types.h"

#ifdef __cplusplus
extern "C" {
#endif

LhStatus lh_safe_read(LhAddr address, void *buffer, size_t size);
LhStatus lh_safe_write(LhAddr address, const void *buffer, size_t size);
LhStatus lh_read_pointer_chain(LhAddr base, const int64_t *offsets, size_t count, LhAddr *result);

#ifdef __cplusplus
}
#endif
