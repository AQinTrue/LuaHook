package com.kulipai.luahook.hook.api

import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.core.file.WorkspaceFileManager.read
import com.kulipai.luahook.core.log.d
import com.kulipai.luahook.core.log.e
import dalvik.system.DexClassLoader
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import org.luaj.Globals
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.lib.OneArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import dalvik.system.DexFile
import java.io.File
import java.util.Enumeration

/**
 * imports可以导入宿主的类或者模块自己的类
 * 优先宿主
 */

class LuaImport(
    private val classLoader: ClassLoader,
    private val thisLoader: ClassLoader,
) {
    private val dexLoaders = ArrayList<ClassLoader>()

    fun registerTo(env: Globals, packageName: String, projectName: String = "") {

        env["require"] = object : OneArgFunction() {
            override fun call(scriptName: LuaValue): LuaValue {
                try {
                    return env.load(read(WorkspaceFileManager.Project + "/" + projectName + "/" + scriptName + ".lua"))
                        .call()
                } catch (e: Exception) {
                    ("[Error] | Package: $packageName | Script: $scriptName | Message: " + LuaUtil.simplifyLuaError(
                        e.toString()
                    )).d()
                }
                return NIL
            }
        }

        env["loadDex"] = object : OneArgFunction() {
            override fun call(pathValue: LuaValue): LuaValue {
                var path = pathValue.checkjstring()
                if (!path.startsWith("/")) {
                    path =
                        WorkspaceFileManager.DIR + WorkspaceFileManager.Project + "/" + projectName + "/" + path
                }
                WorkspaceFileManager.ensureReadable(path)
                val file = File(path)
                if (!file.exists()) {
                    "loadDex: File not found: $path".e()
                    return LuaValue.FALSE
                }
                return try {
                    val loader = DexClassLoader(
                        path,
                        null,
                        null,
                        classLoader
                    )
                    dexLoaders.add(loader)
                    LuaValue.TRUE
                } catch (e: Exception) {
                    ("loadDex error: " + e.message).e()
                    LuaValue.FALSE
                }
            }
        }

        env["imports"] = object : OneArgFunction() {
            override fun call(classNameValue: LuaValue): LuaValue {
                return try {

                    val className = classNameValue.checkjstring()
                    
                    // Handle wildcard import: "package.name.*"
                    if (className.endsWith(".*")) {
                         val pkgName = className.substring(0, className.length - 2)
                         val pkgLen = pkgName.length
                         val dots = pkgName.count { it == '.' }
                         // Collect all loaders
                         val allLoaders = ArrayList<ClassLoader>()
                         allLoaders.add(classLoader)
                         allLoaders.addAll(dexLoaders)
                         allLoaders.add(thisLoader)
                         
                         var count = 0
                         for (loader in allLoaders) {
                             val dexFiles = getDexFiles(loader)
                             for (dex in dexFiles) {
                                 val entries = dex.entries()
                                 while (entries.hasMoreElements()) {
                                     val clsName = entries.nextElement()
                                     // Check if class belongs to the package (direct child)
                                     if (clsName.startsWith(pkgName) && clsName.length > pkgLen) {
                                          val rest = clsName.substring(pkgLen + 1)
                                          if (!rest.contains(".")) {
                                               // This is a direct class in the package
                                               try {
                                                   val clazz = loader.loadClass(clsName)
                                                   val simple = clsName.substringAfterLast('.')
                                                   env.set(simple, CoerceJavaToLua.coerce(clazz))
                                                   count++
                                               } catch (_: Exception) {}
                                          }
                                     }
                                 }
                             }
                         }
                         return LuaValue.valueOf(count)
                    }

                    var clazz: Class<*>? = null
                    try {
                        clazz = XposedHelpers.findClass(className, classLoader)
                    } catch (_: ClassNotFoundError) {
                        for (loader in dexLoaders) {
                            try {
                                clazz = loader.loadClass(className)
                                if (clazz != null) break
                            } catch (_: Exception) {
                            }
                        }
                        if (clazz == null) {
                            try {
                                clazz = XposedHelpers.findClass(className, thisLoader)
                            } catch (_: ClassNotFoundError) {
                                "Error:import.ClassNotFoundError($className)".e()
                                clazz = Void::class.java
                            }
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



    private fun getDexFiles(loader: ClassLoader): List<DexFile> {
        val dexFiles = ArrayList<DexFile>()
        try {
            val pathListField = XposedHelpers.findField(loader.javaClass, "pathList")
            val pathList = pathListField.get(loader)
            val dexElementsField = XposedHelpers.findField(pathList.javaClass, "dexElements")
            val dexElements = dexElementsField.get(pathList) as Array<Any>
            for (element in dexElements) {
                val dexFileField = XposedHelpers.findField(element.javaClass, "dexFile")
                val dexFile = dexFileField.get(element) as? DexFile
                if (dexFile != null) {
                    dexFiles.add(dexFile)
                }
            }
        } catch (e: Exception) {
            // e.printStackTrace()
        }
        return dexFiles
    }

}
