package com.kulipai.luahook.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import com.kulipai.luahook.BuildConfig
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

object ShizukuApi {

    //
    private fun IBinder.wrap() = ShizukuBinderWrapper(this)
    private fun IInterface.asShizukuBinder() = this.asBinder().wrap()

    private var userService: IUserService? = null

    val isBinderAvailable = MutableLiveData(false)
    val isPermissionGranted = MutableLiveData(false)
    val isServiceConnected = MutableLiveData(false)

    fun init() {
        // 使用 Sticky 确保能获取到初始状态
        Shizuku.addBinderReceivedListenerSticky {
            val available = Shizuku.pingBinder()
            isBinderAvailable.value = available
            if (available) {
                isPermissionGranted.value = checkShizukuPermission()
            }
        }

        Shizuku.addBinderDeadListener {
            isBinderAvailable.value = false
            isPermissionGranted.value = false
            isServiceConnected.value = false
            userService = null
        }

    }


    fun checkShizukuPermission(): Boolean = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED


    fun bindShizuku(context: Context) {
        if (!isBinderAvailable.value!! || !isPermissionGranted.value!!) {
            // Err
            return
        }


        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, UserService::class.java.name)
        ).daemon(false)
            .processNameSuffix("LuaHook_Shizuku")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)



        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {

                if (binder != null && binder.pingBinder()) {
                    userService = IUserService.Stub.asInterface(binder)
                    isServiceConnected.value = true
                    // Successful
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isServiceConnected.value = false
                userService = null

            }
        })

    }



    // TODO)) userService拿不到用私有接口shell
    fun execShell(cmd: String):Pair<String, Boolean> {
        if (userService == null) return Pair("Service not bound", false)
        return try {
            val result = userService!!.exec(cmd)
            Pair(result.output, result.success)
        } catch (e: RemoteException) {
            e.printStackTrace()
            Pair("RemoteException: ${e.message}", false)
        }

    }


}