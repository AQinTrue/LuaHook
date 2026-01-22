package com.kulipai.luahook.hook.api

import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.core.utils.dd
import org.luaj.LuaValue
import org.luaj.lib.OneArgFunction

/**
 * 模板
 */

class LuaProject(val projectName: String = "") {
    // 注册一个方法东西在globals里
    fun registerTo(env: LuaValue) {
        if (projectName.isEmpty()) {
            return
        }

        env["getProjectDir"] = object : OneArgFunction() {
            override fun call(p0: LuaValue): LuaValue {
                if (p0==NIL) {
                    return valueOf(WorkspaceFileManager.DIR + WorkspaceFileManager.Project + "/" + projectName + "/")
                }
                return valueOf(WorkspaceFileManager.DIR + WorkspaceFileManager.Project + "/" + projectName + "/" + p0.tojstring())
            }
        }
    }
}
