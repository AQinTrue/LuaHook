package com.kulipai.luahook.library

import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

class NativeLib {
    init {
        System.loadLibrary("luahook")
    }

    // 获取指定名称的so在进程中的基址,
    // 如果还没有被加载到进程中, 则返回 0
    external fun get_module_base(name: String): Long

    fun toLuaTable(): LuaTable {
        val table = LuaTable()
        table["get_module_base"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val name = args.arg(1).tojstring()
                val base = get_module_base(name)
                return CoerceJavaToLua.coerce(base)
            }
        }
        return table
    }
}