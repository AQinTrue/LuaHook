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

    external fun get_module_base(name: String): Long

    external fun read(ptr: Long, size: Int): ByteArray?
    external fun write(ptr: Long, data: ByteArray): Boolean

    fun toLuaTable(): LuaTable {
        val table = LuaTable()
        table["get_module_base"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val name = args.arg(1).tojstring()
                val base = get_module_base(name)
                return CoerceJavaToLua.coerce(base)
            }
        }
        table["read"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val ptr = args.arg(1).tolong()
                val size = args.arg(2).toint()
                val data = read(ptr, size) ?: return NIL
                val table = LuaTable(data.size, 0)
                for (i in 0 until data.size)
                    table[i + 1] = CoerceJavaToLua.coerce(data[i])
                return table
            }
        }
        table["write"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val ptr = args.arg(1).tolong()
                val data = args.arg(2).checktable()
                val bytes = ByteArray(data.length()) { idx ->
                    data.get(idx + 1).checkint().toByte()
                }
                return CoerceJavaToLua.coerce(write(ptr, bytes))
            }
        }
        return table
    }
}