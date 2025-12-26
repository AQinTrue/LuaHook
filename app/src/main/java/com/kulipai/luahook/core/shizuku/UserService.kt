package com.kulipai.luahook.core.shizuku

import android.os.RemoteException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * shizuku有关
 */


class UserService : IUserService.Stub() {

    @Throws(RemoteException::class)
    override fun destroy() {
        exitProcess(0)
    }

    @Throws(RemoteException::class)
    override fun exit() {
        destroy()
    }

    @Throws(RemoteException::class)
    override fun exec(command: String): ShellResult {
        val output = StringBuilder()
        var success = false
        try {
            // 用 sh -c 包装命令，确保管道、重定向等可以被 shell 正确解析
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            // 读取标准输出
            val inputReader = BufferedReader(InputStreamReader(process.inputStream))
            inputReader.useLines { lines ->
                lines.forEach { output.appendLine(it) }
            }

            // 读取错误输出（可选）
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            errorReader.useLines { lines ->
                lines.forEach { output.appendLine(it) }
            }

            val exitCode = process.waitFor()
            success = (exitCode == 0)

        } catch (e: IOException) {
            output.append("IOException: ${e.message}")
        } catch (e: InterruptedException) {
            output.append("InterruptedException: ${e.message}")
        }

        return ShellResult(output.toString().trim(), success)
    }

}