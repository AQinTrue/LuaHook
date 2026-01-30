package com.kulipai.luahook.core.shizuku
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

/*
    部分设备不支持Shizuku Server，
    该类提供一个备选方案，通过反射调用Shizuku的私有方法newProcess来执行Shell命令
 */

object ShizukuShellFallback {
    private const val TAG = "ShizukuShellFallback"

    /**
     * 执行 Shell 命令的备选方案
     * 自动封装为 sh -c "command" 形式，支持管道和复杂参数
     *
     * @param command 要执行的命令字符串
     * @return Pair(结果字符串, 是否成功)
     */
    fun exec(command: String?): Pair<String, Boolean> {
        if (!Shizuku.pingBinder()) {
            return Pair("Error: Shizuku is not running or binder is dead.", false)
        }

        val cmdArray = arrayOf("sh", "-c", command)
        var process: Process? = null
        val output = StringBuilder()
        var isSuccess = true // 默认为 true

        try {
            // 1. 反射获取私有的 newProcess 方法
            val shizukuClass: Class<*> = Shizuku::class.java
            val newProcessMethod: Method = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            // 2. 调用方法
            process = newProcessMethod.invoke(null, cmdArray, null, null) as Process?
            val p = process ?: return Pair("Error: Failed to create process.", false)

            // 3. 读取标准输出
            BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            // 4. 等待进程结束并检查退出码
            val exitCode = p.waitFor()

            // 5. 如果退出码不为0，读取错误流并将 success 设为 false
            if (exitCode != 0) {
                isSuccess = false
                BufferedReader(InputStreamReader(p.errorStream)).use { errorReader ->
                    // 如果标准输出已经有内容，加个换行区分
                    if (output.isNotEmpty()) output.append("\n")
                    output.append("[Exit Code]: ").append(exitCode).append("\n[Error Log]:\n")
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            return Pair("Exception: ${e.message}", false)
        } finally {
            process?.destroy()
        }

        return Pair(output.toString().trim(), isSuccess)
    }
}