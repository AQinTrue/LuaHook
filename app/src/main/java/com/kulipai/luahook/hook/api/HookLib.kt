package com.kulipai.luahook.hook.api

import com.kulipai.luahook.util.LPParam
import com.kulipai.luahook.util.d
import com.kulipai.luahook.util.e
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luaj.LuaFunction
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.LuaValue.NONE
import org.luaj.Varargs
import org.luaj.lib.OneArgFunction
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.CoerceLuaToJava
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.dexkit.DexFinder
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * 封装给lua用于hook的所有工具函数类等等
 */

class HookLib(private val lpparam: LPParam, private val scriptName: String = "") {

    fun registerTo(globals: LuaValue) {
        globals["XpHelper"] = CoerceJavaToLua.coerce(XpHelper::class.java)
        globals["DexFinder"] = CoerceJavaToLua.coerce(DexFinder::class.java)
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["DexKitBridge"] = CoerceJavaToLua.coerce(DexKitBridge::class.java)

        globals["lpparam"] = CoerceJavaToLua.coerce(lpparam)

        globals["getField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val targetObject = args.arg(1)
                val fieldName = args.checkjstring(2)

                if (targetObject.isuserdata(Any::class.java)) {
                    val target = targetObject.touserdata(Any::class.java)
                    val result = XposedHelpers.getObjectField(target, fieldName)
                    return CoerceJavaToLua.coerce(result)
                } else {
                    return NIL
                }
            }
        }

        globals["setField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val targetObject = args.arg(1)
                val fieldName = args.checkjstring(2)
                val fieldValue = fromLuaValue(args.arg(3))

                if (targetObject.isuserdata(Any::class.java)) {
                    val target = targetObject.touserdata(Any::class.java)
                    XposedHelpers.setObjectField(target, fieldName, fieldValue)
                }
                return NIL
            }
        }

        globals["getStaticField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val className = args.checkjstring(1)
                val fieldName = args.checkjstring(2)
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                val result = XposedHelpers.getStaticObjectField(clazz, fieldName)
                return CoerceJavaToLua.coerce(result)

            }
        }

        globals["setStaticField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {

                val className = args.checkjstring(1)
                val fieldName = args.checkjstring(2)
                val fieldValue = fromLuaValue(args.arg(3))
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                XposedHelpers.setStaticObjectField(clazz, fieldName, fieldValue)
                return NIL

            }
        }

        globals["findClass"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 确保 self 是字符串
                val className = args.checkjstring(1)
                val loader: ClassLoader = if (args.narg() == 1) {
                    lpparam.classLoader
                } else {
                    args.checkuserdata(2, ClassLoader::class.java) as ClassLoader
                }

                // 查找类
                val clazz = XposedHelpers.findClass(className, loader)
                return CoerceJavaToLua.coerce(clazz) // 返回 Java Class 对象
            }
        }

        globals["log"] = object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val message = arg.tojstring()
                "$scriptName:$message".d()
                XposedBridge.log("$scriptName:$message")
                return NIL
            }
        }

        globals["invoke"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val obj = args.arg(1)  // Lua 传入的第一个参数
                val methodName = args.checkjstring(2) // 方法名
                var isStatic = false

                // 获取 Java 对象
                val targetObject: Any? = when {
                    obj.isuserdata(XC_MethodHook.MethodHookParam::class.java) -> {
                        val param =
                            obj.touserdata(XC_MethodHook.MethodHookParam::class.java) as XC_MethodHook.MethodHookParam
                        param.thisObject // 获取 thisObject
                    }

                    obj.isuserdata(Class::class.java) -> {
                        isStatic = true
                        obj.touserdata(Class::class.java)
                    }

                    obj.isuserdata(Any::class.java) -> obj.touserdata(Any::class.java)
                    else -> throw IllegalArgumentException("invoke 需要 Java 对象")
                }

                // 收集参数
                val javaParams = mutableListOf<Any?>()
                for (i in 3..args.narg()) {
                    javaParams.add(fromLuaValue(args.arg(i)))
                }

                var result: Any
                if (isStatic) {
                    // 反射调用方法
                    result = XposedHelpers.callStaticMethod(
                        targetObject as Class<*>?,
                        methodName,
                        *javaParams.toTypedArray()
                    )

                } else {
                    // 反射调用方法
                    result = XposedHelpers.callMethod(
                        targetObject,
                        methodName,
                        *javaParams.toTypedArray()
                    )
                }

                return CoerceJavaToLua.coerce(result) // 把 Java 结果返回 Lua
            }
        }



        /**
         * 安全地将 LuaValue 转换为 Java Class 对象
         * 处理普通 Class 和 JavaClass（接口或类）的情况
         */
        fun safeToJavaClass(luaValue: LuaValue): Class<*>? {
            if (!luaValue.isuserdata()) return null

            val userData = luaValue.touserdata()

            // 直接是 Class 类型的情况
            if (userData is Class<*>) {
                return userData
            }

            // JavaClass 或其他包装类型的情况
            return try {
                // 尝试获取内部字段
                val possibleFields = listOf("clazz", "class", "jclass", "classObject")
                for (fieldName in possibleFields) {
                    try {
                        val field = userData.javaClass.getDeclaredField(fieldName)
                        field.isAccessible = true
                        val fieldValue = field.get(userData)
                        if (fieldValue is Class<*>) {
                            return fieldValue
                        }
                    } catch (_: NoSuchFieldException) {
                        // 继续尝试下一个字段
                    }
                }

                // 尝试调用可能的方法
                val possibleMethods = listOf("getClassObject", "toClass", "asClass", "getJavaClass")
                for (methodName in possibleMethods) {
                    try {
                        val method = userData.javaClass.getMethod(methodName)
                        val result = method.invoke(userData)
                        if (result is Class<*>) {
                            return result
                        }
                    } catch (_: NoSuchMethodException) {
                        // 继续尝试下一个方法
                    }
                }

                // 记录反射信息以便调试
                "无法转换 ${userData.javaClass.name} 为 Class 对象".d()

                // 最后尝试通过名称获取
                try {
                    // 尝试获取类名
                    val nameMethod = userData.javaClass.getMethod("getName")
                    val className = nameMethod.invoke(userData) as? String
                    if (className != null) {
                        return Class.forName(className)
                    }
                } catch (_: Exception) {
                    // 忽略并返回 null
                }

                null
            } catch (t: Throwable) {
                t.toString().e()
                null
            }
        }


        // 封装获取构造函数的 Lua 函数
        globals["getConstructor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Class::class.java)) {
                    return error("Usage: getConstructor(class, argType1, argType2, ...)")
                }

                val clazz = args.arg(1).touserdata(Class::class.java) as Class<*>
                val paramTypes = mutableListOf<Class<*>>()

                for (i in 2..args.narg()) {

                    if (args.arg(i).isstring()) {
                        val typeName = args.checkjstring(i)
                        val type = parseType(typeName, lpparam.classLoader)
                        paramTypes.add(type as Class<*>)
                    } else if (args.arg(i).isuserdata()) {
                        // 使用安全的转换方法替代直接 as 转换
                        val classObj = safeToJavaClass(args.arg(i))
                        if (classObj != null) {
                            paramTypes.add(classObj)
                        } else {
                            "getConstructor: 无法将参数 $i 转换为 Class".e()
                            return error("getConstructor: 无法将参数 $i 转换为 Class")
                        }
                    } else {
                        "getConstructor: 参数 $i 类型不支持".e()
                        return error("getConstructor: 参数类型不支持")
                    }

                }


                val constructor = clazz.getConstructor(*paramTypes.toTypedArray())
                return CoerceJavaToLua.coerce(constructor)
            }
        }


        // 封装创建新实例的 Lua 函数
        globals["newInstance"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Constructor::class.java)) {
                    return error("Usage: newInstance(constructor, arg1, arg2, ...)")
                }

                val constructor = args.arg(1).touserdata(Constructor::class.java) as Constructor<*>
                val params = mutableListOf<Any?>()

                for (i in 2..args.narg()) {
                    params.add(fromLuaValue(args.arg(i)))
                }


                constructor.isAccessible = true // 允许访问非公共构造函数
                val instance = constructor.newInstance(*params.toTypedArray())
                return CoerceJavaToLua.coerce(instance)

            }
        }


        // 已弃用
        globals["new"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val classNameOrClass = args.arg(1)
                val clazz: Class<*> = when {
                    //字符串
                    classNameOrClass.isstring() -> {
                        val className = args.checkjstring(1)
                        Class.forName(className)
                    }
                    //类
                    classNameOrClass.isuserdata(Class::class.java) -> classNameOrClass.touserdata(
                        Class::class.java
                    ) as Class<*>

                    else -> {
                        throw IllegalArgumentException("First argument must be class name (String) or Class object")
                    }
                }

                val params = mutableListOf<Any?>()
                val paramTypes = mutableListOf<Class<*>>()
                for (i in 2..args.narg()) {
                    val luaValue = args.arg(i)
                    val value = fromLuaValue(luaValue)
                    params.add(value)
                    paramTypes.add(
                        value?.javaClass ?: Any::class.java
                    ) // Still using this for simplicity, see note below
                }

                val constructor = try {
                    clazz.getConstructor(*paramTypes.toTypedArray())
                } catch (e: NoSuchMethodException) {
                    // Attempt to find constructor with more flexible type matching
                    var foundConstructor: Constructor<*>? = null
                    for (ctor in clazz.constructors) {
                        if (ctor.parameterCount == params.size) {
                            val ctorParamTypes = ctor.parameterTypes
                            var match = true
                            for (i in params.indices) {
                                val param = params[i]
                                val ctorParamType = ctorParamTypes[i]
                                if (param != null && !ctorParamType.isAssignableFrom(param.javaClass)) {
                                    match = false
                                    break
                                } else if (param == null && ctorParamType.isPrimitive) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                foundConstructor = ctor
                                break
                            }
                        }
                    }
                    foundConstructor
                        ?: throw e // Re-throw the original NoSuchMethodException if no flexible match found
                }

                constructor.isAccessible = true // 允许访问非公共构造函数
                val instance = constructor.newInstance(*params.toTypedArray())
                return CoerceJavaToLua.coerce(instance)


            }
        }


        globals["callMethod"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata()) {
                    throw IllegalArgumentException("First argument must be a Method object")
                }

                // Extract the Method object from the JavaInstance wrapper
                val methodArg = args.arg(1)
                val methodObj = methodArg.touserdata()
                val method: Method

                // Handle different ways the Method might be wrapped
                if (methodObj is Method) {
                    method = methodObj

                } else {
                    "callMethod param1 is not method".e()
                    return NIL
                }

                // Check if the method is static
                val isStatic = Modifier.isStatic(method.modifiers)

                // For static methods, we can call directly with all arguments
                // For instance methods, the second argument should be the instance

                val result: Any?

                if (isStatic) {
                    // Convert all Lua arguments to Java objects
                    val javaArgs = Array(args.narg() - 1) { i ->
                        fromLuaValue(args.arg(i + 2))
                    }

                    // Call the static method
                    result = method.invoke(null, *javaArgs)
                } else {
                    // Need at least 2 arguments for instance methods
                    if (args.narg() < 2) {
                        ("Instance method requires an object instance as second parameter").e()
                        return NIL
                    }

                    // Get the instance object
                    val instance = fromLuaValue(args.arg(2))

                    // Convert remaining Lua arguments to Java objects
                    val javaArgs = Array(args.narg() - 2) { i ->
                        fromLuaValue(args.arg(i + 3))
                    }

                    // Call the instance method
                    result = method.invoke(instance, *javaArgs)
                }

                // Convert the result back to Lua
                return CoerceJavaToLua.coerce(result)

            }
        }


        // 单独错误处理
        globals["hook"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val classNameOrTableOrMethod = args.arg(1)
                var classLoader: ClassLoader?
                val methodName: String
                if (classNameOrTableOrMethod.istable()) {//table 全新语法
                    val table = classNameOrTableOrMethod.checktable()
                    val method = table.get("method")
                    val before = table.get("before")
                    val after = table.get("after")
                    if (method.isstring()) {//方法为字符串
                        val clazz = table.get("class")
                        classLoader =
                            table.get("classloader").touserdata(ClassLoader::class.java)
                                ?: lpparam.classLoader

                        val params: LuaTable? = table.get("params").checktable()

                        val paramTypes = mutableListOf<Class<*>>()

                        params?.let {
                            for (key in it.keys()) {
                                val param = it.get(key)
                                when {
                                    param.isstring() -> {
                                        // 支持更多类型转换
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)
                                    }
                                    // 可以扩展更多类型的处理
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        }

                        if (clazz.isstring()) {///////clazz字符串

                            XposedHelpers.findAndHookMethod(
                                clazz.tojstring(),
                                classLoader,
                                method.tojstring(),
                                *paramTypes.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam?) {
                                        before?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }

                                        }
                                    }

                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        after?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                    }
                                }
                            )
                        } else if (clazz.isuserdata(Class::class.java)) {
                            XposedHelpers.findAndHookMethod(
                                clazz.touserdata(Class::class.java),
                                method.tojstring(),
                                *paramTypes.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam?) {
                                        before?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }

                                        }
                                    }

                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        after?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                    }
                                }
                            )
                        }

                    } else if (method.isuserdata(Method::class.java)) {

                        XposedBridge.hookMethod(
                            method.touserdata(Method::class.java),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    before?.takeUnless { it.isnil() }?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                }

                                override fun afterHookedMethod(param: MethodHookParam?) {
                                    after?.takeUnless { it.isnil() }?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                }
                            }
                        )

                    }

                } else if (classNameOrTableOrMethod.isstring()) { /////////string,
                    classLoader =
                        args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                    val className = classNameOrTableOrMethod.tojstring()
                    methodName = args.checkjstring(3)

                    val paramTypes = mutableListOf<Class<*>>()
                    if (args.arg(4).istable()) {
                        for (key in args.arg(4).checktable().keys()) {
                            val param = args.arg(4).checktable().get(key)

                            when {

                                param.isstring() -> {
                                    val typeStr = param.tojstring()
                                    val type = parseType(typeStr, classLoader)
                                    paramTypes.add(type!!)

                                }

                                else -> {
                                    val classObj = safeToJavaClass(param)
                                    if (classObj != null) {
                                        paramTypes.add(classObj)
                                    }
                                }
                            }
                        }
                    } else {
                        // 收集参数类型
                        for (i in 4 until args.narg() - 1) {
                            val param = args.arg(i)
                            when {

                                param.isstring() -> {
                                    // 支持更多类型转换
                                    val typeStr = param.tojstring()
                                    val type = parseType(typeStr, classLoader)
                                    paramTypes.add(type!!)

                                }
                                // 可以扩展更多类型的处理
                                else -> {
                                    val classObj = safeToJavaClass(param)
                                    if (classObj != null) {
                                        paramTypes.add(classObj)
                                    }
                                }
                            }
                        }
                    }


                    val beforeFunc = args.optfunction(args.narg() - 1, null)
                    val afterFunc = args.optfunction(args.narg(), null)

                    XposedHelpers.findAndHookMethod(
                        className,
                        classLoader,
                        methodName,
                        *paramTypes.toTypedArray(),
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam?) {

                                beforeFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)

                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }

                                }
                            }

                            override fun afterHookedMethod(param: MethodHookParam?) {
                                afterFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)

                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }

                                }
                            }
                        }
                    )


                } else if (classNameOrTableOrMethod.isuserdata(Method::class.java)) {   ///method

                    val method = args.arg(1).touserdata(Method::class.java) as Method
                    val beforeFunc = args.optfunction(2, null)
                    val afterFunc = args.optfunction(3, null)

                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam?) {
                                beforeFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)

                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }

                                }
                            }

                            override fun afterHookedMethod(param: MethodHookParam?) {
                                afterFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)
                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }
                                }
                            }
                        }
                    )

                    return TRUE
                }

                return NIL
            }
        }


        // 单独错误处理
        globals["replace"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val classNameOrTableOrMethod = args.arg(1)
                var classLoader: ClassLoader?
                val methodName: String

                if (classNameOrTableOrMethod.istable()) {//table 全新语法
                    val table = classNameOrTableOrMethod.checktable()
                    val method = table.get("method")
                    val replace = table.get("replace")
                    if (method.isstring()) {//方法为字符串
                        val clazz = table.get("class")
                        classLoader =
                            table.get("classloader").touserdata(ClassLoader::class.java)
                                ?: lpparam.classLoader

                        val params: LuaTable? = table.get("params").checktable()

                        val paramTypes = mutableListOf<Class<*>>()

                        params?.let {
                            for (key in it.keys()) {
                                val param = it.get(key)
                                when {
                                    param.isstring() -> {
                                        // 支持更多类型转换
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)
                                    }
                                    // 可以扩展更多类型的处理
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        }

                        if (clazz.isstring()) {///////clazz字符串

                            XposedHelpers.findAndHookMethod(
                                clazz.tojstring(),
                                classLoader,
                                method.tojstring(),
                                *paramTypes.toTypedArray(),
                                object : XC_MethodReplacement() {
                                    override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                                        replace?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                        return null
                                    }
                                }
                            )
                        } else if (clazz.isuserdata(Class::class.java)) {
                            XposedHelpers.findAndHookMethod(
                                clazz.touserdata(Class::class.java),
                                method.tojstring(),
                                *paramTypes.toTypedArray(),
                                object : XC_MethodReplacement() {
                                    override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                                        replace?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                        return null
                                    }
                                }
                            )
                        }

                    } else if (method.isuserdata(Method::class.java)) {

                        XposedBridge.hookMethod(
                            method.touserdata(Method::class.java),
                            object : XC_MethodReplacement() {
                                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                                    replace?.takeUnless { it.isnil() }?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                    return null
                                }
                            }
                        )

                    }

                } else if (classNameOrTableOrMethod.isstring()) { /////////string,
                    classLoader =
                        args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                    val className = classNameOrTableOrMethod.tojstring()
                    methodName = args.checkjstring(3)


                    val paramTypes = mutableListOf<Class<*>>()

                    if (args.arg(4).istable()) {
                        for (key in args.arg(4).checktable().keys()) {
                            val param = args.arg(4).checktable().get(key)
                            when {
                                param.isstring() -> {
                                    val typeStr = param.tojstring()
                                    val type = parseType(typeStr, classLoader)
                                    paramTypes.add(type!!)

                                }

                                else -> {
                                    val classObj = safeToJavaClass(param)
                                    if (classObj != null) {
                                        paramTypes.add(classObj)
                                    }
                                }
                            }
                        }
                    } else {
                        // 收集参数类型
                        for (i in 4 until args.narg()) {
                            val param = args.arg(i)
                            when {

                                param.isstring() -> {
                                    val typeStr = param.tojstring()
                                    val type = parseType(typeStr, classLoader)
                                    paramTypes.add(type!!)

                                }

                                else -> {
                                    val classObj = safeToJavaClass(param)
                                    if (classObj != null) {
                                        paramTypes.add(classObj)
                                    }
                                }
                            }
                        }
                    }


                    val replaceFunc = args.optfunction(args.narg(), null)

                    XposedHelpers.findAndHookMethod(
                        className,
                        classLoader,
                        methodName,
                        *paramTypes.toTypedArray(),
                        object : XC_MethodReplacement() {
                            override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                                replaceFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)
                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }
                                }
                                return null
                            }
                        }
                    )


                } else if (classNameOrTableOrMethod.isuserdata(Method::class.java)) {   ///method

                    val method = args.arg(1).touserdata(Method::class.java) as Method
                    val replaceFunc = args.optfunction(2, null)

                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodReplacement() {
                            override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                                replaceFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)
                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }
                                }
                                return null
                            }
                        }
                    )
                    return TRUE
                }
                return NIL
            }
        }





        globals["hookAll"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrTableOrClass = args.arg(1)
                    var classLoader: ClassLoader?
                    lateinit var clazz: Class<*>
                    lateinit var method: String
                    lateinit var beforeFunc: LuaFunction
                    lateinit var afterFunc: LuaFunction

                    if (classNameOrTableOrClass.istable()) {//table 全新语法
                        val table = classNameOrTableOrClass.checktable()
                        val method = table.get("method")
                        val before = table.get("before")
                        val after = table.get("after")
                        val clazz = table.get("class")
                        classLoader =
                            table.get("classloader").touserdata(ClassLoader::class.java)
                                ?: lpparam.classLoader

                        if (clazz.isstring()) {///////clazz字符串

                            XposedBridge.hookAllMethods(
                                XposedHelpers.findClass(clazz.tojstring(), classLoader),
                                method.tojstring(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam?) {
                                        before?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }

                                        }
                                    }

                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        after?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                    }
                                }
                            )
                        } else if (clazz.isuserdata(Class::class.java)) {
                            XposedBridge.hookAllMethods(
                                clazz.touserdata(Class::class.java),
                                method.tojstring(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam?) {
                                        before?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }

                                        }
                                    }

                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        after?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    } else if (classNameOrTableOrClass.isstring()) { // If first arg is a string (class name)
                        classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                        val className = classNameOrTableOrClass.tojstring()
                        clazz = XposedHelpers.findClass(className, classLoader)
                        method = args.arg(3).toString()
                        beforeFunc = args.optfunction(4, null)
                        afterFunc = args.optfunction(5, null)
                    } else if (classNameOrTableOrClass.isuserdata(Class::class.java)) {
                        clazz = args.arg(1).touserdata(Class::class.java) as Class<*>
                        method = args.arg(2).toString()
                        beforeFunc = args.optfunction(3, null)
                        afterFunc = args.optfunction(4, null)
                    }

                    XposedBridge.hookAllMethods(
                        clazz,
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam?) {
                                beforeFunc.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)
                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }
                                }
                            }

                            override fun afterHookedMethod(param: MethodHookParam?) {
                                afterFunc.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)
                                    try {
                                        func.call(luaParam)
                                    } catch (e: Exception) {
                                        val err = LuaUtil.simplifyLuaError(e.toString())
                                        ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                    }
                                }
                            }
                        }
                    )

                    return TRUE

                } catch (e: Exception) {
                    val err = LuaUtil.simplifyLuaError(e.toString())
                    ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                    return FALSE
                }
            }
        }


        globals["hookctor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrTableOrClass = args.arg(1)
                    var classLoader: ClassLoader?

                    if (classNameOrTableOrClass.istable()) {//table 全新语法
                        val table = classNameOrTableOrClass.checktable()
                        val before = table.get("before")
                        val after = table.get("after")
                        val clazz = table.get("class")
                        classLoader =
                            table.get("classloader").touserdata(ClassLoader::class.java)
                                ?: lpparam.classLoader

                        val params: LuaTable? = table.get("params").checktable()

                        val paramTypes = mutableListOf<Class<*>>()

                        params?.let {
                            for (key in it.keys()) {
                                val param = it.get(key)
                                when {
                                    param.isstring() -> {
                                        // 支持更多类型转换
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)
                                    }
                                    // 可以扩展更多类型的处理
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        }

                        if (clazz.isstring()) {///////clazz字符串

                            XposedHelpers.findAndHookConstructor(
                                clazz.tojstring(),
                                classLoader,
                                *paramTypes.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam?) {
                                        before?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }

                                        }
                                    }

                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        after?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                    }
                                }
                            )
                        } else if (clazz.isuserdata(Class::class.java)) {
                            XposedHelpers.findAndHookConstructor(
                                clazz.touserdata(Class::class.java),
                                *paramTypes.toTypedArray(),
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam?) {
                                        before?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }

                                        }
                                    }

                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        after?.takeUnless { it.isnil() }?.let { func ->
                                            val luaParam = CoerceJavaToLua.coerce(param)
                                            try {
                                                func.call(luaParam)
                                            } catch (e: Exception) {
                                                val err = LuaUtil.simplifyLuaError(e.toString())
                                                ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                            }
                                        }
                                    }
                                }
                            )
                        }


                    } else if (classNameOrTableOrClass.isstring()) { /////
                        classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                        val className = classNameOrTableOrClass.tojstring()
                        val paramTypes = mutableListOf<Class<*>>()

                        if (args.arg(3).istable()) {
                            for (key in args.arg(3).checktable().keys()) {
                                val param = args.arg(3).checktable().get(key)
                                when {
                                    param.isstring() -> {
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)

                                        paramTypes.add(type!!)
                                    }

                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        } else {
                            for (i in 3 until args.narg() - 1) {
                                val param = args.arg(i)
                                when {
                                    param.isstring() -> {
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)
                                    }

                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        }


                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookConstructor(
                            className,
                            classLoader,
                            *paramTypes.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    beforeFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                }

                                override fun afterHookedMethod(param: MethodHookParam?) {
                                    afterFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                }
                            }
                        )

                    } else if (classNameOrTableOrClass.isuserdata(Class::class.java)) {

                        val classs = classNameOrTableOrClass.touserdata(Class::class.java) as Class<*>

                        val paramTypes = mutableListOf<Class<*>>()

                        //参数处理
                        if (args.arg(2).istable()) {
                            for (key in args.arg(2).checktable().keys()) {
                                val param = args.arg(2).checktable().get(key)
                                when {
                                    param.isstring() -> {
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, lpparam.classLoader)
                                        paramTypes.add(type!!)
                                    }

                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        } else {
                            for (i in 2 until args.narg() - 1) {
                                val param = args.arg(i)
                                when {
                                    param.isstring() -> {

                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, lpparam.classLoader)
                                        paramTypes.add(type!!)
                                    }

                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        }


                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookConstructor(
                            classs,
                            *paramTypes.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    beforeFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                }

                                override fun afterHookedMethod(param: MethodHookParam?) {
                                    afterFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)
                                        try {
                                            func.call(luaParam)
                                        } catch (e: Exception) {
                                            val err = LuaUtil.simplifyLuaError(e.toString())
                                            ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                                        }
                                    }
                                }
                            }
                        )


                    }

                    return NIL

                } catch (e: Exception) {
                    val err = LuaUtil.simplifyLuaError(e.toString())
                    ("[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err").e()
                    return NIL
                }
            }
        }

        globals["hookcotr"] = globals["hookctor"]


        // 已弃用
        globals["createProxy"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // --- CORRECTED ARGUMENT PARSING ---

                // Argument 1: Interface Class (as Java Class userdata at index 1)
                val interfaceClass = args.checkuserdata(1, Class::class.java) as Class<*>
                if (!interfaceClass.isInterface) {
                    // Use argerror for better Lua-side error reporting
                    argerror(1, "Expected an interface class")
                }

                // Argument 2: Lua Table with method implementations (at index 2)
                val implementationTable = args.checktable(2)

                // Argument 3: Optional ClassLoader (as ClassLoader userdata at index 3)
                // Default to the interface's classloader if argument 3 is nil or absent.
                val classLoaderOrDefault = interfaceClass.classLoader // Define default value
                val classLoader = args.optuserdata(
                    3, // Index of the Lua argument
                    ClassLoader::class.java, // Expected Java type
                    classLoaderOrDefault // Default value if arg 3 is nil/absent
                ) as ClassLoader? // Cast the result (Object) to ClassLoader?
                    ?: classLoaderOrDefault // Use default if optuserdata returned null (which it shouldn't with a non-null default, but good practice)

                // Ensure we have a non-null loader (should always be true here)
                val finalClassLoader = classLoader ?: classLoaderOrDefault

                // --- END OF CORRECTIONS ---

                println("createProxy: Interface=${interfaceClass.name}, Loader=${finalClassLoader}")

                // Create the InvocationHandler
                val handler =
                    LuaInvocationHandler(implementationTable) // Assumes LuaInvocationHandler class is defined elsewhere

                try {
                    // Create the proxy instance using the specified class loader
                    val proxyInstance = Proxy.newProxyInstance(
                        finalClassLoader, // Use the resolved class loader
                        arrayOf(interfaceClass),
                        handler
                    )

                    // Return the proxy instance coerced to a LuaValue
                    return CoerceJavaToLua.coerce(proxyInstance)

                } catch (e: Exception) {
                    println("createProxy error: ${e.message}")
                    e.printStackTrace() // Log the full stack trace for debugging
                    // Consider returning a LuaError for better script handling
                    // throw LuaError("Failed to create proxy: ${e.message}")
                    return NIL
                }
            }
        }

        globals["native"] = NativeLib().toLuaTable()
    }


    // 将Lua值转换回Java类型
    fun fromLuaValue(value: LuaValue?): Any? {
        return when {
            value == null || value.isnil() -> null
            value.isboolean() -> value.toboolean()
            value.isint() -> value.toint()
            value.islong() -> value.tolong()
            value.isnumber() -> value.todouble()
            value.isstring() -> value.tojstring()
            value.isuserdata() -> value.touserdata()
            else -> null
        }
    }


    fun parseType(
        typeStr: String,
        classLoader: ClassLoader? = Thread.currentThread().contextClassLoader
    ): Class<*>? {
        val typeMap = mapOf(
            "int" to Int::class.javaPrimitiveType,
            "long" to Long::class.javaPrimitiveType,
            "boolean" to Boolean::class.javaPrimitiveType,
            "double" to Double::class.javaPrimitiveType,
            "float" to Float::class.javaPrimitiveType,
            "char" to Char::class.javaPrimitiveType,
            "byte" to Byte::class.javaPrimitiveType,
            "short" to Short::class.javaPrimitiveType,
            "String" to String::class.java
        )

        var baseType = typeStr.trim()
        var arrayDepth = 0

        // 计算数组维度
        while (baseType.endsWith("[]")) {
            arrayDepth++
            baseType = baseType.substring(0, baseType.length - 2).trim()
        }

        val baseClass = typeMap[baseType] ?: try {
            XposedHelpers.findClass(baseType, classLoader)
        } catch (_: ClassNotFoundException) {
            "Parameter error".d()
            return null
        }

        // 构建数组类型
        var resultClass = baseClass
        repeat(arrayDepth) {
            resultClass = java.lang.reflect.Array.newInstance(resultClass, 0).javaClass
        }

        return resultClass
    }


    class LuaInvocationHandler(private val luaTable: LuaTable) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val methodName = method.name
            val luaFunction = luaTable.get(methodName)

            // Debugging log
            // println("Proxy invoked: Method=$methodName, LuaFunction found=${!luaFunction.isnil()}")

            return if (luaFunction.isfunction()) {

                // Convert Java arguments to LuaValue varargs
                val luaArgs = convertArgsToLuaValues(args)
                // Call the Lua function
                // Note: Lua functions typically don't receive 'self' implicitly when called from Java proxies.
                // We pass only the method arguments.
                val result = luaFunction.invoke(luaArgs) // Use invoke for Varargs

                // Convert the Lua return value back to the expected Java type
                CoerceLuaToJava.coerce(
                    result.arg1(),
                    method.returnType
                ) // result is Varargs, get first value
            } else {
                // Handle standard Object methods or missing implementations
                when (methodName) {
                    "toString" -> "LuaProxy<${proxy.javaClass.interfaces.firstOrNull()?.name ?: "UnknownInterface"}>@${
                        Integer.toHexString(
                            hashCode()
                        )
                    }"

                    "hashCode" -> luaTable.hashCode() // Or System.identityHashCode(proxy)? Or handler's hashcode? Be consistent.
                    "equals" -> proxy === args?.get(0) // Standard proxy equality check
                    else -> {
                        // No Lua function found for this method
                        println("Warning: No Lua implementation found for proxied method: $methodName")
                        // Return default value based on return type, or throw exception
                        if (method.returnType == Void.TYPE) {
                            null // Return null for void methods
                        } else if (method.returnType.isPrimitive) {
                            // Return default primitive values (0, false)
                            when (method.returnType) {
                                Boolean::class.javaPrimitiveType -> false
                                Char::class.javaPrimitiveType -> '\u0000'
                                Byte::class.javaPrimitiveType -> 0.toByte()
                                Short::class.javaPrimitiveType -> 0.toShort()
                                Int::class.javaPrimitiveType -> 0
                                Long::class.javaPrimitiveType -> 0L
                                Float::class.javaPrimitiveType -> 0.0f
                                Double::class.javaPrimitiveType -> 0.0
                                else -> throw UnsupportedOperationException("Unsupported primitive return type: ${method.returnType.name}")
                            }
                        } else {
                            // Return null for object return types
                            null
                            // Alternatively, throw an exception:
                            // throw UnsupportedOperationException("No Lua implementation for method: $methodName")
                        }
                    }
                }
            }
        }

        // Helper function to convert Java args array to LuaValue Varargs
        private fun convertArgsToLuaValues(javaArgs: Array<Any?>?): Varargs {
            if (javaArgs == null || javaArgs.isEmpty()) {
                return NONE
            }
            val luaArgs = javaArgs.map { CoerceJavaToLua.coerce(it) }.toTypedArray()
            // Important: Use varargsOf, not listOf, to create Varargs correctly
            return LuaValue.varargsOf(luaArgs)
        }


    }

}
