#include <cstring>
#include <jni.h>
#include <sys/mman.h>
#include "xdl.h"

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_kulipai_luahook_library_NativeLib_read(JNIEnv *env, jobject thiz, jlong ptr, jint size) {
    if (ptr == 0 || size <= 0) return nullptr;

    jbyteArray byteArray = env->NewByteArray(size);
    if (!byteArray) return nullptr;

    env->SetByteArrayRegion(byteArray, 0, size, reinterpret_cast<jbyte *>(ptr));
    return byteArray;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_library_NativeLib_write(JNIEnv *env, jobject thiz, jlong ptr, jbyteArray data) {
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

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_library_NativeLib_moduleBase(JNIEnv *env, jobject thiz, jstring name) {
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

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_library_NativeLib_resolveSymbol(JNIEnv *env, jobject thiz, jstring module, jstring name) {
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