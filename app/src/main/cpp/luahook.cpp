#include <cstring>
#include <jni.h>
#include <sys/mman.h>
#include "xdl.h"
#include "SysRead.h"

extern "C" JNIEXPORT jbyteArray

JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_read(JNIEnv *env, jobject thiz, jlong ptr, jint size) {
    if (ptr == 0 || size <= 0) return nullptr;

    jbyteArray byteArray = env->NewByteArray(size);
    if (!byteArray) return nullptr;

    env->SetByteArrayRegion(byteArray, 0, size, reinterpret_cast<jbyte *>(ptr));
    return byteArray;
}

extern "C" JNIEXPORT jboolean

JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_write(JNIEnv *env, jobject thiz, jlong ptr,
                                                 jbyteArray data) {
    if (ptr == 0 || data == nullptr) return JNI_FALSE;

    jsize size = env->GetArrayLength(data);
    if (size <= 0) return JNI_FALSE;

    jbyte *buffer = env->GetByteArrayElements(data, nullptr);
    if (!buffer) return JNI_FALSE;

    int result = mprotect((void *) ptr, size, PROT_READ | PROT_WRITE | PROT_EXEC);
    if (result != 0) return JNI_FALSE;

    std::memcpy((void *) ptr, buffer, size);
    env->ReleaseByteArrayElements(data, buffer, 0);  // 0: copy back and free
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong

JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_moduleBase(JNIEnv *env, jobject thiz, jstring name) {
    const char *chars = env->GetStringUTFChars(name, nullptr);
    int64_t result = 0;
    void *handle = xdl_open(chars, XDL_DEFAULT);
    if (handle != nullptr) {
        xdl_info_t info;
        xdl_info(handle, XDL_DI_DLINFO, &info);
        result = (int64_t) info.dli_fbase;
    }
    env->ReleaseStringUTFChars(name, chars);
    return result;
}

extern "C" JNIEXPORT jlong

JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_resolveSymbol(JNIEnv *env, jobject thiz, jstring module,
                                                         jstring name) {
    const char *module_chars = env->GetStringUTFChars(module, nullptr);
    const char *name_chars = env->GetStringUTFChars(name, nullptr);
    void *handle = xdl_open(module_chars, XDL_DEFAULT);
    int64_t result = 0;
    if (handle != nullptr) {
        // 首先尝试 xdl_sym; 如果xdl_sym失败, 再尝试xdl_dsym
        void *symbol = xdl_sym(handle, name_chars, nullptr);
        if (symbol == nullptr)
            symbol = xdl_dsym(handle, name_chars, nullptr);
        // 只有成功时才给 result 赋值
        if (symbol != nullptr)
            result = (int64_t) symbol;
    }
    env->ReleaseStringUTFChars(name, module_chars);
    env->ReleaseStringUTFChars(name, name_chars);
    return result;
}

extern "C"
JNIEXPORT jlong

JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_getModuleBase(JNIEnv *env, jobject thiz,
                                                         jstring module_name,
                                                         jstring module_field) {
    const char *native_module_name = env->GetStringUTFChars(module_name, nullptr);
    const char *native_module_field = env->GetStringUTFChars(module_field, nullptr);
    if (native_module_name == nullptr || native_module_field == nullptr) {
        return 0;
    }

    uintptr_t base_address = GetModuleBase(native_module_name, native_module_field);
    env->ReleaseStringUTFChars(module_name, native_module_name);
    env->ReleaseStringUTFChars(module_field, native_module_field);
    return static_cast<jlong>(base_address);;

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_writeDword(JNIEnv *env, jobject thiz, jlong ptr,
                                                      jint value) {
    if (ptr == 0) return JNI_FALSE;
    try {
        WriteDword(static_cast<long>(ptr), static_cast<int>(value));
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }


}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readDword(JNIEnv *env, jobject thiz, jlong ptr) {
    if (ptr == 0) return JNI_FALSE;
    try {
        return (jint) ReadDword((long) ptr);
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readPoint(JNIEnv *env, jobject thiz,
                                                     jlong ptr, jlongArray offsetsArray) {
    if (ptr == 0) {
        return 0;
    }

    try {
        // 如果没有偏移数组，只读取一级指针
        if (!offsetsArray) {
            return (jlong) ReadZZ((long) ptr);
        }

        jsize length = env->GetArrayLength(offsetsArray);
        if (length == 0) {
            return (jlong) ReadZZ((long) ptr);
        }

        // 获取偏移数组
        jlong *offsetsPtr = env->GetLongArrayElements(offsetsArray, nullptr);
        if (!offsetsPtr) {
            return (jlong) ReadZZ((long) ptr);
        }

        // 将 jlong 转换为 int（因为您的 ReadPoint 使用 int 参数）
        std::vector<int> offsets;
        offsets.reserve(length);
        for (jsize i = 0; i < length; i++) {
            offsets.push_back((int) offsetsPtr[i]);
        }

        // 直接实现 ReadPoint 的逻辑
        long addr = (long) ptr;

        // 第一步：读取第一级指针
        addr = ReadZZ(addr);

        // 处理所有偏移
        for (jsize i = 0; i < length; i++) {
            if (i == length - 1) {
                // 最后一个偏移，直接加上
                addr += offsets[i];
                break;
            }
            // 读取下一级指针并加上偏移
            addr = ReadZZ(addr + offsets[i]);
            if (addr == 0) {
                // 读取失败
                break;
            }
        }

        env->ReleaseLongArrayElements(offsetsArray, offsetsPtr, JNI_ABORT);
        return (jlong) addr;
    }
    catch (...) {
        return 0;
    }
}
extern "C"
JNIEXPORT jfloat JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readFloat(JNIEnv *env, jobject thiz, jlong ptr) {
    if (ptr == 0) return JNI_FALSE;
    try {
        return (jfloat) ReadFloat((long) ptr);
    } catch (...) {
        return JNI_FALSE;
    }
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_writeFloat(JNIEnv *env, jobject thiz, jlong ptr,
                                                      jfloat value) {
    if (ptr == 0) return JNI_FALSE;
    try {
        WriteFloat(static_cast<long>(ptr), static_cast<float>(value));
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
}
extern "C"
JNIEXPORT jbyte JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readByte(JNIEnv *env, jobject thiz, jlong ptr) {
    if (ptr == 0) return 0;
    try {
        return static_cast<jbyte>(ReadByte(static_cast<long>(ptr)));
    } catch (...) {
        return 0;
    }
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_writeByte(JNIEnv *env, jobject thiz, jlong ptr,
                                                     jbyte value) {
    if (ptr == 0) return JNI_FALSE;
    try {
        WriteByte(static_cast<long>(ptr), static_cast<uint8_t>(value));
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
}