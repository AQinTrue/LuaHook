package com.kulipai.luahook.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.kulipai.luahook.BuildConfig
import com.kulipai.luahook.shizuku.IUserService
import com.kulipai.luahook.shizuku.UserService
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

/**
 * ShellManager
 */

object ShellManager {

    enum class Mode {
        ROOT, SHIZUKU, NONE
    }

    private var mode: Mode = Mode.NONE
    private var shizukuBound = false
    private var userService: IUserService? = null
    private var rootShell: Shell? = null

    /**
     * 初始化，Application.onCreate() 中调用
     */
    fun init(context: Context, onInitialized: (() -> Unit)? = null) {

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
                mode = Mode.ROOT
                LShare.init(context)
                onInitialized?.invoke()
            }
            // 显式尝试获取一次 Shell，会触发 root 权限申请（如必要）
            else if (Shizuku.getBinder() != null && Shizuku.pingBinder()) {
                //shizuku

                bindShizuku(context) {
                    mode = if (userService != null) Mode.SHIZUKU else Mode.NONE
                    LShare.init(context)
                    onInitialized?.invoke()
                }
            } else {
                //no
                mode = Mode.NONE
                onInitialized?.invoke()
            }
        }

    }


    private fun bindShizuku(context: Context, onBound: (() -> Unit)? = null) {
        if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
//            "Missing Shizuku permission, cannot bind service".d()
            onBound?.invoke()
            return
        }

        if (shizukuBound) {
            onBound?.invoke()
            return
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, UserService::class.java.name)
        ).daemon(false)
            .processNameSuffix("adb_service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    userService = IUserService.Stub.asInterface(binder)
                    shizukuBound = true

                    onBound?.invoke()
                } else {

                    onBound?.invoke()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                userService = null
                shizukuBound = false
                Log.w("ShellManager", "Shizuku service disconnected")
            }
        })
    }

    /**
     * 执行命令，返回 (输出, 是否成功)
     */
    fun shell(cmd: String): Pair<String, Boolean> {
        return when (mode) {
            Mode.ROOT -> {
                try {
                    val result = Shell.cmd(cmd).exec()
                    if (result.isSuccess) {
                        Pair(result.out.joinToString("\n"), true)
                    } else {
                        Pair(result.err.joinToString("\n"), false)
                    }
                } catch (e: Exception) {
                    Pair("Exception: ${e.message}", false)
                }
            }

            Mode.SHIZUKU -> {
                if (userService == null) return Pair("Service not bound", false)
                return try {
                    val result = userService!!.exec(cmd)
                    Pair(result.output, result.success)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                    Pair("RemoteException: ${e.message}", false)
                }
            }


            else -> {
                Pair("No shell method available", false)
            }
        }
    }

    fun getMode(): Mode = mode
    fun setMode(m: Mode) {
        mode = m
    }

    fun setShell(s: Shell) {
        rootShell = s
    }
}
