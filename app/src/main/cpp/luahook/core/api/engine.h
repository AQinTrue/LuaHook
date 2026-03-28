#pragma once

#include "luahook/common/status.h"

#ifdef __cplusplus
extern "C" {
#endif

struct LhEngine;

LhEngine *lh_engine_create();
void lh_engine_destroy(LhEngine *engine);

#ifdef __cplusplus
}
#endif
