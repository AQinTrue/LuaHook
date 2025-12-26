package com.kulipai.luahook.core.shell

import android.content.Context
import com.kulipai.luahook.core.shizuku.ShizukuApi
import com.kulipai.luahook.util.LShare
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * ShellManager
 */

object ShellManager {

    enum class Mode {
        ROOT, SHIZUKU, NONE
    }


    private var _mode = MutableStateFlow(Mode.NONE)
    var mode = _mode.asStateFlow()

    fun setMode(newMode: Mode) {
        _mode.value = newMode
    }


    private var rootShell: Shell? = null

    fun init(context: Context) {

        // MOUNT_MASTER 标志
        try {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
            )
        }catch (_: Exception) {

        }


        Shell.getShell {
            if (it.isRoot) {
                rootShell = it
                setMode(Mode.ROOT)
                LShare.init(context)

            }
            // 显式尝试获取一次 Shell，会触发 root 权限申请（如必要）
            else if (Shizuku.getBinder() != null && Shizuku.pingBinder()) {
                //shizuku

                ShizukuApi.bindShizuku(context)
//                {
//                    mode = if (userService != null) Mode.SHIZUKU else Mode.NONE
//                    LShare.init(context)
//                    onInitialized?.invoke()
//                }
            } else {
                //no
                setMode(Mode.NONE)
            }
        }

    }




    /**
     * 执行命令，返回 (输出, 是否成功)
     */
    fun shell(cmd: String): ShellResult {
        return when (mode.value) {
            Mode.ROOT -> {
                try {
                    val result = Shell.cmd(cmd).exec()
                    if (result.isSuccess) {
                        ShellResult.Success(result.out.joinToString("\n"))
                    } else {
                        ShellResult.Error(result.err.joinToString("\n"))
                    }
                } catch (e: Exception) {
                    ShellResult.Error("Exception occurred", e)
                }
            }

            Mode.SHIZUKU -> {
                ShizukuApi.execShell(cmd)
            }

            else -> {
                ShellResult.Error("No shell method available")
            }
        }
    }

}