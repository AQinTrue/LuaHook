#include <jni.h>
#include "xdl.h"

extern "C"
JNIEXPORT jlong JNICALL
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