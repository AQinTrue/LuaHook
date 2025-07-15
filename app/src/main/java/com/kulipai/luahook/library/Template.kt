package com.kulipai.luahook.library

import org.luaj.LuaValue
import org.luaj.lib.VarArgFunction

object LuaTem {

    fun registerTo(env: LuaValue) {

        //func
        env["func1"] = object : VarArgFunction() {

        }
        //...
    }

}
