package com.kulipai.luahook.hook.api

import com.kulipai.luahook.core.file.LShare
import com.kulipai.luahook.core.file.LShare.read
import com.kulipai.luahook.core.log.d
import com.kulipai.luahook.core.log.e
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import org.luaj.Globals
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.lib.OneArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * imports可以导入宿主的类或者模块自己的类
 * 优先宿主
 */

class LuaImport(
    private val classLoader: ClassLoader,
    private val thisLoader: ClassLoader,
) {
    fun registerTo(env: Globals, packageName: String) {

        env["require"] = object : OneArgFunction() {
            override fun call(scriptName: LuaValue): LuaValue {
                try {
                    return env.load(read(LShare.AppScript + "/" + packageName + "/" + scriptName + ".lua"))
                        .call()
                } catch (e: Exception) {
                    ("[Error] | Package: $packageName | Script: $scriptName | Message: "+ LuaUtil.simplifyLuaError(
                        e.toString()
                    )).d()
                }
                return NIL
            }
        }

        env["imports"] = object : OneArgFunction() {
            override fun call(classNameValue: LuaValue): LuaValue {
                return try {

                    val className = classNameValue.checkjstring()
                    var clazz: Class<*>
                    try {
                        clazz = XposedHelpers.findClass(className, classLoader)
                    } catch (_: ClassNotFoundError) {
                        try {
                            clazz = XposedHelpers.findClass(className, thisLoader)
                        } catch (_: ClassNotFoundError) {
                            "Error:import.ClassNotFoundError($className)".e()
                            clazz = Void::class.java
                        }
                    }
                    val luaClass = CoerceJavaToLua.coerce(clazz)

                    // 提取简名作为全局变量（例如 java.io.File -> File）
                    val simpleName = className.substringAfterLast('.')
                    env.set(simpleName, luaClass)
                    luaClass
                } catch (e: Exception) {
                    throw LuaError("import.err: " + e.message)
                }
            }
        }
    }


}
