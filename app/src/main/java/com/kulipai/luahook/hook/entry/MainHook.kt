package com.kulipai.luahook.hook.entry

import com.kulipai.luahook.hook.api.HookLib
import com.kulipai.luahook.hook.api.LuaActivity
import com.kulipai.luahook.hook.api.LuaImport
import com.kulipai.luahook.hook.api.LuaUtil
import com.kulipai.luahook.util.LPParam
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.LoadPackageParamWrapper
import com.kulipai.luahook.util.e
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.dexkit.DexFinder
import kotlin.collections.iterator

/**
 * MainHook是用于xposed api小于100的hook主类
 * 加载lua脚本hook宿主
 */

class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    companion object {
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

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        luaHookInit(LoadPackageParamWrapper(lpparam))
    }

    fun luaHookInit(lpparam: LPParam) {
        // 读取luahook启用的app
        selectAppsString = LShare.read("/apps.txt").replace("\n", "")
        // 读取全局脚本
        luaScript = LShare.read("/global.lua")
        selectAppsList = if (selectAppsString.isNotEmpty() && selectAppsString != "") {
            selectAppsString.split(",").toMutableList()
        } else {
            mutableListOf()
        }

        // 全局脚本
        try {
            // 排除模块自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = createGlobals(lpparam, "[GLOBAL]").load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            // 捕获错误并美化输出
            val err = LuaUtil.simplifyLuaError(e.toString())
            "${lpparam.packageName}:[GLOBAL]:$err".e()
        }


        // app单独脚本
        if (lpparam.packageName in selectAppsList) {

            // 读取已保存的宿主app脚本的map
            for ((scriptName, v) in LShare.readMap("/${LShare.AppConf}/${lpparam.packageName}.txt")) {
                try {

                    if (v is Boolean) { // 兼容旧版luahook的存储格式
                        createGlobals(lpparam, scriptName)
                            .load(LShare.read("/${LShare.AppScript}/${lpparam.packageName}/$scriptName.lua"))
                            .call()
                    } else if ((v is JSONArray)) { // 新的格式，包含是否启用，描述和版本信息
                        if (v[0] as Boolean) {
                            createGlobals(lpparam, scriptName)
                                .load(LShare.read("/${LShare.AppScript}/${lpparam.packageName}/$scriptName.lua"))
                                .call()
                        }
                    }
                } catch (e: Exception) {
                    val err = LuaUtil.simplifyLuaError(e.toString())
                    ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                }
            }
        }

    }


    // 创建一个Hook的lua环境
    fun createGlobals(lpparam: LPParam, scriptName: String = ""): Globals {
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

        LuaImport(lpparam.classLoader, this::class.java.classLoader!!).registerTo(globals,lpparam.packageName)
        LuaUtil.loadBasicLib(globals)

        return globals
    }

}