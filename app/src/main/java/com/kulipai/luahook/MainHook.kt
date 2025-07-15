package com.kulipai.luahook

import com.kulipai.luahook.library.HookLib
import com.kulipai.luahook.library.LuaActivity
import com.kulipai.luahook.library.LuaImport
import com.kulipai.luahook.library.LuaUtil
import com.kulipai.luahook.util.LShare
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
    companion object {
//        init {
//            System.loadLibrary("dexkit")
//        }

        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        const val PATH = "/data/local/tmp/LuaHook"
    }


    lateinit var luaScript: String
    lateinit var selectAppsString: String

    lateinit var selectAppsList: MutableList<String>
    lateinit var suparam: IXposedHookZygoteInit.StartupParam



    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {

        XpHelper.initZygote(startupParam)
        suparam = startupParam

    }



    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        LuaHook_init(LoadPackageParamWrapper(lpparam))
    }


    fun LuaHook_init(lpparam: LPParam) {

        selectAppsString = read("$PATH/apps.txt").replace("\n", "")

        luaScript = read("$PATH/global.lua")

        if (selectAppsString.isNotEmpty() && selectAppsString != "") {
            selectAppsList = selectAppsString.split(",").toMutableList()
        } else {
            selectAppsList = mutableListOf()
        }


        //全局脚本
        try {
            //排除自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = CreateGlobals(lpparam, "[GLOBAL]").load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            val err = simplifyLuaError(e.toString())
            "${lpparam.packageName}:[GLOBAL]:$err".e()
        }


//        app单独脚本

        if (lpparam.packageName in selectAppsList) {

            for ((k, v) in readMap("$PATH/${LShare.AppConf}/${lpparam.packageName}.txt")) {
                try {
                    if (v is Boolean) {
                        CreateGlobals(
                            lpparam,
                            k
                        ).load(read("$PATH/${LShare.AppScript}/${lpparam.packageName}/$k.lua"))
                            .call()
                    } else if ((v is JSONArray)) {
                        if (v[0] as Boolean) {
                            CreateGlobals(
                                lpparam,
                                k
                            ).load(read("$PATH/${LShare.AppScript}/${lpparam.packageName}/$k.lua"))
                                .call()
                        }
                    }
                } catch (e: Exception) {
                    val err = simplifyLuaError(e.toString())
                    "${lpparam.packageName}:$k:$err".e()
                }
            }
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