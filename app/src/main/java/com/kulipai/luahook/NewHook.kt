package com.kulipai.luahook

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import com.kulipai.luahook.library.HookLib
import com.kulipai.luahook.library.LuaActivity
import com.kulipai.luahook.library.LuaImport
import com.kulipai.luahook.library.LuaUtil
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.d
import com.kulipai.luahook.util.e
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import top.sacz.xphelper.XpHelper
import java.io.File

lateinit var  LPParam_processName: String

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
fun read(path: String): String {
    if (File(path).exists()) {
        return File(path).readText()
    }
    return ""
}

fun readMap(path: String): MutableMap<String, Any?> {
    val jsonString = read(path)
    if (jsonString.isEmpty()) {
        return mutableMapOf()
    }
    return try {
        val jsonObject = JSONObject(jsonString)
        val map = mutableMapOf<String, Any?>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonObject.get(key)
        }
        map
    } catch (_: Exception) {
        mutableMapOf()
    }

}

fun simplifyLuaError(raw: String): String {
    val lines = raw.lines()

    // 1. 优先提取第一条真正的错误信息（不是 traceback）
    val primaryErrorLine = lines.firstOrNull { it.trim().matches(Regex(""".*:\d+ .+""")) }

    if (primaryErrorLine != null) {
        val match = Regex(""".*:(\d+) (.+)""").find(primaryErrorLine)
        if (match != null) {
            val (lineNum, msg) = match.destructured
            return "line $lineNum: $msg"
        }
    }

    // 2. 其次从 traceback 提取（防止所有匹配失败）
    val fallbackLine = lines.find { it.trim().matches(Regex(""".*:\d+: .*""")) }
    if (fallbackLine != null) {
        val match = Regex(""".*:(\d+): (.+)""").find(fallbackLine)
        if (match != null) {
            val (lineNum, msg) = match.destructured
            return "line $lineNum: $msg"
        }
    }

    return raw.lines().firstOrNull()?.take(100) ?: "未知错误"
}



class NewHook(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {

    var MODULE_PACKAGE : String =""

    lateinit var luaScript: String
    lateinit var suparam: IXposedHookZygoteInit.StartupParam



    //api 100
    init {
        LPParam_processName=param.processName
    }



    @SuppressLint("DiscouragedPrivateApi")
    override fun onPackageLoaded(lpparam: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(lpparam)
        suparam = createStartupParam(this.applicationInfo.sourceDir)
        MODULE_PACKAGE = suparam.modulePath.substringAfterLast("/").substringBefore("-")
        MODULE_PACKAGE.d()
        XpHelper.initZygote(suparam)

        LuaHook_init(ModuleInterfaceParamWrapper(lpparam))

    }


    fun LuaHook_init(lpparam: LPParam) {


        luaScript = LuaCode.code


        //全局脚本
        try {
            //排除自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = CreateGlobals(lpparam, "[LuaHook]").load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            val err = simplifyLuaError(e.toString())
            "${lpparam.packageName}:[LuaHook]:$err".e()
        }


    }




    fun CreateGlobals(lpparam: LPParam, scriptName: String = ""): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["suparam"] = CoerceJavaToLua.coerce(suparam)
        LuaActivity(null).registerTo(globals)
        HookLib(lpparam, scriptName).registerTo(globals)
        LuaUtil.loadBasicLib(globals)
        LuaImport(lpparam.classLoader, this::class.java.classLoader!!).registerTo(globals)

        return globals
    }



    fun createStartupParam(modulePath: String): IXposedHookZygoteInit.StartupParam {
        val clazz = IXposedHookZygoteInit.StartupParam::class.java
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()

        // 设置字段值
        val fieldModulePath = clazz.getDeclaredField("modulePath")
        fieldModulePath.isAccessible = true
        fieldModulePath.set(instance, modulePath)

        return instance
    }


}