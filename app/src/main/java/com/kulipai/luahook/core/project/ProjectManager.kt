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
    val scope: List<String> = emptyList(), // "all" or list of packages
    val launcher: String = "" // Package to launch for testing
)

object ProjectManager {

    const val INFO_JSON = "/Project/info.json"

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
                var launcher = ""

                try {
                    val luaCode = WorkspaceFileManager.read(initPath)
                    if (luaCode.isNotEmpty()) {
                         try {
                            val chunk = globals.load(luaCode)
                            chunk.call()
                            
                            icon = globals.get("icon").optjstring("icon.png")
                            author = globals.get("author").optjstring("")
                            desc = globals.get("description").optjstring("")
                            launcher = globals.get("launcher").optjstring("")
                            
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

                projects.add(Project(name, isEnabled, icon, author, desc, scopeList, launcher))
            }
        }
        return projects
    }

    fun createProject(name: String, description: String, author: String, iconPath: String?, iconUnicode: String?, scope: List<String>, launcher: String): Boolean {
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
launcher = "$launcher"
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

    fun importProject(context: android.content.Context, uri: android.net.Uri): Boolean {
        val cacheFile = java.io.File(context.externalCacheDir, "import_temp.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        val tempZip = "${WorkspaceFileManager.DIR}/import_temp.zip"
        val tempDir = "${WorkspaceFileManager.DIR}/import_temp_dir"

        com.kulipai.luahook.core.shell.ShellManager.shell("rm -rf \"$tempZip\" \"$tempDir\"")
        com.kulipai.luahook.core.shell.ShellManager.shell("cat \"${cacheFile.absolutePath}\" > \"$tempZip\"")
        com.kulipai.luahook.core.shell.ShellManager.shell("mkdir -p \"$tempDir\"")
        com.kulipai.luahook.core.shell.ShellManager.shell("unzip -o \"$tempZip\" -d \"$tempDir\"")

        var root = tempDir
        if (!checkFiles(root)) {
            val res = com.kulipai.luahook.core.shell.ShellManager.shell("ls \"$tempDir\"")
            if (res is com.kulipai.luahook.core.shell.ShellResult.Success) {
                val lines = res.stdout.trim().split("\n").filter { it.isNotBlank() }
                if (lines.size == 1) {
                    val sub = "$tempDir/${lines[0]}"
                    if (checkFiles(sub)) {
                        root = sub
                    } else {
                        cleanupImport(tempZip, tempDir)
                        return false
                    }
                } else {
                    cleanupImport(tempZip, tempDir)
                    return false
                }
            } else {
                cleanupImport(tempZip, tempDir)
                return false
            }
        }

        val initLua = WorkspaceFileManager.read(root.replace(WorkspaceFileManager.DIR, "") + "/init.lua")
        val name = parseName(initLua)
        if (name.isEmpty()) {
            cleanupImport(tempZip, tempDir)
            return false
        }

        val dest = "${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$name"
        if (WorkspaceFileManager.directoryExists(dest)) {
            cleanupImport(tempZip, tempDir)
            return false
        }

        com.kulipai.luahook.core.shell.ShellManager.shell("mv \"$root\" \"$dest\"")

        val infoMap = WorkspaceFileManager.readMap(INFO_JSON)
        infoMap[name] = true
        WorkspaceFileManager.writeMap(INFO_JSON, infoMap)

        cleanupImport(tempZip, tempDir)
        return true
    }

    private fun cleanupImport(zip: String, dir: String) {
        com.kulipai.luahook.core.shell.ShellManager.shell("rm -rf \"$zip\" \"$dir\"")
    }

    private fun checkFiles(dir: String): Boolean {
        val init = "$dir/init.lua"
        val main = "$dir/main.lua"
        return WorkspaceFileManager.directoryExists(init) && WorkspaceFileManager.directoryExists(main)
    }

    private fun parseName(lua: String): String {
        return try {
            val globals = JsePlatform.standardGlobals()
            val chunk = globals.load(lua)
            chunk.call()
            globals.get("name").optjstring("")
        } catch (e: Exception) {
            ""
        }
    }

    fun exportProject(context: android.content.Context, name: String, uri: android.net.Uri): Boolean {
        val projectRelativePath = "${WorkspaceFileManager.Project}/$name"
        val projectFullPath = "${WorkspaceFileManager.DIR}$projectRelativePath"
        
        if (!WorkspaceFileManager.directoryExists(projectFullPath)) return false
        
        val cacheDir = java.io.File(context.externalCacheDir, "export_temp")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        
        val destDir = java.io.File(cacheDir, name)
        
        // Copy using shell
        val cmd = "cp -r \"$projectFullPath\" \"${cacheDir.absolutePath}\""
        com.kulipai.luahook.core.shell.ShellManager.shell(cmd)
        
        // Ensure we can read
        com.kulipai.luahook.core.shell.ShellManager.shell("chmod -R 777 \"${cacheDir.absolutePath}\"")
        
        if (!destDir.exists()) return false
        
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                java.util.zip.ZipOutputStream(output).use { zipOut ->
                    zipFolder(destDir, "", zipOut)
                }
            }
            cacheDir.deleteRecursively()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun zipFolder(folder: java.io.File, parentPath: String, zipOut: java.util.zip.ZipOutputStream) {
        val files = folder.listFiles() ?: return
        for (file in files) {
            val entryPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
            if (file.isDirectory) {
                zipFolder(file, entryPath, zipOut)
            } else {
                zipOut.putNextEntry(java.util.zip.ZipEntry(entryPath))
                file.inputStream().use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
}
