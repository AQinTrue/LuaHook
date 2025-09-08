#include <cstring>
#include <jni.h>
#include "xdl.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_library_NativeLib_get_1module_1base(JNIEnv *env, jobject thiz, jstring name) {
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

    std::memcpy((void *) ptr, buffer, size);
    env->ReleaseByteArrayElements(data, buffer, 0);  // 0: copy back and free
    return JNI_TRUE;
}