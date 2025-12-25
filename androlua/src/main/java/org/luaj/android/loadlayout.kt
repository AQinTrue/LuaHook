package org.luaj.android

import android.content.Context
import com.androlua.LuaContext
import com.androlua.LuaLayout
import com.kulipai.luahook.util.e
import com.nekolaska.ktx.argAt
import com.nekolaska.ktx.firstArg
import com.nekolaska.ktx.secondArg
import org.luaj.Globals
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import top.sacz.xphelper.XpHelper

class loadlayout(var mContext: Context?, val globals: Globals) : VarArgFunction() {

    override fun invoke(args: Varargs): Varargs {
        if(mContext == null) {
            mContext = XpHelper.context
        }
        mContext = mContext!!
        return when (args.narg()) {

            // use cast instead of LuaContext.getContext
            1 -> LuaLayout(mContext!!).load(args.firstArg(), globals)
            2 -> LuaLayout(mContext!!).load(args.firstArg(), args.secondArg().checktable())
            3 -> LuaLayout(mContext!!).load(
                args.firstArg(),
                args.secondArg().checktable(),
                args.argAt(3)
            )

            else -> ("loadlayout: invalid arguments").e().let { NIL }
        }
    }
}
