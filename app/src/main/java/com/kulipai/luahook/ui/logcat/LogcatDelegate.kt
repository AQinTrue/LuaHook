package com.kulipai.luahook.ui.logcat

import com.kulipai.luahook.core.shell.ShellManager
import com.kulipai.luahook.core.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 读取系统日志的工具类
 */

object LogcatDelegate {

    suspend fun getSystemLogsByTagSince(tag: String, since: String? = null): List<String> {
        val command = if (since.isNullOrEmpty()) {
            "logcat -d $tag:* *:S"
        } else {
            "logcat -d -T \"$since\" $tag:* *:S"
        }

        return withContext(Dispatchers.IO) {
            val result = ShellManager.shell(command)
            when(result) {
                is ShellResult.Error -> {}
                is ShellResult.Success -> {}
            }
//            try {
//                val (result, err) =
//                if (err) {
//                    result.split("\n")
//                } else {
//                    mutableListOf()
//                }
//            } catch (e: Exception) {
////                e.printStackTrace()
//                mutableListOf()
//            }
        }
    }

    fun getCurrentLogcatTimeFormat(): String {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return dateFormat.format(Date())
    }
}