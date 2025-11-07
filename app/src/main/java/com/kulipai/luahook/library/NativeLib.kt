package com.kulipai.luahook.library

import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * 提供native能力的hook
 */

class NativeLib {
    init {
        System.loadLibrary("luahook")
    }


    external fun read(ptr: Long, size: Int): ByteArray?
    external fun write(ptr: Long, data: ByteArray): Boolean

    external fun moduleBase(name: String): Long
    external fun resolveSymbol(module: String, name: String): Long

    fun toLuaTable(): LuaTable {
        val table = LuaTable()

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

        table["module_base"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val name = args.arg(1).tojstring()
                val base = moduleBase(name)
                return CoerceJavaToLua.coerce(base)
            }
        }

        table["resolve_symbol"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val module = args.arg(1).tojstring()
                val name = args.arg(2).tojstring()
                return CoerceJavaToLua.coerce(resolveSymbol(module, name))
            }
        }

        table["sleep"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs? {
                val millis = args.arg(1).tolong()
                Thread.sleep(millis)
                return NIL
            }
        }

        table["get_module_base"] = table["module_base"] // 被重命名的函数

        return table
    }
}