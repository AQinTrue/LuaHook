package com.kulipai.luahook.activity


import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.kulipai.luahook.library.LuaActivity
import com.kulipai.luahook.library.LuaImport
import com.kulipai.luahook.library.LuaUtil
import com.kulipai.luahook.util.e
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.ZeroArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import java.io.File

class ScriptSetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val path = intent.getStringExtra("path").toString()
        val script = read(path)
        val func = extractLuaFunctionByLabel(script, "set")
        func?.let {
            val funcLine = func.functionStartLine
            val callfunc = getFunctionName(func.functionCode)
            try {
                createGlobals().load("${func.functionCode}\n$callfunc()").call()
            } catch (e: Exception) {
                val err = simplifyLuaError(e.toString(), funcLine)
                val errText = TextView(this)
                errText.text = err
                errText.gravity = Gravity.CENTER
                setContentView(errText)
                "${path.substringAfterLast("/")}:@set@:$err".e()
            }
        }


    }

    fun read(path: String): String {
        if (File(path).exists()) {
            return File(path).readText()
        }
        return ""
    }

    fun getFunctionName(functionString: String): String? {
        // 正则表达式匹配 'function ' 后面跟着的函数名
        // 函数名由字母、数字和下划线组成，且不能以数字开头
        val functionNamePattern = Regex("function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(.*\\)")

        val matchResult = functionNamePattern.find(functionString)

        // 返回第一个捕获组，即函数名
        return matchResult?.groups?.get(1)?.value
    }

    /**
     * 函数提取结果
     */
    data class FunctionExtractionResult(
        val functionCode: String,
        val functionStartLine: Int // 函数头在原脚本中的行号（从1开始）
    )

    /**
     * 提取Lua代码中带标签的函数
     * 支持的标签格式：::label、@label、::label::、@label@、::label@、@label::
     */
    fun extractLuaFunctionByLabel(luaCode: String, label: String): FunctionExtractionResult? {
        val lines = luaCode.split('\n')
        val labelPattern = createLabelPattern(label)

        // 查找标签位置
        var labelLineIndex = -1
        for (i in lines.indices) {
            if (labelPattern.matches(lines[i].trim())) {
                labelLineIndex = i
                break
            }
        }

        if (labelLineIndex == -1) {
            return null // 找不到标签
        }

        // 从标签后开始查找函数
        var functionStartLine = -1

        for (i in (labelLineIndex + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isNotEmpty()) {
                val functionMatch = Regex("""^\s*function\s+\w+\s*\(.*?\)\s*$""").find(lines[i])
                if (functionMatch != null) {
                    functionStartLine = i
                    break
                }
            }
        }

        if (functionStartLine == -1) {
            return null // 找不到函数
        }

        // 提取完整函数
        val functionCode = extractCompleteFunction(lines, functionStartLine)

        return FunctionExtractionResult(
            functionCode = functionCode,
            functionStartLine = functionStartLine + 1 // 转换为从1开始的行号
        )
    }

    /**
     * 创建标签匹配模式
     */
    private fun createLabelPattern(label: String): Regex {
        val escapedLabel = Regex.escape(label)
        // 匹配 ::label、@label、::label::、@label@、::label@、@label::
        return Regex("^\\s*[:@]{1,2}$escapedLabel[:@]{0,2}\\s*$")
    }

    /**
     * 提取完整的函数，处理嵌套结构
     */
    private fun extractCompleteFunction(lines: List<String>, startLine: Int): String {
        val result = mutableListOf<String>()
        var depth = 0
        var i = startLine

        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()

            result.add(line)

            // 计算深度变化
            depth += countOpeningKeywords(trimmedLine)
            depth -= countClosingKeywords(trimmedLine)

            // 如果是第一行（function声明），深度应该是1
            if (i == startLine) {
                depth = 1
            }

            // 如果深度回到0，说明函数结束
            if (depth == 0) {
                break
            }

            i++
        }

        return result.joinToString("\n")
    }

    /**
     * 计算开启关键词的数量
     */
    private fun countOpeningKeywords(line: String): Int {
        var count = 0
        val keywords = listOf("function", "if", "for", "while", "repeat", "do")

        // 移除字符串和注释
        val cleanLine = removeStringsAndComments(line)

        for (keyword in keywords) {
            // 使用单词边界匹配，避免匹配到变量名中的关键词
            val pattern = Regex("\\b$keyword\\b")
            count += pattern.findAll(cleanLine).count()
        }

        return count
    }

    /**
     * 计算关闭关键词的数量
     */
    private fun countClosingKeywords(line: String): Int {
        var count = 0
        val keywords = listOf("end", "until")

        // 移除字符串和注释
        val cleanLine = removeStringsAndComments(line)

        for (keyword in keywords) {
            // 使用单词边界匹配
            val pattern = Regex("\\b$keyword\\b")
            count += pattern.findAll(cleanLine).count()
        }

        return count
    }

    /**
     * 移除字符串和注释，避免在字符串中的关键词被误计算
     */
    private fun removeStringsAndComments(line: String): String {
        var result = line

        // 移除单行注释
        val commentIndex = result.indexOf("--")
        if (commentIndex != -1) {
            result = result.substring(0, commentIndex)
        }

        // 移除字符串（简化处理，只处理双引号字符串）
        result = result.replace(Regex("\"[^\"]*\""), "")
        result = result.replace(Regex("'[^']*'"), "")

        return result
    }


    fun createGlobals(): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["enableEdgeToEdge"] = CoerceJavaToLua.coerce(object : ZeroArgFunction() {
            override fun call(): LuaValue? {
                enableEdgeToEdge()
                return NIL
            }
        })
        globals["activity"] = CoerceJavaToLua.coerce(this)
        LuaActivity(this).registerTo(globals)
        LuaUtil.loadBasicLib(globals)
        LuaImport(
            this::class.java.classLoader!!,
            this::class.java.classLoader!!
        ).registerTo(globals, packageName)
        LuaUtil.shell(globals)
        return globals
    }


    fun simplifyLuaError(raw: String, funcLine: Int): String {
        val lines = raw.lines()

        // 1. 优先提取第一条真正的错误信息（不是 traceback）
        val primaryErrorLine = lines.firstOrNull { it.trim().matches(Regex(""".*:\d+ .+""")) }

        if (primaryErrorLine != null) {
            val match = Regex(""".*:(\d+) (.+)""").find(primaryErrorLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line ${lineNum.toInt() + funcLine - 1}: $msg"
            }
        }

        // 2. 其次从 traceback 提取（防止所有匹配失败）
        val fallbackLine = lines.find { it.trim().matches(Regex(""".*:\d+: .*""")) }
        if (fallbackLine != null) {
            val match = Regex(""".*:(\d+): (.+)""").find(fallbackLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line ${lineNum.toInt() + funcLine - 1}: $msg"
            }
        }

        return raw.lines().firstOrNull()?.take(100) ?: "未知错误"
    }

}