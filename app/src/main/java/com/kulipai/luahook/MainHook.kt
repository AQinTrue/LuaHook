package com.kulipai.luahook

import com.kulipai.luahook.library.HookLib
import com.kulipai.luahook.library.LuaActivity
import com.kulipai.luahook.library.LuaImport
import com.kulipai.luahook.library.LuaUtil
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.d
import com.kulipai.luahook.util.e
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.json.JSONArray
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.dexkit.DexFinder


class MainHook: IXposedHookZygoteInit, IXposedHookLoadPackage {

    var MODULE_PACKAGE: String = ""

    lateinit var luaScript: String

    lateinit var suparam: IXposedHookZygoteInit.StartupParam



    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        MODULE_PACKAGE = startupParam.modulePath.substringAfterLast("/").substringBefore("-")
        MODULE_PACKAGE.d()

        XpHelper.initZygote(startupParam)
        suparam = startupParam

    }



    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        LuaHook_init(LoadPackageParamWrapper(lpparam))
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
        globals["XpHelper"] = CoerceJavaToLua.coerce(XpHelper::class.java)
        globals["DexFinder"] = CoerceJavaToLua.coerce(DexFinder::class.java)
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["DexKitBridge"] = CoerceJavaToLua.coerce(DexKitBridge::class.java)
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["suparam"] = CoerceJavaToLua.coerce(suparam)
        LuaActivity(null).registerTo(globals)
        HookLib(lpparam, scriptName).registerTo(globals)

        LuaImport(lpparam.classLoader, this::class.java.classLoader!!).registerTo(globals)
        LuaUtil.loadBasicLib(globals)

        return globals
    }

}