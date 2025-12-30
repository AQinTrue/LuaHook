package com.kulipai.luahook.core.log

import android.util.Log

/**
 * 日志
 */

private val TAG = "LuaXposed"

fun Throwable.log(text: String = "Throwable"): Throwable {
    Log.e(TAG, text, this)
    return this
}

fun String.d(text: String = "Debug"): String {
    Log.d(TAG, this)
    return this
}

fun String.e(text: String = "Error"): String {
    Log.e(TAG, this)
    return this
}

fun printStackTrace() {
    val stackTrace = Throwable().stackTrace
    val stackTraceStr = stackTrace.joinToString("\n") { element ->
        "at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
    }
    ("StackTrace\n$stackTraceStr").d()
}
