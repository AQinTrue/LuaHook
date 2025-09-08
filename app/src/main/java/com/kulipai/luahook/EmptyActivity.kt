package com.kulipai.luahook

import android.os.Bundle
import com.kulipai.luahook.library.LuaActivity
import com.kulipai.luahook.library.LuaImport
import com.kulipai.luahook.library.LuaUtil
//import androidx.activity.enableEdgeToEdge
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luaj.Globals
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.activity.BaseActivity
import top.sacz.xphelper.dexkit.DexFinder

class EmptyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.getStringExtra("script")
        CreateGlobals().load(data).call()

    }

    fun CreateGlobals(): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["XpHelper"] = CoerceJavaToLua.coerce(XpHelper::class.java)
        globals["DexFinder"] = CoerceJavaToLua.coerce(DexFinder::class.java)
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["DexKitBridge"] = CoerceJavaToLua.coerce(DexKitBridge::class.java)
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["activity"] = CoerceJavaToLua.coerce(this)
        LuaActivity(this).registerTo(globals)

        LuaImport(this::class.java.classLoader!!, this::class.java.classLoader!!).registerTo(globals)
        LuaUtil.loadBasicLib(globals)
        return globals
    }

}