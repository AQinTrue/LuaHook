package com.kulipai.luahook.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

object LuaTask {
    fun registerTo(env: LuaValue) {

        env["Task"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val func = args.checkfunction(1)
                val time = if (args.narg() == 2) args.checknumber(2).tolong() else 0L
                CoroutineScope(Dispatchers.Main).launch {

                    delay(time)
                    func.invoke()
                }

                return NIL
            }

        }

    }

}
