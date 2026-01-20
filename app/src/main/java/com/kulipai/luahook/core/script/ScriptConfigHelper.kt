package com.kulipai.luahook.core.script

import com.kulipai.luahook.core.file.WorkspaceFileManager
import org.json.JSONArray
import org.json.JSONException

object ScriptConfigHelper {

    fun readConf(packageName: String): MutableList<MutableMap.MutableEntry<String, Any?>> {
        val path = WorkspaceFileManager.AppConf + "/" + packageName + ".txt"
        val list = WorkspaceFileManager.readMap(path).entries.toMutableList()
        transformBooleanValuesToJsonArrayInMaps(list)
        return list
    }

    fun writeScriptConfig(packageName: String, name: String, description: String) {
        val path = WorkspaceFileManager.AppConf + "/" + packageName + ".txt"
        val map = WorkspaceFileManager.readMap(path)
        map[name] = arrayOf<Any?>(true, description, "v1.0")
        WorkspaceFileManager.writeMap(path, map)
        WorkspaceFileManager.ensureDirectoryExists(WorkspaceFileManager.DIR + "/" + WorkspaceFileManager.AppScript + "/" + packageName)
    }

    private fun transformBooleanValuesToJsonArrayInMaps(
        dataList: MutableList<MutableMap.MutableEntry<String, Any?>>
    ) {
        for (entry in dataList) {
            val value = entry.value
            if (value is Boolean) {
                try {
                    val newArray = JSONArray(arrayOf<Any?>(value, "", "v1.0"))
                    entry.setValue(newArray)
                } catch (_: JSONException) {
                } catch (_: Exception) {
                }
            }
        }
    }
}
