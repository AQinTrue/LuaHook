#include <jni.h>
#include <string>
#include <vector>
#include <sys/uio.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <pthread.h>
#include <cstdio>
#include <cstdlib>
#include <cctype>
#include <algorithm>
#include "dobby.h"

#define LOG_TAG "LuaHookNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Hook 数量上限，对应下方的桩生成数量
#define MAX_HOOKS 50
#define MAX_STACK_ARGS 16

// 全局变量
static JavaVM *g_jvm = nullptr;
static jobject g_nativeLibObj = nullptr;
extern "C" {
void *g_orig_funcs[MAX_HOOKS]; // 存放原函数地址
}

// Return type config per hook
// 0: int/ptr (x0/r0), 1: float/double (q0/d0)
enum HookRetType {
    RET_INT = 0, RET_FLOAT = 1, RET_DOUBLE = 3, RET_VOID = 4
};
extern "C" {
int g_ret_types[MAX_HOOKS];
}
extern "C" {
int g_stack_counts[MAX_HOOKS];
}
static int g_current_hook_idx = 0;

// 线程清理 Key
static pthread_key_t g_thread_key;
static pthread_mutex_t g_hook_mutex = PTHREAD_MUTEX_INITIALIZER;

// 线程析构函数：自动 Detach 防止内存泄露
void detach_current_thread(void *value) {
    if (g_jvm) {
        g_jvm->DetachCurrentThread();
    }
}

// RAII 风格的 JNIEnv 获取与管理
struct ScopedJNIEnv {
    JNIEnv *env;

    ScopedJNIEnv() : env(nullptr) {
        if (!g_jvm) return;
        int res = g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                // 标记 TLS，线程退出时触发 detach_current_thread
                pthread_setspecific(g_thread_key, (void *) 1);
            } else {
                env = nullptr;
            }
        }
    }
};

// 内核级内存读取，防止 Crash
bool safe_read_memory(void *address, void *buffer, size_t size) {
    struct iovec local_iov = {buffer, size};
    struct iovec remote_iov = {address, size};
    // process_vm_readv 在 Android 6.0+ 可用
    ssize_t nread = process_vm_readv(getpid(), &local_iov, 1, &remote_iov, 1, 0);
    return nread == (ssize_t) size;
}

static int get_prot_for_addr(uintptr_t addr) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return -1;
    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        unsigned long long start = 0, end = 0;
        char perms[5] = {0};
        if (sscanf(line, "%llx-%llx %4s", &start, &end, perms) == 3) {
            if (addr >= (uintptr_t) start && addr < (uintptr_t) end) {
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

// =============================================================
//  Bridge Functions (C++ <-> Kotlin 桥接)
// =============================================================

#ifdef __aarch64__

static inline uint64_t read_gpr64(void *ctx, int idx) {
    return ((uint64_t *) ctx)[idx];
}

static inline void write_gpr64(void *ctx, int idx, uint64_t v) {
    ((uint64_t *) ctx)[idx] = v;
}

static inline uint64_t read_fpr64(void *ctx, int idx) {
    uint8_t *base = (uint8_t *) ctx + 0x060 + (idx * 16);
    uint64_t v;
    memcpy(&v, base, sizeof(v));
    return v;
}

static inline void write_fpr64(void *ctx, int idx, uint64_t v) {
    uint8_t *base = (uint8_t *) ctx + 0x060 + (idx * 16);
    memcpy(base, &v, sizeof(v));
}

static inline uint8_t *stack_base64(void *ctx) {
    return (uint8_t *) ctx + 0x260;
}

#else

static inline uint32_t read_gpr32(void *ctx, int idx) {
    return ((uint32_t *) ctx)[idx];
}

static inline void write_gpr32(void *ctx, int idx, uint32_t v) {
    ((uint32_t *) ctx)[idx] = v;
}

static inline uint64_t read_fpr32(void *ctx, int idx) {
    uint8_t *sp_after = (uint8_t *) ctx - 128;
    uint8_t *base = sp_after + (idx * 8);
    uint64_t v;
    memcpy(&v, base, sizeof(v));
    return v;
}

static inline void write_fpr32(void *ctx, int idx, uint64_t v) {
    uint8_t *sp_after = (uint8_t *) ctx - 128;
    uint8_t *base = sp_after + (idx * 8);
    memcpy(base, &v, sizeof(v));
}

static inline uint8_t *stack_base32(void *ctx) {
    uint8_t *sp_after = (uint8_t *) ctx - 128;
    return sp_after + 184;
}

#endif


// 进入 Hook：Kotlin 修改参数
extern "C" void bridge_enter(int index, void *ctx_stack_ptr) {
    ScopedJNIEnv state;
    JNIEnv *env = state.env;
    if (!env || !g_nativeLibObj) return;

#ifdef __aarch64__
    const int gpr_count = 8;
    const int fpr_count = 8;
    int stack_count = g_stack_counts[index];
    if (stack_count < 0) stack_count = 0;
    if (stack_count > MAX_STACK_ARGS) stack_count = MAX_STACK_ARGS;

    int total = gpr_count + fpr_count + stack_count;
    jlongArray args = env->NewLongArray(total);
    if (!args) return;
    jlong temp[gpr_count + fpr_count + MAX_STACK_ARGS];

    for (int i = 0; i < gpr_count; i++) temp[i] = (jlong) read_gpr64(ctx_stack_ptr, i);
    for (int i = 0; i < fpr_count; i++) temp[gpr_count + i] = (jlong) read_fpr64(ctx_stack_ptr, i);
    uint8_t *sb = stack_base64(ctx_stack_ptr);
    for (int i = 0; i < stack_count; i++) {
        uint64_t v;
        memcpy(&v, sb + i * 8, 8);
        temp[gpr_count + fpr_count + i] = (jlong) v;
    }
#else
    const int gpr_count = 4;
    const int fpr_count = 8;
    int stack_count = g_stack_counts[index];
    if (stack_count < 0) stack_count = 0;
    if (stack_count > MAX_STACK_ARGS) stack_count = MAX_STACK_ARGS;

    int total = gpr_count + fpr_count + stack_count;
    jlongArray args = env->NewLongArray(total);
    if (!args) return;
    jlong temp[gpr_count + fpr_count + MAX_STACK_ARGS];

    for (int i = 0; i < gpr_count; i++) temp[i] = (jlong) read_gpr32(ctx_stack_ptr, i);
    for (int i = 0; i < fpr_count; i++) temp[gpr_count + i] = (jlong) read_fpr32(ctx_stack_ptr, i);
    uint8_t *sb = stack_base32(ctx_stack_ptr);
    for (int i = 0; i < stack_count; i++) {
        uint32_t v;
        memcpy(&v, sb + i * 4, 4);
        temp[gpr_count + fpr_count + i] = (jlong) v;
    }
#endif

    env->SetLongArrayRegion(args, 0, total, temp);

    jclass cls = env->GetObjectClass(g_nativeLibObj);
    if (!cls) {
        env->DeleteLocalRef(args);
        return;
    }
    jmethodID mid = env->GetMethodID(cls, "onNativeEnter", "(I[J)[J");
    if (!mid) {
        env->DeleteLocalRef(args);
        env->DeleteLocalRef(cls);
        return;
    }

    jobject ret = env->CallObjectMethod(g_nativeLibObj, mid, index, args);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(args);
        env->DeleteLocalRef(cls);
        return;
    }

    if (ret != nullptr) {
        auto newArgs = (jlongArray) ret;
        jsize n = env->GetArrayLength(newArgs);
        jsize c = n < total ? n : total;
        jlong *el = env->GetLongArrayElements(newArgs, nullptr);
        if (el) {
#ifdef __aarch64__
            for (int i = 0; i < gpr_count && i < c; i++)
                write_gpr64(ctx_stack_ptr, i, (uint64_t) el[i]);
            for (int i = 0; i < fpr_count && (gpr_count + i) < c; i++)
                write_fpr64(ctx_stack_ptr, i, (uint64_t) el[gpr_count + i]);
            for (int i = 0; i < stack_count && (gpr_count + fpr_count + i) < c; i++) {
                uint64_t v = (uint64_t) el[gpr_count + fpr_count + i];
                memcpy(sb + i * 8, &v, 8);
            }
#else
            for (int i = 0; i < gpr_count && i < c; i++)
                write_gpr32(ctx_stack_ptr, i, (uint32_t) el[i]);
            for (int i = 0; i < fpr_count && (gpr_count + i) < c; i++)
                write_fpr32(ctx_stack_ptr, i, (uint64_t) el[gpr_count + i]);
            for (int i = 0; i < stack_count && (gpr_count + fpr_count + i) < c; i++) {
                uint32_t v = (uint32_t) el[gpr_count + fpr_count + i];
                memcpy(sb + i * 4, &v, 4);
            }
#endif
            env->ReleaseLongArrayElements(newArgs, el, JNI_ABORT);
        }
        env->DeleteLocalRef(newArgs);
    }

    env->DeleteLocalRef(args);
    env->DeleteLocalRef(cls);
}

// 离开 Hook：Kotlin 修改返回值
extern "C" void bridge_leave(int index, void *retval_ptr) {
    ScopedJNIEnv state;
    JNIEnv *env = state.env;
    if (!env || !g_nativeLibObj) return;

    jclass cls = env->GetObjectClass(g_nativeLibObj);
    if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onNativeLeave", "(IJ)J");
    if (!mid) {
        env->DeleteLocalRef(cls);
        return;
    }

    int rt = g_ret_types[index];
    jlong call_ret = 0;
#ifdef __aarch64__
    if (rt == RET_FLOAT || rt == RET_DOUBLE) {
        uint64_t v = 0;
        uint8_t *base = (uint8_t *) retval_ptr + 0x060;
        memcpy(&v, base, sizeof(v));
        call_ret = (jlong) v;
    } else {
        long *r = (long *) retval_ptr;
        call_ret = (jlong) *r;
    }
#else
    if (rt == RET_FLOAT || rt == RET_DOUBLE) {
        uint64_t v = 0;
        uint8_t *sp_after = (uint8_t *) retval_ptr - 128;
        memcpy(&v, sp_after, sizeof(v));
        call_ret = (jlong) v;
    } else {
        long *r = (long *) retval_ptr;
        call_ret = (jlong) *r;
    }
#endif

    jlong new_ret = env->CallLongMethod(g_nativeLibObj, mid, index, call_ret);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(cls);
        return;
    }

#ifdef __aarch64__
    if (rt == RET_INT) {
        long *r = (long *) retval_ptr;
        *r = (long) new_ret;
    }
    if (rt == RET_FLOAT || rt == RET_DOUBLE) {
        uint64_t v = (uint64_t) new_ret;
        uint8_t *base = (uint8_t *) retval_ptr + 0x060;
        memcpy(base, &v, sizeof(v));
    }
#else
    if (rt == RET_INT) {
        long *r = (long *) retval_ptr;
        *r = (long) new_ret;
    }
    if (rt == RET_FLOAT || rt == RET_DOUBLE) {
        uint64_t v = (uint64_t) new_ret;
        uint8_t *sp_after = (uint8_t *) retval_ptr - 128;
        memcpy(sp_after + 0, &v, sizeof(v));
    }
#endif

    env->DeleteLocalRef(cls);
}

// =============================================================
//  Assembly Stubs (汇编桩)
// =============================================================

#ifdef __aarch64__
// ---------------- ARM64 Stubs ----------------
/*
 * ARM64 栈布局策略 (Total Size: 0x260 = 608 bytes)
 * 必须严格遵守 stp 偏移限制 (GPRs < 504, SIMD < 1008)
 * 关键逻辑：在整个调用过程中保持栈帧，直到最后才恢复 LR 并返回，防止 blr 覆盖 LR 导致死循环。
 */
#define DEFINE_STUB(N) \
    extern "C" __attribute__((naked)) void stub_##N() { \
        __asm__ volatile( \
            /* 1. 开辟栈空间 */ \
            "sub sp, sp, #0x260\n" \
            \
            /* 2. 保存通用寄存器 (放在低地址 0x000 - 0x050) */ \
            "stp x0, x1, [sp, #0x000]\n" \
            "stp x2, x3, [sp, #0x010]\n" \
            "stp x4, x5, [sp, #0x020]\n" \
            "stp x6, x7, [sp, #0x030]\n" \
            "str x8,     [sp, #0x040]\n" \
            "stp x29, x30, [sp, #0x050]\n" /* 保存调用者的 FP, LR */ \
            \
            /* 3. 保存浮点寄存器 (放在高地址 0x060 - 0x240) */ \
            "stp q0, q1, [sp, #0x060]\n" \
            "stp q2, q3, [sp, #0x080]\n" \
            "stp q4, q5, [sp, #0x0A0]\n" \
            "stp q6, q7, [sp, #0x0C0]\n" \
            "stp q8, q9, [sp, #0x0E0]\n" \
            "stp q10, q11, [sp, #0x100]\n" \
            "stp q12, q13, [sp, #0x120]\n" \
            "stp q14, q15, [sp, #0x140]\n" \
            "stp q16, q17, [sp, #0x160]\n" \
            "stp q18, q19, [sp, #0x180]\n" \
            "stp q20, q21, [sp, #0x1A0]\n" \
            "stp q22, q23, [sp, #0x1C0]\n" \
            "stp q24, q25, [sp, #0x1E0]\n" \
            "stp q26, q27, [sp, #0x200]\n" \
            "stp q28, q29, [sp, #0x220]\n" \
            "stp q30, q31, [sp, #0x240]\n" \
            \
            /* 4. 调用 bridge_enter (Arg1=index, Arg2=regs_ptr) */ \
            "mov x0, %0\n" \
            "mov x1, sp\n" \
            "bl bridge_enter\n" \
            \
            /* 5. 恢复参数 (x0-x7, q0-q7) 供原函数使用 */ \
            "ldp x0, x1, [sp, #0x000]\n" \
            "ldp x2, x3, [sp, #0x010]\n" \
            "ldp x4, x5, [sp, #0x020]\n" \
            "ldp x6, x7, [sp, #0x030]\n" \
            "ldr x8,     [sp, #0x040]\n" \
            "ldp q0, q1, [sp, #0x060]\n" \
            "ldp q2, q3, [sp, #0x080]\n" \
            "ldp q4, q5, [sp, #0x0A0]\n" \
            "ldp q6, q7, [sp, #0x0C0]\n" \
            \
            /* 6. 调用原函数 (会覆盖当前的 LR) */ \
            "adrp x9, :got:g_orig_funcs\n" \
            "ldr x9, [x9, :got_lo12:g_orig_funcs]\n" \
            "mov x10, %0\n" \
            "ldr x10, [x9, x10, lsl #3]\n" \
            "blr x10\n" \
            \
            /* 7. 保存返回值 (x0, q0) 到栈上供 bridge_leave 修改 */ \
            "str x0, [sp, #0x000]\n" \
            "str q0, [sp, #0x060]\n" \
            \
            /* 8. 调用 bridge_leave */ \
            "mov x0, %0\n" \
            "mov x1, sp\n" \
            "bl bridge_leave\n" \
            \
            /* 9. ???????????? */ \
            "adrp x11, :got:g_ret_types\n" \
            "ldr x11, [x11, :got_lo12:g_ret_types]\n" \
            "mov x12, %0\n" \
            "ldr w12, [x11, x12, lsl #2]\n" \
            "cmp w12, #0\n" \
            "b.eq 1f\n" \
            "cmp w12, #1\n" \
            "b.eq 2f\n" \
            "cmp w12, #3\n" \
            "b.eq 2f\n" \
            "b 3f\n" \
            "1:\n" \
            "ldr x0, [sp, #0x000]\n" \
            "b 3f\n" \
            "2:\n" \
            "ldr q0, [sp, #0x060]\n" \
            "3:\n" \
            \
/* 10. 恢复所有 Callee-saved 寄存器并返回 */ \
            /* 必须在此处恢复 LR，因为之前的 blr 破坏了 LR */ \
            "ldp x29, x30, [sp, #0x050]\n" \
            \
            /* 恢复其他可能被破坏的寄存器 (Full Context Restore) */ \
            "ldp q8, q9, [sp, #0x0E0]\n" \
            "ldp q10, q11, [sp, #0x100]\n" \
            "ldp q12, q13, [sp, #0x120]\n" \
            "ldp q14, q15, [sp, #0x140]\n" \
            "ldp q16, q17, [sp, #0x160]\n" \
            "ldp q18, q19, [sp, #0x180]\n" \
            "ldp q20, q21, [sp, #0x1A0]\n" \
            "ldp q22, q23, [sp, #0x1C0]\n" \
            "ldp q24, q25, [sp, #0x1E0]\n" \
            "ldp q26, q27, [sp, #0x200]\n" \
            "ldp q28, q29, [sp, #0x220]\n" \
            "ldp q30, q31, [sp, #0x240]\n" \
            \
            "add sp, sp, #0x260\n" \
            "ret\n" \
            : : "i"(N) \
        ); \
    }

#else
// ---------------- ARM32 Stubs ----------------
/*
 * ARM32 栈布局 (Total Size: 56 + 128 = 184 bytes, 8-byte aligned)
 * PUSH {r0-r12, lr} -> 14 * 4 = 56 bytes
 * VPUSH {d0-d15}    -> 16 * 8 = 128 bytes
 * 栈顶 (SP) -> d0 ... d15 ... r0 ... lr
 * 偏移:
 * SP + 0   : d0
 * SP + 128 : r0
 */
#define DEFINE_STUB(N) \
    extern "C" __attribute__((naked)) void stub_##N() { \
        __asm__ volatile( \
            /* 1. 保存上下文 */ \
            "push {r0-r12, lr}\n"       /* 保存通用寄存器 */ \
            "vpush {d0-d15}\n"          /* 保存浮点寄存器 (NEON) */ \
            \
            /* 2. 调用 bridge_enter */ \
            "mov r0, %0\n"              /* Arg1: index */ \
            "add r1, sp, #128\n"      /* Arg2: ctx (r0 save area) */ \
            "bl bridge_enter\n"         /* 调用 C++ */ \
            \
            /* 3. 恢复参数 (r0-r3, d0-d7) */ \
            "vldr d0, [sp, #0]\n" \
            "vldr d1, [sp, #8]\n" \
            "vldr d2, [sp, #16]\n" \
            "vldr d3, [sp, #24]\n" \
            "vldr d4, [sp, #32]\n" \
            "vldr d5, [sp, #40]\n" \
            "vldr d6, [sp, #48]\n" \
            "vldr d7, [sp, #56]\n" \
            "ldr r0, [sp, #128]\n" \
            "ldr r1, [sp, #132]\n" \
            "ldr r2, [sp, #136]\n" \
            "ldr r3, [sp, #140]\n" \
            \
            /* 4. 调用原函数 */ \
            "ldr r12, =g_orig_funcs\n"  /* 加载数组地址 */ \
            "mov r9, %0\n"              /* 加载索引 */ \
            "ldr r12, [r12, r9, lsl #2]\n" /* 读取原函数指针 */ \
            "blx r12\n"                 /* 调用原函数 */ \
            \
            /* 5. 保存返回值 (r0, r1, d0) 回栈，供 bridge_leave 修改 */ \
            "str r0, [sp, #128]\n" \
            "str r1, [sp, #132]\n" \
            "vstr d0, [sp, #0]\n" \
            \
            /* 6. 调用 bridge_leave */ \
            "mov r0, %0\n" \
            "add r1, sp, #128\n" \
            "bl bridge_leave\n" \
            \
            /* 7. 恢复所有寄存器并返回 */ \
            "ldr r4, =g_ret_types\n" \
            "mov r5, %0\n" \
            "ldr r4, [r4, r5, lsl #2]\n" \
            "cmp r4, #1\n" \
            "beq 1f\n" \
            "cmp r4, #3\n" \
            "beq 1f\n" \
            "1:\n" \
            "vldr d0, [sp, #0]\n" \
            "2:\n" \
"vpop {d0-d15}\n" \
            "pop {r0-r12, lr}\n"        /* 恢复通用寄存器 */ \
            "bx lr\n"                   /* 返回 */ \
            : : "i"(N) \
        ); \
    }
#endif

// ---------------- 批量生成桩 (50个) ----------------
DEFINE_STUB(0)  DEFINE_STUB(1)  DEFINE_STUB(2)  DEFINE_STUB(3)  DEFINE_STUB(4)
DEFINE_STUB(5)  DEFINE_STUB(6)  DEFINE_STUB(7)  DEFINE_STUB(8)  DEFINE_STUB(9)
DEFINE_STUB(10) DEFINE_STUB(11) DEFINE_STUB(12) DEFINE_STUB(13) DEFINE_STUB(14)
DEFINE_STUB(15) DEFINE_STUB(16) DEFINE_STUB(17) DEFINE_STUB(18) DEFINE_STUB(19)
DEFINE_STUB(20) DEFINE_STUB(21) DEFINE_STUB(22) DEFINE_STUB(23) DEFINE_STUB(24)
DEFINE_STUB(25) DEFINE_STUB(26) DEFINE_STUB(27) DEFINE_STUB(28) DEFINE_STUB(29)
DEFINE_STUB(30) DEFINE_STUB(31) DEFINE_STUB(32) DEFINE_STUB(33) DEFINE_STUB(34)
DEFINE_STUB(35) DEFINE_STUB(36) DEFINE_STUB(37) DEFINE_STUB(38) DEFINE_STUB(39)
DEFINE_STUB(40) DEFINE_STUB(41) DEFINE_STUB(42) DEFINE_STUB(43) DEFINE_STUB(44)
DEFINE_STUB(45) DEFINE_STUB(46) DEFINE_STUB(47) DEFINE_STUB(48) DEFINE_STUB(49)

// 注册桩数组
void *g_stubs[] = {
        (void *) stub_0, (void *) stub_1, (void *) stub_2, (void *) stub_3, (void *) stub_4,
        (void *) stub_5, (void *) stub_6, (void *) stub_7, (void *) stub_8, (void *) stub_9,
        (void *) stub_10, (void *) stub_11, (void *) stub_12, (void *) stub_13, (void *) stub_14,
        (void *) stub_15, (void *) stub_16, (void *) stub_17, (void *) stub_18, (void *) stub_19,
        (void *) stub_20, (void *) stub_21, (void *) stub_22, (void *) stub_23, (void *) stub_24,
        (void *) stub_25, (void *) stub_26, (void *) stub_27, (void *) stub_28, (void *) stub_29,
        (void *) stub_30, (void *) stub_31, (void *) stub_32, (void *) stub_33, (void *) stub_34,
        (void *) stub_35, (void *) stub_36, (void *) stub_37, (void *) stub_38, (void *) stub_39,
        (void *) stub_40, (void *) stub_41, (void *) stub_42, (void *) stub_43, (void *) stub_44,
        (void *) stub_45, (void *) stub_46, (void *) stub_47, (void *) stub_48, (void *) stub_49
};

// =============================================================
//  JNI 导出函数
// =============================================================

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    // 注册 TLS，确保线程安全
    if (pthread_key_create(&g_thread_key, detach_current_thread) != 0) {
        LOGE("Failed to create pthread key");
    }
    for (int i = 0; i < MAX_HOOKS; i++) {
        g_ret_types[i] = RET_INT;
        g_stack_counts[i] = 0;
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (!g_jvm) return;
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK || !env) return;
    if (g_nativeLibObj) {
        env->DeleteGlobalRef(g_nativeLibObj);
        g_nativeLibObj = nullptr;
    }
    pthread_key_delete(g_thread_key);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_registerGenericHook(JNIEnv *env, jobject thiz,
                                                                jlong addr, jint retType,
                                                                jint argc) {
    if (!g_nativeLibObj) g_nativeLibObj = env->NewGlobalRef(thiz);
    pthread_mutex_lock(&g_hook_mutex);
    if (g_current_hook_idx >= MAX_HOOKS) {
        LOGE("Max hooks limit reached");
        pthread_mutex_unlock(&g_hook_mutex);
        return -1;
    }

    int idx = g_current_hook_idx;
    int rt = retType;
    if (rt < RET_INT || rt > RET_VOID) rt = RET_INT;
    g_ret_types[idx] = rt;
    int sc = argc;
    if (sc < 0) sc = 0;
    if (sc > MAX_STACK_ARGS) sc = MAX_STACK_ARGS;
    g_stack_counts[idx] = sc;
    void *target = (void *) addr;
    void *orig = nullptr;

    int ret = DobbyHook(target, g_stubs[idx], (void **) &orig);
    if (ret == 0) {
        g_orig_funcs[idx] = orig;
        g_current_hook_idx++;
        pthread_mutex_unlock(&g_hook_mutex);
        return idx;
    }
    pthread_mutex_unlock(&g_hook_mutex);
    LOGE("DobbyHook failed with code %d", ret);
    return -1;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_moduleBase(JNIEnv *env, jobject thiz, jstring name) {
    if (!name) return 0;
    const char *pkg = env->GetStringUTFChars(name, nullptr);
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) {
        env->ReleaseStringUTFChars(name, pkg);
        return 0;
    }

    char line[512];
    jlong base = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, pkg)) {
            char *end;
            // 必须使用 strtoull 确保 64 位地址解析正确，防止 Narrowing conversion
            base = (jlong) strtoull(line, &end, 16);
            break;
        }
    }
    fclose(fp);
    env->ReleaseStringUTFChars(name, pkg);
    return base;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_safeRead(JNIEnv *env, jobject thiz, jlong ptr,
                                                     jint size) {
    if (size <= 0) return nullptr;
    jbyteArray ba = env->NewByteArray(size);
    if (!ba) return nullptr;
    jbyte *buf = env->GetByteArrayElements(ba, nullptr);

    if (safe_read_memory((void *) ptr, buf, size)) {
        env->ReleaseByteArrayElements(ba, buf, 0);
        return ba;
    } else {
        env->ReleaseByteArrayElements(ba, buf, JNI_ABORT);
        return nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_safeWrite(JNIEnv *env, jobject thiz, jlong ptr,
                                                      jbyteArray data) {
    if (!data) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_TRUE;
    if (ptr == 0) return JNI_FALSE;
    void *addr = (void *) ptr;

    long pagesize = sysconf(_SC_PAGESIZE);
    if (pagesize <= 0) return JNI_FALSE;
    uintptr_t start = (uintptr_t) addr;
    uintptr_t page_start = start & ~(uintptr_t) (pagesize - 1);
    uintptr_t page_end =
            (start + (uintptr_t) len + (uintptr_t) pagesize - 1) & ~(uintptr_t) (pagesize - 1);
    size_t protect_len = (size_t) (page_end - page_start);

    int old_prot = get_prot_for_addr(start);
    int new_prot = (old_prot >= 0) ? (old_prot | PROT_WRITE) : (PROT_READ | PROT_WRITE | PROT_EXEC);

    // update memory permissions for the full range
    if (mprotect((void *) page_start, protect_len, new_prot) != 0) {
        return JNI_FALSE;
    }

    jbyte *buf = env->GetByteArrayElements(data, nullptr);
    if (!buf) {
        if (old_prot >= 0) {
            mprotect((void *) page_start, protect_len, old_prot);
        }
        return JNI_FALSE;
    }
    memcpy(addr, buf, len);

    // 刷新指令缓存
    __builtin___clear_cache((char *) addr, (char *) addr + len);

    env->ReleaseByteArrayElements(data, buf, 0);
    if (old_prot >= 0) {
        mprotect((void *) page_start, protect_len, old_prot);
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_mallocString(JNIEnv *env, jobject thiz, jstring str) {
    if (!str) return 0;
    const char *c = env->GetStringUTFChars(str, nullptr);
    char *ptr = strdup(c);
    env->ReleaseStringUTFChars(str, c);
    return (jlong) ptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_malloc(JNIEnv *env, jobject thiz, jint size) {
    if (size <= 0) return 0;
    void *ptr = malloc((size_t) size);
    return (jlong) ptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_free(JNIEnv *env, jobject thiz, jlong ptr) {
    if (ptr) free((void *) ptr);
}
