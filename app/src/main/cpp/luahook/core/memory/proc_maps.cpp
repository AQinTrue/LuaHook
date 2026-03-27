#include "luahook/core/api/symbol.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace {

static inline LhAddr parse_map_start(const char *line) {
  char *end = nullptr;
  return static_cast<LhAddr>(strtoull(line, &end, 16));
}

}  // namespace

LhStatus lh_find_map_base(const char *module_expr, const char *field, LhAddr *result) {
  if (!module_expr || !field || !result) {
    return LH_ERR_INVALID_ARG;
  }

  char *tmp = strdup(module_expr);
  if (!tmp) {
    return LH_ERR_INTERNAL;
  }

  const char *mod_name = strtok(tmp, ":");
  const char *isbss = strtok(nullptr, ":");
  if (!mod_name) {
    free(tmp);
    return LH_ERR_NOT_FOUND;
  }

  FILE *fp = fopen("/proc/self/maps", "r");
  if (!fp) {
    free(tmp);
    return LH_ERR_INTERNAL;
  }

  char line[512];
  bool matched = false;
  LhAddr base = 0;
  while (fgets(line, sizeof(line), fp)) {
    if (strstr(line, mod_name) && strstr(line, field)) {
      matched = true;
      if (!isbss) {
        base = parse_map_start(line);
        break;
      }
    }
    if (matched && strstr(line, "[anon:.bss]")) {
      base = parse_map_start(line);
      break;
    }
  }

  fclose(fp);
  free(tmp);

  if (!base) {
    return LH_ERR_NOT_FOUND;
  }

  *result = base;
  return LH_OK;
}
