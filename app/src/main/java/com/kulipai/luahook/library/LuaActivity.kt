package com.kulipai.luahook.library

import android.content.Context
import android.content.Intent
import com.kulipai.luahook.EmptyActivity
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.android.loadlayout
import org.luaj.lib.TwoArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import top.sacz.xphelper.XpHelper

class LuaActivity(val context: Context?) {
    fun registerTo(env: LuaValue) {

            //注入界面
            env["injectActivity"] = object : TwoArgFunction() {
                override fun call(
                    context: LuaValue?,
                    data: LuaValue? //
                ): LuaValue? {
                    val activity = context?.touserdata(Context::class.java)

                    val intent = Intent(activity, EmptyActivity::class.java)
                    intent.putExtra("script",data?.tojstring())
                    activity?.startActivity(intent)

                    return NIL
                }

            }


            // loadlayout
            if (context != null) {
                env["loadlayout"] = CoerceJavaToLua.coerce(loadlayout(context, env as Globals))
            }else{
                env["loadlayout"] = CoerceJavaToLua.coerce(loadlayout(XpHelper.context, env as Globals))
            }

        }




}