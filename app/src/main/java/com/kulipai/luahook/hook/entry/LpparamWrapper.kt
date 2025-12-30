package com.kulipai.luahook.hook.entry

import android.content.pm.ApplicationInfo
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedModuleInterface

/**
 * 统一抽象化api100和低于100的hook的部分接口
 */

lateinit var LPParam_processName: String

interface LPParam {
    val packageName: String
    val classLoader: ClassLoader
    val appInfo: ApplicationInfo
    val isFirstApplication: Boolean
    val processName: String
}

class LoadPackageParamWrapper(val origin: LoadPackageParam) : LPParam {
    override val packageName: String get() = origin.packageName
    override val classLoader: ClassLoader get() = origin.classLoader
    override val appInfo: ApplicationInfo get() = origin.appInfo
    override val processName: String get() = origin.processName
    override val isFirstApplication: Boolean get() = origin.isFirstApplication
}

class ModuleInterfaceParamWrapper(val origin: XposedModuleInterface.PackageLoadedParam) : LPParam {
    override val packageName get() = origin.packageName
    override val classLoader get() = origin.classLoader
    override val appInfo: ApplicationInfo get() = origin.applicationInfo
    override val processName: String get() = LPParam_processName
    override val isFirstApplication: Boolean get() = origin.isFirstPackage

}