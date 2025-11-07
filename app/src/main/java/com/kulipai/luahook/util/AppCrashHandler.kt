package com.kulipai.luahook.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.kulipai.luahook.activity.ErrorActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * CustomCrashHandler 是一个自定义的未捕获异常处理器。
 * 它会捕获应用程序中任何未捕获的异常，然后跳转到 ErrorActivity 显示错误信息。
 *
 * @param context 应用程序上下文
 * @param defaultHandler 默认的未捕获异常处理器，用于链式调用或在某些情况下使用。
 */
class AppCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    /**
     * 当线程发生未捕获异常时调用。
     *
     * @param t 发生异常的线程。
     * @param e 发生的异常对象。
     */
    override fun uncaughtException(t: Thread, e: Throwable) {
        // 打印异常堆栈，用于调试
//        e.printStackTrace()

        // 捕获错误信息
        val errorMessage = e.localizedMessage ?: "未知错误"
        val stackTrace = getStackTrace(e)

        // 创建 Intent 跳转到 ErrorActivity
        val intent = Intent(context, ErrorActivity::class.java).apply {
            // 添加标志，确保在一个新的任务栈中启动，并清除旧的 activity 栈
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(ErrorActivity.Companion.EXTRA_ERROR_MESSAGE, errorMessage)
            putExtra(ErrorActivity.Companion.EXTRA_STACK_TRACE, stackTrace)
        }

        // 启动 ErrorActivity
        context.startActivity(intent)

        // 终止当前进程，防止应用程序在显示错误后继续运行在不稳定的状态
        Process.killProcess(Process.myPid())
        exitProcess(1) // 退出 Java 虚拟机
    }

    /**
     * 将 Throwable 的堆栈跟踪转换为字符串。
     *
     * @param e 异常对象。
     * @return 异常的堆栈跟踪字符串。
     */
    private fun getStackTrace(e: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        return sw.toString()
    }

    companion object {
        /**
         * 初始化自定义错误处理器。
         * 建议在 Application 的 onCreate() 方法中调用此方法。
         *
         * @param context 应用程序上下文。
         */
        fun init(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler !is AppCrashHandler) { // 避免重复设置
                Thread.setDefaultUncaughtExceptionHandler(AppCrashHandler(context.applicationContext))
            }
        }
    }
}