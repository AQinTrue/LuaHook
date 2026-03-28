#include "luahook/core/api/memory.h"

#include <cstdio>
#include <cstring>
#include <sys/mman.h>
#include <sys/uio.h>
#include <unistd.h>

namespace {

static int get_prot_for_addr(uintptr_t addr) {
  FILE *fp = fopen("/proc/self/maps", "r");
  if (!fp) {
    return -1;
  }

  char line[512];
  while (fgets(line, sizeof(line), fp)) {
    unsigned long long start = 0;
    unsigned long long end = 0;
    char perms[5] = {0};
    if (sscanf(line, "%llx-%llx %4s", &start, &end, perms) == 3) {
      if (addr >= static_cast<uintptr_t>(start) && addr < static_cast<uintptr_t>(end)) {
        int prot = 0;
        if (perms[0] == 'r') prot |= PROT_READ;
        if (perms[1] == 'w') prot |= PROT_WRITE;
        if (perms[2] == 'x') prot |= PROT_EXEC;
        fclose(fp);
        return prot;
      }
    }
  }

  fclose(fp);
  return -1;
}

static inline LhAddr add_offset(LhAddr value, int64_t offset) {
  return static_cast<LhAddr>(static_cast<intptr_t>(value) + static_cast<intptr_t>(offset));
}

}  // namespace

LhStatus lh_safe_read(LhAddr address, void *buffer, size_t size) {
  if (!address || !buffer || size == 0) {
    return LH_ERR_INVALID_ARG;
  }

  struct iovec local_iov = {buffer, size};
  struct iovec remote_iov = {reinterpret_cast<void *>(address), size};
  ssize_t nread = process_vm_readv(getpid(), &local_iov, 1, &remote_iov, 1, 0);
  return nread == static_cast<ssize_t>(size) ? LH_OK : LH_ERR_INTERNAL;
}

LhStatus lh_safe_write(LhAddr address, const void *buffer, size_t size) {
  if (!address || !buffer || size == 0) {
    return LH_ERR_INVALID_ARG;
  }

  void *addr = reinterpret_cast<void *>(address);
  long page_size = sysconf(_SC_PAGESIZE);
  if (page_size <= 0) {
    return LH_ERR_INTERNAL;
  }

  uintptr_t start = reinterpret_cast<uintptr_t>(addr);
  uintptr_t page_start = start & ~(static_cast<uintptr_t>(page_size) - 1);
  uintptr_t page_end = (start + size + static_cast<uintptr_t>(page_size) - 1) &
                       ~(static_cast<uintptr_t>(page_size) - 1);
  size_t protect_len = static_cast<size_t>(page_end - page_start);

  int old_prot = get_prot_for_addr(start);
  int new_prot = (old_prot >= 0) ? (old_prot | PROT_WRITE)
                                 : (PROT_READ | PROT_WRITE | PROT_EXEC);

  if (mprotect(reinterpret_cast<void *>(page_start), protect_len, new_prot) != 0) {
    return LH_ERR_INTERNAL;
  }

  memcpy(addr, buffer, size);
  __builtin___clear_cache(reinterpret_cast<char *>(addr),
                          reinterpret_cast<char *>(addr) + size);

  if (old_prot >= 0 &&
      mprotect(reinterpret_cast<void *>(page_start), protect_len, old_prot) != 0) {
    return LH_ERR_INTERNAL;
  }
  return LH_OK;
}

LhStatus lh_read_pointer_chain(LhAddr base, const int64_t *offsets, size_t count, LhAddr *result) {
  if (!base || !result || (count > 0 && !offsets)) {
    return LH_ERR_INVALID_ARG;
  }

  LhAddr current = 0;
  LhStatus status = lh_safe_read(base, &current, sizeof(current));
  if (status != LH_OK) {
    return status;
  }

  for (size_t i = 0; i < count; ++i) {
    if (i + 1 == count) {
      current = add_offset(current, offsets[i]);
      break;
    }

    LhAddr next = 0;
    LhAddr step_addr = add_offset(current, offsets[i]);
    status = lh_safe_read(step_addr, &next, sizeof(next));
    if (status != LH_OK) {
      return status;
    }
    current = next;
  }

  *result = current;
  return LH_OK;
}

