#pragma once

#include <android/log.h>

#ifndef LH_LOG_TAG
#define LH_LOG_TAG "LuaHookNative"
#endif

#define LH_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LH_LOG_TAG, __VA_ARGS__)
#define LH_LOGI(...) __android_log_print(ANDROID_LOG_INFO, LH_LOG_TAG, __VA_ARGS__)
