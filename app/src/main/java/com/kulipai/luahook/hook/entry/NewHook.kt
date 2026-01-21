package com.kulipai.luahook.hook.entry

import android.annotation.SuppressLint
import com.kulipai.luahook.hook.api.HookLib
import com.kulipai.luahook.hook.api.LuaActivity
import com.kulipai.luahook.hook.api.LuaImport
import com.kulipai.luahook.hook.api.LuaUtil
import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.core.log.e
import de.robv.android.xposed.IXposedHookZygoteInit
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONArray
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import top.sacz.xphelper.XpHelper
import kotlin.collections.iterator

/**
 * api100专用新hook入口
 */

class NewHook(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) : XposedModule(base, param) {
    companion object {
        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        const val PATH = "/data/local/tmp/LuaHook"
    }


    lateinit var luaScript: String
    lateinit var selectAppsString: String

    lateinit var selectAppsList: MutableList<String>
    lateinit var suparam: IXposedHookZygoteInit.StartupParam


    //api 100
    init {
        LPParam_processName = param.processName
    }


    @SuppressLint("DiscouragedPrivateApi")
    override fun onPackageLoaded(lpparam: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(lpparam)
        suparam = createStartupParam(this.applicationInfo.sourceDir)
        XpHelper.initZygote(suparam)

        luaHookInit(ModuleInterfaceParamWrapper(lpparam))


    }


    fun luaHookInit(lpparam: LPParam) {

        selectAppsString = WorkspaceFileManager.read("/apps.txt").replace("\n", "")

        luaScript = WorkspaceFileManager.read("/global.lua")

        selectAppsList = if (selectAppsString.isNotEmpty() && selectAppsString != "") {
            selectAppsString.split(",").toMutableList()
        } else {
            mutableListOf()
        }


        //全局脚本
        try {
            //排除自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = createGlobals(lpparam, "[GLOBAL]").load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            val err = LuaUtil.simplifyLuaError(e.toString())
            "${lpparam.packageName}:[GLOBAL]:$err".e()
        }


//        app单独脚本


        // app单独脚本
        if (lpparam.packageName in selectAppsList) {

            for ((scriptName, v) in WorkspaceFileManager.readMap("/${WorkspaceFileManager.AppConf}/${lpparam.packageName}.txt")) {
                try {
                    if (v is Boolean) {  // 兼容旧版luahook的存储格式
                        createGlobals(lpparam, scriptName)
                            .load(WorkspaceFileManager.read("/${WorkspaceFileManager.AppScript}/${lpparam.packageName}/$scriptName.lua"))
                            .call()
                    } else if ((v is JSONArray)) {
                        if (v[0] as Boolean) {
                            createGlobals(lpparam, scriptName)
                                .load(WorkspaceFileManager.read("/${WorkspaceFileManager.AppScript}/${lpparam.packageName}/$scriptName.lua"))
                                .call()
                        }
                    }
                } catch (e: Exception) {
                    val err = LuaUtil.simplifyLuaError(e.toString())
                    ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                }
            }
        }
        
        // Project Hooks
        try {
            val projectInfo = WorkspaceFileManager.readMap("/Project/info.json")
            for ((projectName, isEnabled) in projectInfo) {
                 if (isEnabled == true) {
                      try {
                           val tempGlobals = createGlobals(lpparam, projectName)
                           val projectDir = "/Project/$projectName"
                           val initScript = WorkspaceFileManager.read("$projectDir/init.lua")
                           
                           tempGlobals.load(initScript).call()
                           
                           val scope = tempGlobals.get("scope")
                           var shouldRun = false
                           
                           if (scope.isstring() && scope.tojstring() == "all") {
                                shouldRun = true
                           } else if (scope.istable()) {
                                val len = scope.length()
                                for (i in 1..len) {
                                    if (scope.get(i).tojstring() == lpparam.packageName) {
                                         shouldRun = true
                                         break
                                    }
                                }
                           }
                           
                           if (shouldRun) {
                                val mainScript = WorkspaceFileManager.read("$projectDir/main.lua")
                                tempGlobals.load(mainScript).call()
                           }
                      } catch (e: Exception) {
                           val err = LuaUtil.simplifyLuaError(e.toString())
                           "${lpparam.packageName}:[Project:$projectName]:$err".e()
                      }
                 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }


    fun createGlobals(lpparam: LPParam, scriptName: String = ""): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["suparam"] = CoerceJavaToLua.coerce(suparam)
        LuaActivity(null).registerTo(globals)
        HookLib(lpparam, scriptName).registerTo(globals)
        LuaUtil.loadBasicLib(globals)
        LuaImport(lpparam.classLoader, this::class.java.classLoader!!).registerTo(globals,lpparam.packageName)

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