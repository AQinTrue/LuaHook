package com.kulipai.luahook.library

import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.lib.VarArgFunction

/**
 * 模板
 */

object LuaTem {

    // 注册一个方法东西在globals里
    fun registerTo(env: LuaValue) {

        //func
        env["func1"] = object : VarArgFunction() {

        }
        //...
    }

}
