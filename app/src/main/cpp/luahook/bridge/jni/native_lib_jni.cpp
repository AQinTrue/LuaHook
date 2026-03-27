#include <jni.h>

#include <pthread.h>

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "luahook/core/api/engine.h"
#include "luahook/core/api/hook.h"
#include "luahook/core/api/invoke.h"
#include "luahook/core/api/memory.h"
#include "luahook/core/api/symbol.h"

namespace {

JavaVM *g_jvm = nullptr;
LhEngine *g_engine = nullptr;
jobject g_native_lib_obj = nullptr;
jmethodID g_on_enter_method = nullptr;
jmethodID g_on_leave_method = nullptr;
pthread_key_t g_thread_key{};
bool g_thread_key_created = false;
std::mutex g_callback_mutex;
std::mutex g_managed_refs_mutex;
std::unordered_map<jlong, jobject> g_managed_global_refs;

void detach_current_thread(void *value) {
  (void)value;
  if (g_jvm) {
    g_jvm->DetachCurrentThread();
  }
}

struct ScopedJNIEnv {
  JNIEnv *env;

  ScopedJNIEnv() : env(nullptr) {
    if (!g_jvm) {
      return;
    }
    if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
      return;
    }
    if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK && g_thread_key_created) {
      pthread_setspecific(g_thread_key, reinterpret_cast<void *>(1));
    } else {
      env = nullptr;
    }
  }
};

jlong callback_id_from_userdata(void *userdata) {
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(userdata));
}

bool ensure_callback_target(JNIEnv *env, jobject thiz) {
  if (!env || !thiz) {
    return false;
  }

  std::lock_guard<std::mutex> lock(g_callback_mutex);
  if (g_native_lib_obj && env->IsSameObject(g_native_lib_obj, thiz) == JNI_TRUE &&
      g_on_enter_method && g_on_leave_method) {
    return true;
  }

  if (g_native_lib_obj) {
    env->DeleteGlobalRef(g_native_lib_obj);
    g_native_lib_obj = nullptr;
  }
  g_on_enter_method = nullptr;
  g_on_leave_method = nullptr;

  g_native_lib_obj = env->NewGlobalRef(thiz);
  if (!g_native_lib_obj) {
    return false;
  }

  jclass cls = env->GetObjectClass(thiz);
  if (!cls) {
    env->DeleteGlobalRef(g_native_lib_obj);
    g_native_lib_obj = nullptr;
    return false;
  }

  g_on_enter_method = env->GetMethodID(cls, "onNativeEnter", "(J[J)[J");
  g_on_leave_method = env->GetMethodID(cls, "onNativeLeave", "(JJ)J");
  env->DeleteLocalRef(cls);
  if (!g_on_enter_method || !g_on_leave_method) {
    env->DeleteGlobalRef(g_native_lib_obj);
    g_native_lib_obj = nullptr;
    g_on_enter_method = nullptr;
    g_on_leave_method = nullptr;
    return false;
  }
  return true;
}

bool snapshot_callback_target(JNIEnv *env,
                              jobject *out_target,
                              jmethodID *out_enter,
                              jmethodID *out_leave) {
  if (!env || !out_target || !out_enter || !out_leave) {
    return false;
  }

  std::lock_guard<std::mutex> lock(g_callback_mutex);
  if (!g_native_lib_obj || !g_on_enter_method || !g_on_leave_method) {
    return false;
  }

  jobject target = env->NewLocalRef(g_native_lib_obj);
  if (!target) {
    return false;
  }
  *out_target = target;
  *out_enter = g_on_enter_method;
  *out_leave = g_on_leave_method;
  return true;
}

bool current_env_matches(JNIEnv *env, jlong env_ptr) {
  return env && env_ptr != 0 &&
         reinterpret_cast<jlong>(reinterpret_cast<intptr_t>(env)) == env_ptr;
}

jlong ref_handle_from_jobject(jobject ref) {
  return static_cast<jlong>(reinterpret_cast<intptr_t>(ref));
}

jlong register_managed_global_ref(JNIEnv *env, jobject ref) {
  if (!env || !ref) {
    return 0;
  }

  jobject global = env->NewGlobalRef(ref);
  if (!global) {
    return 0;
  }

  const jlong handle = ref_handle_from_jobject(global);
  {
    std::lock_guard<std::mutex> lock(g_managed_refs_mutex);
    auto insert_result = g_managed_global_refs.emplace(handle, global);
    if (!insert_result.second) {
      env->DeleteGlobalRef(global);
      return 0;
    }
  }
  return handle;
}

jobject snapshot_managed_global_ref(JNIEnv *env, jlong handle) {
  if (!env || handle == 0) {
    return nullptr;
  }

  std::lock_guard<std::mutex> lock(g_managed_refs_mutex);
  auto it = g_managed_global_refs.find(handle);
  if (it == g_managed_global_refs.end()) {
    return nullptr;
  }
  return env->NewLocalRef(it->second);
}

bool release_managed_global_ref(JNIEnv *env, jlong handle) {
  if (!env || handle == 0) {
    return false;
  }

  jobject global = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_managed_refs_mutex);
    auto it = g_managed_global_refs.find(handle);
    if (it == g_managed_global_refs.end()) {
      return false;
    }
    global = it->second;
    g_managed_global_refs.erase(it);
  }
  env->DeleteGlobalRef(global);
  return true;
}

void release_all_managed_global_refs(JNIEnv *env) {
  if (!env) {
    return;
  }

  std::vector<jobject> refs;
  {
    std::lock_guard<std::mutex> lock(g_managed_refs_mutex);
    refs.reserve(g_managed_global_refs.size());
    for (const auto &entry : g_managed_global_refs) {
      refs.push_back(entry.second);
    }
    g_managed_global_refs.clear();
  }

  for (jobject ref : refs) {
    env->DeleteGlobalRef(ref);
  }
}

void bridge_on_enter(void *userdata, LhCallFrame *frame) {
  if (!frame) {
    return;
  }

  ScopedJNIEnv scoped_env;
  JNIEnv *env = scoped_env.env;
  if (!env) {
    return;
  }

  jobject callback_target = nullptr;
  jmethodID on_enter = nullptr;
  jmethodID on_leave = nullptr;
  if (!snapshot_callback_target(env, &callback_target, &on_enter, &on_leave)) {
    return;
  }

  const jsize arg_count = static_cast<jsize>(frame->arg_count);
  jlongArray arg_bits = env->NewLongArray(arg_count);
  if (!arg_bits) {
    env->DeleteLocalRef(callback_target);
    return;
  }

  if (arg_count > 0) {
    std::vector<jlong> values(frame->arg_count);
    for (size_t i = 0; i < frame->arg_count; ++i) {
      values[i] = static_cast<jlong>(frame->args[i].bits);
    }
    env->SetLongArrayRegion(arg_bits, 0, arg_count, values.data());
  }

  jobject result = env->CallObjectMethod(callback_target, on_enter,
                                         callback_id_from_userdata(userdata),
                                         arg_bits);
  env->DeleteLocalRef(arg_bits);
  env->DeleteLocalRef(callback_target);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    if (result) {
      env->DeleteLocalRef(result);
    }
    return;
  }
  if (!result) {
    return;
  }

  jlongArray replacement = reinterpret_cast<jlongArray>(result);
  const jsize replacement_count = env->GetArrayLength(replacement);
  const jsize copy_count = replacement_count < arg_count ? replacement_count : arg_count;
  if (copy_count > 0) {
    std::vector<jlong> values(static_cast<size_t>(copy_count));
    env->GetLongArrayRegion(replacement, 0, copy_count, values.data());
    for (jsize i = 0; i < copy_count; ++i) {
      frame->args[static_cast<size_t>(i)].bits = static_cast<LhBits>(values[static_cast<size_t>(i)]);
    }
  }
  env->DeleteLocalRef(replacement);
}

void bridge_on_leave(void *userdata, LhCallFrame *frame) {
  if (!frame) {
    return;
  }

  ScopedJNIEnv scoped_env;
  JNIEnv *env = scoped_env.env;
  if (!env) {
    return;
  }

  jobject callback_target = nullptr;
  jmethodID on_enter = nullptr;
  jmethodID on_leave = nullptr;
  if (!snapshot_callback_target(env, &callback_target, &on_enter, &on_leave)) {
    return;
  }

  jlong result = env->CallLongMethod(callback_target, on_leave,
                                     callback_id_from_userdata(userdata),
                                     static_cast<jlong>(frame->return_value.bits));
  env->DeleteLocalRef(callback_target);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    return;
  }
  frame->return_value.bits = static_cast<LhBits>(result);
}

const LhHookCallbacks g_bridge_callbacks = {
    bridge_on_enter,
    bridge_on_leave,
};

bool copy_arg_types(JNIEnv *env,
                    jintArray arg_types,
                    std::vector<LhNativeType> *native_types) {
  if (!native_types) {
    return false;
  }
  native_types->clear();
  if (!arg_types) {
    return true;
  }

  const jsize arg_count = env->GetArrayLength(arg_types);
  native_types->resize(static_cast<size_t>(arg_count));
  if (arg_count == 0) {
    return true;
  }

  std::vector<jint> raw_types(static_cast<size_t>(arg_count));
  env->GetIntArrayRegion(arg_types, 0, arg_count, raw_types.data());
  for (jsize i = 0; i < arg_count; ++i) {
    (*native_types)[static_cast<size_t>(i)] =
        static_cast<LhNativeType>(raw_types[static_cast<size_t>(i)]);
  }
  return true;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)reserved;
  g_jvm = vm;
  g_engine = lh_engine_create();
  if (!g_engine) {
    return 0;
  }
  if (pthread_key_create(&g_thread_key, detach_current_thread) == 0) {
    g_thread_key_created = true;
  }
  return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;
  if (g_jvm) {
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK && env) {
      release_all_managed_global_refs(env);
      std::lock_guard<std::mutex> lock(g_callback_mutex);
      if (g_native_lib_obj) {
        env->DeleteGlobalRef(g_native_lib_obj);
        g_native_lib_obj = nullptr;
      }
      g_on_enter_method = nullptr;
      g_on_leave_method = nullptr;
    }
  }

  if (g_thread_key_created) {
    pthread_key_delete(g_thread_key);
    g_thread_key_created = false;
  }
  if (g_engine) {
    lh_engine_destroy(g_engine);
    g_engine = nullptr;
  }
  g_jvm = nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_hook(JNIEnv *env,
                                                         jobject thiz,
                                                         jlong addr,
                                                         jint returnType,
                                                         jintArray argTypes,
                                                         jlong callbackId) {
  if (!g_engine || addr == 0 || callbackId <= 0) {
    return 0;
  }
  if (static_cast<uint64_t>(callbackId) >
      static_cast<uint64_t>(std::numeric_limits<uintptr_t>::max())) {
    return 0;
  }
  if (!ensure_callback_target(env, thiz)) {
    return 0;
  }

  std::vector<LhNativeType> native_types;
  if (!copy_arg_types(env, argTypes, &native_types)) {
    return 0;
  }

  LhNativeSignature signature{
      static_cast<LhNativeType>(returnType),
      native_types.empty() ? nullptr : native_types.data(),
      native_types.size(),
      0,
  };
  LhHookSpec spec{
      static_cast<LhAddr>(addr),
      &signature,
      0,
      reinterpret_cast<void *>(static_cast<uintptr_t>(callbackId)),
  };
  LhHookHandle handle{};
  LhStatus status = lh_register_hook(g_engine, &spec, &g_bridge_callbacks, &handle);
  return status == LH_OK ? static_cast<jlong>(handle.id) : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_unhook(JNIEnv *env,
                                                           jobject thiz,
                                                           jlong handle) {
  (void)env;
  (void)thiz;
  if (!g_engine || handle == 0) {
    return JNI_FALSE;
  }
  LhStatus status = lh_unregister_hook(g_engine,
                                       LhHookHandle{static_cast<uint64_t>(handle)});
  return status == LH_OK ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_allocCStringUtf8(JNIEnv *env,
                                                         jobject thiz,
                                                         jstring str) {
  (void)thiz;
  if (!str) {
    return 0;
  }
  const char *chars = env->GetStringUTFChars(str, nullptr);
  if (!chars) {
    return 0;
  }
  char *ptr = strdup(chars);
  env->ReleaseStringUTFChars(str, chars);
  return static_cast<jlong>(reinterpret_cast<intptr_t>(ptr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readJStringUtf8(JNIEnv *env,
                                                          jobject thiz,
                                                          jlong envPtr,
                                                          jlong jstringRef) {
  (void)thiz;
  if (!current_env_matches(env, envPtr) || jstringRef == 0) {
    return nullptr;
  }
  return static_cast<jstring>(
      env->NewLocalRef(reinterpret_cast<jstring>(static_cast<intptr_t>(jstringRef))));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_retainManagedRef(JNIEnv *env,
                                                            jobject thiz,
                                                            jlong envPtr,
                                                            jlong ref) {
  (void)thiz;
  if (!current_env_matches(env, envPtr) || ref == 0) {
    return 0;
  }
  return register_managed_global_ref(
      env, reinterpret_cast<jobject>(static_cast<intptr_t>(ref)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readManagedJStringUtf8(JNIEnv *env,
                                                                jobject thiz,
                                                                jlong ref) {
  (void)thiz;
  return static_cast<jstring>(snapshot_managed_global_ref(env, ref));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_createManagedJStringUtf8(JNIEnv *env,
                                                                    jobject thiz,
                                                                    jstring text) {
  (void)thiz;
  if (!env || !text) {
    return 0;
  }
  return register_managed_global_ref(env, text);
}

extern "C" JNIEXPORT void JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_releaseManagedRef(JNIEnv *env,
                                                              jobject thiz,
                                                              jlong ref) {
  (void)thiz;
  if (!env || ref == 0) {
    return;
  }
  release_managed_global_ref(env, ref);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_alloc(JNIEnv *env,
                                                   jobject thiz,
                                                   jint size) {
  (void)env;
  (void)thiz;
  if (size <= 0) {
    return 0;
  }
  return static_cast<jlong>(reinterpret_cast<intptr_t>(std::malloc(static_cast<size_t>(size))));
}

extern "C" JNIEXPORT void JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_free(JNIEnv *env,
                                                 jobject thiz,
                                                 jlong ptr) {
  (void)env;
  (void)thiz;
  if (ptr != 0) {
    std::free(reinterpret_cast<void *>(static_cast<intptr_t>(ptr)));
  }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_findModuleBase(JNIEnv *env, jobject thiz, jstring name) {
  (void)thiz;
  if (!name) {
    return 0;
  }

  const char *chars = env->GetStringUTFChars(name, nullptr);
  if (!chars) {
    return 0;
  }

  LhAddr base = 0;
  LhStatus status = lh_module_base(chars, &base);
  env->ReleaseStringUTFChars(name, chars);
  return status == LH_OK ? static_cast<jlong>(base) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_findSymbol(JNIEnv *env,
                                                          jobject thiz,
                                                          jstring module,
                                                          jstring name) {
  (void)thiz;
  if (!module || !name) {
    return 0;
  }

  const char *module_chars = env->GetStringUTFChars(module, nullptr);
  const char *name_chars = env->GetStringUTFChars(name, nullptr);
  if (!module_chars || !name_chars) {
    if (module_chars) {
      env->ReleaseStringUTFChars(module, module_chars);
    }
    if (name_chars) {
      env->ReleaseStringUTFChars(name, name_chars);
    }
    return 0;
  }

  LhAddr address = 0;
  LhStatus status = lh_resolve_symbol(module_chars, name_chars, &address);
  env->ReleaseStringUTFChars(module, module_chars);
  env->ReleaseStringUTFChars(name, name_chars);
  return status == LH_OK ? static_cast<jlong>(address) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_findMapBase(
    JNIEnv *env, jobject thiz, jstring moduleExpr, jstring perms) {
  (void)thiz;
  if (!moduleExpr || !perms) {
    return 0;
  }

  const char *module_chars = env->GetStringUTFChars(moduleExpr, nullptr);
  const char *perms_chars = env->GetStringUTFChars(perms, nullptr);
  if (!module_chars || !perms_chars) {
    if (module_chars) {
      env->ReleaseStringUTFChars(moduleExpr, module_chars);
    }
    if (perms_chars) {
      env->ReleaseStringUTFChars(perms, perms_chars);
    }
    return 0;
  }

  LhAddr base = 0;
  LhStatus status = lh_find_map_base(module_chars, perms_chars, &base);
  env->ReleaseStringUTFChars(moduleExpr, module_chars);
  env->ReleaseStringUTFChars(perms, perms_chars);
  return status == LH_OK ? static_cast<jlong>(base) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readPointerChain(JNIEnv *env, jobject thiz,
                                                      jlong ptr,
                                                      jlongArray offsetsArray) {
  (void)thiz;
  if (ptr == 0) {
    return 0;
  }

  LhAddr result = 0;
  if (!offsetsArray) {
    return lh_read_pointer_chain(static_cast<LhAddr>(ptr), nullptr, 0, &result) == LH_OK
               ? static_cast<jlong>(result)
               : 0;
  }

  jsize length = env->GetArrayLength(offsetsArray);
  if (length < 0) {
    return 0;
  }
  if (length == 0) {
    return lh_read_pointer_chain(static_cast<LhAddr>(ptr), nullptr, 0, &result) == LH_OK
               ? static_cast<jlong>(result)
               : 0;
  }

  jlong *offsets_ptr = env->GetLongArrayElements(offsetsArray, nullptr);
  if (!offsets_ptr) {
    return 0;
  }

  std::vector<int64_t> offsets(static_cast<size_t>(length));
  for (jsize i = 0; i < length; ++i) {
    offsets[static_cast<size_t>(i)] = static_cast<int64_t>(offsets_ptr[i]);
  }
  env->ReleaseLongArrayElements(offsetsArray, offsets_ptr, JNI_ABORT);

  LhStatus status = lh_read_pointer_chain(
      static_cast<LhAddr>(ptr), offsets.data(), offsets.size(), &result);
  return status == LH_OK ? static_cast<jlong>(result) : 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_readMemory(JNIEnv *env, jobject thiz,
                                                     jlong ptr, jint size) {
  (void)thiz;
  if (ptr == 0 || size <= 0) {
    return nullptr;
  }

  jbyteArray bytes = env->NewByteArray(size);
  if (!bytes) {
    return nullptr;
  }

  jbyte *buffer = env->GetByteArrayElements(bytes, nullptr);
  if (!buffer) {
    env->DeleteLocalRef(bytes);
    return nullptr;
  }

  LhStatus status = lh_safe_read(static_cast<LhAddr>(ptr), buffer, static_cast<size_t>(size));
  if (status == LH_OK) {
    env->ReleaseByteArrayElements(bytes, buffer, 0);
    return bytes;
  }

  env->ReleaseByteArrayElements(bytes, buffer, JNI_ABORT);
  env->DeleteLocalRef(bytes);
  return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_writeMemory(JNIEnv *env, jobject thiz,
                                                      jlong ptr,
                                                      jbyteArray data) {
  (void)thiz;
  if (!data) {
    return JNI_FALSE;
  }

  jsize len = env->GetArrayLength(data);
  if (len <= 0) {
    return JNI_TRUE;
  }
  if (ptr == 0) {
    return JNI_FALSE;
  }

  jbyte *buffer = env->GetByteArrayElements(data, nullptr);
  if (!buffer) {
    return JNI_FALSE;
  }

  LhStatus status = lh_safe_write(static_cast<LhAddr>(ptr), buffer, static_cast<size_t>(len));
  env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
  return status == LH_OK ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kulipai_luahook_hook_api_NativeLib_callFunction(JNIEnv *env,
                                                           jobject thiz,
                                                           jlong addr,
                                                           jint returnType,
                                                           jintArray argTypes,
                                                           jlongArray argBits) {
  (void)thiz;
  if (addr == 0) {
    return 0;
  }
  if ((argTypes == nullptr) != (argBits == nullptr)) {
    return 0;
  }

  const jsize argCount = argTypes ? env->GetArrayLength(argTypes) : 0;
  const jsize bitsCount = argBits ? env->GetArrayLength(argBits) : 0;
  if (argCount != bitsCount) {
    return 0;
  }

  std::vector<jint> raw_types(static_cast<size_t>(argCount));
  std::vector<jlong> raw_bits(static_cast<size_t>(argCount));
  if (argCount > 0) {
    env->GetIntArrayRegion(argTypes, 0, argCount, raw_types.data());
    env->GetLongArrayRegion(argBits, 0, argCount, raw_bits.data());
  }

  std::vector<LhNativeType> native_types(static_cast<size_t>(argCount));
  std::vector<LhNativeValue> native_args(static_cast<size_t>(argCount));
  for (jsize i = 0; i < argCount; ++i) {
    native_types[static_cast<size_t>(i)] =
        static_cast<LhNativeType>(raw_types[static_cast<size_t>(i)]);
    native_args[static_cast<size_t>(i)] = {
        native_types[static_cast<size_t>(i)],
        static_cast<LhBits>(raw_bits[static_cast<size_t>(i)])};
  }

  LhNativeSignature signature{
      static_cast<LhNativeType>(returnType),
      native_types.empty() ? nullptr : native_types.data(),
      static_cast<size_t>(argCount),
      0,
  };
  LhInvokeRequest request{
      static_cast<LhAddr>(addr),
      &signature,
      native_args.empty() ? nullptr : native_args.data(),
  };
  LhNativeValue result{static_cast<LhNativeType>(returnType), 0};
  LhStatus status = lh_invoke(&request, &result);
  return status == LH_OK ? static_cast<jlong>(result.bits) : 0;
}


