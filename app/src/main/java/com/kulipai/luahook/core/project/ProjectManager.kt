package com.kulipai.luahook.core.project

import com.kulipai.luahook.core.file.WorkspaceFileManager
import org.json.JSONObject
import org.luaj.Globals
import org.luaj.lib.jse.JsePlatform

data class Project(
    val name: String,
    val isEnabled: Boolean,
    val icon: String, // Path or unicode
    val author: String = "",
    val description: String = "",
    val scope: List<String> = emptyList() // "all" or list of packages
)

object ProjectManager {

    private const val INFO_JSON = "/Project/info.json"

    fun getProjects(): List<Project> {
        val projects = mutableListOf<Project>()
        val infoMap = WorkspaceFileManager.readMap(INFO_JSON) // Name -> Boolean

        val globals: Globals = JsePlatform.standardGlobals()

        infoMap.forEach { (name, enabledObj) ->
            val isEnabled = enabledObj as? Boolean ?: false
            val projectRelativePath = "${WorkspaceFileManager.Project}/$name"
            val projectFullPath = "${WorkspaceFileManager.DIR}$projectRelativePath"
            val initPath = "$projectRelativePath/init.lua"

            if (WorkspaceFileManager.directoryExists(projectFullPath)) {
                var icon = "icon.png"
                var author = ""
                var desc = ""
                var scopeList = mutableListOf<String>()

                // Try to load init.lua using a simplified parser or regex if possible, 
                // but user asked to use JsePlatform to load it?
                // "globals.load(init.lua代码) globals.get("icon")"
                // Reading code manually to avoid executing potential harmful code just for listing? 
                // But user instruction was specific: load it.
                
                try {
                    val luaCode = WorkspaceFileManager.read(initPath)
                    if (luaCode.isNotEmpty()) {
                         try {
                            val chunk = globals.load(luaCode)
                            chunk.call()
                            
                            icon = globals.get("icon").optjstring("icon.png")
                            author = globals.get("author").optjstring("")
                            desc = globals.get("description").optjstring("")
                            
                            val scopeLua = globals.get("scope")
                            if (scopeLua.isstring()) {
                                scopeList.add(scopeLua.tojstring())
                            } else if (scopeLua.istable()) {
                                val len = scopeLua.length()
                                for (i in 1..len) {
                                    scopeList.add(scopeLua.get(i).tojstring())
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                projects.add(Project(name, isEnabled, icon, author, desc, scopeList))
            }
        }
        return projects
    }

    fun createProject(name: String, description: String, author: String, iconPath: String?, iconUnicode: String?, scope: List<String>): Boolean {
        if (name.isEmpty()) return false
        
        val projectRelativePath = "${WorkspaceFileManager.Project}/$name"
        val projectFullPath = "${WorkspaceFileManager.DIR}$projectRelativePath"

        if (WorkspaceFileManager.directoryExists(projectFullPath)) return false // Already exists

        if (!WorkspaceFileManager.ensureDirectoryExists("${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}")) return false
        if (!WorkspaceFileManager.ensureDirectoryExists(projectFullPath)) return false
        
        // Double Check
        if (!WorkspaceFileManager.directoryExists(projectFullPath)) return false
        
        // Handle Icon
        var iconValue = "icon.png"
        if (!iconUnicode.isNullOrEmpty()) {
            iconValue = iconUnicode // e.g. "\u8989"
        } else if (iconPath != null) {
            val destIcon = "$projectFullPath/icon.png"
            // iconPath is now a local file path (cache)
            val script = "cat \"$iconPath\" > \"$destIcon\"\nchmod 666 \"$destIcon\""
            com.kulipai.luahook.core.shell.ShellManager.shell(script)
        }

        // Generate init.lua
        val scopeLua = if (scope.contains("all")) "\"all\"" else {
            "{\n" + scope.joinToString(",\n") { "    \"$it\"" } + "\n}"
        }
        
        val initLua = """
name = "$name"
description = "$description"
author = "$author"
icon = "$iconValue"
scope = ${scopeLua.trimIndent()}
""".trimIndent()
        
        if (!WorkspaceFileManager.write("$projectRelativePath/init.lua", initLua)) return false
        
        // Create empty main.lua
        if (!WorkspaceFileManager.write("$projectRelativePath/main.lua", "-- Main entry for $name\n\nprint(\"Hello from $name\")")) return false

        // Update info.json
        val infoMap = WorkspaceFileManager.readMap(INFO_JSON)
        infoMap[name] = true // Enabled by default
        return WorkspaceFileManager.writeMap(INFO_JSON, infoMap)
    }

    fun setProjectEnabled(name: String, enabled: Boolean) {
        val infoMap = WorkspaceFileManager.readMap(INFO_JSON)
        infoMap[name] = enabled
        WorkspaceFileManager.writeMap(INFO_JSON, infoMap)
    }
    
    fun deleteProject(name: String) {
        val projectDir = "${WorkspaceFileManager.Project}/$name"
        WorkspaceFileManager.rm(projectDir)
        
        val infoMap = WorkspaceFileManager.readMap(INFO_JSON)
        infoMap.remove(name)
        WorkspaceFileManager.writeMap(INFO_JSON, infoMap)
    }
}
