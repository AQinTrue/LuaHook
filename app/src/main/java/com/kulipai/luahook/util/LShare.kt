package com.kulipai.luahook.util

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File

/**
 * /data/local/tmp/LuaHook/文件管理系统
 */

object LShare {

    const val DIR: String = "/data/local/tmp/LuaHook"
    const val Project: String = "/Project"
    const val AppConf: String = "/AppConf"
    const val AppScript: String = "/AppScript"
    const val Plugin: String = "/Plugin"


    var isok: Boolean = false

    fun read(path: String): String {
        val path = DIR + path
        if (File(path).exists()) {
            return File(path).readText()
        }
        return ""
    }


    fun write(file: String, content: String): Boolean {
        val path = "$DIR/$file"

        // 将内容编码为 Base64，然后解码写入文件
        val base64Content = Base64.encodeToString(
            content.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        ) // NO_WRAP 避免换行

        val script = """
        rm -f "$path"
        touch "$path"
        echo "$base64Content" | base64 -d > "$path"
    """.trimIndent()

        val (_, success) = ShellManager.shell(script)
        return success
    }


    /**
     * 保存任意 Kotlin Map 到文件，作为 JSON 对象。
     *
     * @param file 文件名
     * @param data 要保存的 Map (键为 String，值为 Any)
     * @return 是否保存成功
     */
    fun writeMap(file: String, data: MutableMap<String, Any?>): Boolean {
        val jsonObject = JSONObject(data) // JSONObject 可以直接从 Map<String, Any> 构建
        val jsonString = jsonObject.toString()
        return write(file, jsonString)
    }

    /**
     * 从文件读取 JSON 字符串并解析为 Map<String, Any?>。
     *
     * @param file 文件名
     * @return 读取到的 Map，如果失败则返回 null
     */
    fun readMap(file: String): MutableMap<String, Any?> {
        val jsonString = read(file)
        if (jsonString.isEmpty()) {
            return mutableMapOf()
        }
        return try {
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, Any?>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.get(key)
            }
            map
        } catch (e: Exception) {
            ("Error decoding arbitrary object from $file: ${e.message}").d()
            mutableMapOf()
        }
    }

    fun writeStringList(path: String, list: List<String>) {
        write(path, list.joinToString(","))
    }


    fun readStringList(path: String): MutableList<String> {
        val serialized = read(path)
        return if (serialized != "") {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }

    fun writeTmp(packageName: String, content: String): Boolean {
        var tmpPath = "/tmp"

        if (isok && ensureDirectoryExists(DIR + tmpPath)) {
            return write("$tmpPath/$packageName.lua", content)
        }
        return false
    }


    fun rm(file: String): Boolean {
        val path = "$DIR/$file"
        val script = "rm -f $path"
        val (_, success) = ShellManager.shell(script)
        return success
    }

    fun init(context: Context) {
        ensureDirectoryExists(DIR)
        ensureDirectoryExists(DIR + Project)
        ensureDirectoryExists(DIR + AppConf)
        ensureDirectoryExists(DIR + AppScript)
        ensureDirectoryExists(DIR + Plugin)
        isok = true
    }


//    private fun md5(str: String): String {
//
//        val bytes = MessageDigest.getInstance("MD5").digest(str.toByteArray())
//        return bytes.joinToString("") { "%02x".format(it) }
//    }

    fun DirectoryExists(path: String): Boolean {
        // 使用 ls 判断目录是否存在
        val (_, checkSuccess) = ShellManager.shell("ls \"$path\"")
        return checkSuccess
        // 目录存在
    }

    /**
     * 完整路径
     * @param path 路径
     * @return 如果创建成功就True否则false
     */
    fun ensureDirectoryExists(path: String): Boolean {

        if (DirectoryExists(path)) {
            return true
        }


        // 不存在则尝试创建
        val (_, mkdirSuccess) = ShellManager.shell("mkdir -p \"$path\"")
        return mkdirSuccess
    }


    // 定义一个数据类来存储解析后的参数
    data class FileParameters(
        val name: String? = null,
        val descript: String? = null,
        val packageName: String? = null, // 注意：'package' 是 Kotlin 的关键字，用 packageName
        val author: String? = null,
        val otherParams: Map<String, String> = emptyMap() // 用于存储其他未定义的参数
    )

    /**
     * 从文件内容的开头解析标准参数。
     * 参数格式：-- key: value
     *
     * @param fileContent 文件的完整内容字符串。
     * @return 包含解析后参数的 FileParameters 对象。
     */
    fun parseParameters(fileContent: String): FileParameters? {
        try {
            val lines = fileContent.split("\n") // 将文件内容按行分割
            val parsedParams = mutableMapOf<String, String>()

            // 遍历文件开头的行，直到遇到不符合参数格式的行
            for (line in lines) {
                val trimmedLine = line.trim() // 去除行首尾空格

                // 检查是否是参数行
                if (trimmedLine.startsWith("--")) {
                    // 移除 "--" 前缀
                    val content = trimmedLine.substring(2).trim()

                    // 查找第一个冒号的位置
                    val colonIndex = content.indexOf(":")

                    if (colonIndex != -1) {
                        val key = content.substring(0, colonIndex).trim()
                        val value = content.substring(colonIndex + 1).trim()
                        parsedParams[key] = value
                    } else {
                        // 如果一行以 -- 开头但没有冒号，则停止解析参数
                        break
                    }
                } else if (trimmedLine.isEmpty()) {
                    // 忽略空行
                    continue
                } else {
                    // 遇到不以 "--" 开头且非空的行，表示参数部分结束
                    break
                }
            }

            return FileParameters(
                name = parsedParams["name"],
                descript = parsedParams["descript"],
                packageName = parsedParams["package"], // 对应文件中的 "package"
                author = parsedParams["author"],
                otherParams = parsedParams.filterKeys {
                    it != "name" && it != "descript" && it != "package" && it != "author"
                }
            )
        } catch (_: Exception) {

        }
        return null
    }


}