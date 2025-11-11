package io.dingyi222666.sora.lua.ai

import android.util.Log
import io.dingyi222666.sora.lua.ai.AutocompleteTemplate
import io.dingyi222666.sora.lua.http.postSSE
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import org.json.JSONArray
import org.json.JSONObject

class OpenAICodeCompleter(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : AICodeCompleter {
    private val template = AutocompleteTemplate.getTemplateForModel()

    override suspend fun singleFileCompletion(
        content: Content,
        position: CharPosition
    ): List<DiffPatch> {
        val (prefix, suffix) = getContextForCompletion(content, position)

        val prompt = template.template
            .replace(
                "{prefix}", prefix
                    .replace("\n", "<newline>")
                //   .replace(" ","<br>")
            )
            .replace("{suffix}", suffix.replace("\n", "<newline>")/*.replace(" ","<br>")*/)

        val messages = listOf(
            "system" to "You are a Lua code assistant. You need to read this code file \n<context>\n${
                content.toString()
                    .replace("\n", "<newline>")
                /* .replace(" ","<br>")*/
            }\n</context>\n to get completion",
            "user" to prompt
        )

        return try {
            sendRequest(messages)
                .let { completion ->

                    val jsonObject = JSONObject(completion)
                    listOf(
                        DiffPatch(
                            patches = listOf(
                                DiffPatch.Patch(
                                    jsonObject.optString("original_code"),
                                    jsonObject.optString("new_code")
                                ),
                            ),
                            displayText = jsonObject.optString("new_code")
                        )
                    )
                }


        } catch (e: Exception) {
            Log.e("OpenAICodeCompleter", "Error getting completions", e)
            emptyList()
        }
    }

    private fun getContextForCompletion(
        content: Content,
        position: CharPosition
    ): Pair<String, String> {
        val prefixLines = mutableListOf<String>()
        val suffixLines = mutableListOf<String>()

        // 获取当前行
        val currentLine = content.getLine(position.line).toString()
        val prefixContent = currentLine.substring(0, position.column)
        val suffixContent = currentLine.substring(position.column)

        // 计算当前缩进级别
        val currentIndent = currentLine.takeWhile { it.isWhitespace() }.length

        // 向上扫描获取相关上下文
        var lineIndex = position.line - 1
        var continuousContext = true
        var blockLevel = 0 // 跟踪代码块嵌套层级

        while (lineIndex >= 0 && continuousContext && prefixLines.size < 10) {
            val line = content.getLine(lineIndex).toString()
            val trimmedLine = line.trim()

            // 更新代码块层级
            blockLevel += when {
                trimmedLine.startsWith("end") -> 1
                trimmedLine.startsWith("function") ||
                        trimmedLine.startsWith("if") ||
                        trimmedLine.startsWith("for") ||
                        trimmedLine.startsWith("while") ||
                        trimmedLine.startsWith("do") -> -1

                else -> 0
            }

            // 检查是否应该包含这一行
            val indent = line.takeWhile { it.isWhitespace() }.length
            val shouldInclude = when {
                // 同一代码块内的行
                indent == currentIndent -> true
                // 外层代码块的起始行
                indent < currentIndent && (
                        trimmedLine.startsWith("function") ||
                                trimmedLine.startsWith("if") ||
                                trimmedLine.startsWith("for") ||
                                trimmedLine.startsWith("while") ||
                                trimmedLine.startsWith("do") ||
                                trimmedLine.startsWith("local") ||
                                trimmedLine.contains("=")
                        ) -> true
                // 内层代码块的行
                indent > currentIndent && blockLevel < 0 -> true
                // 其他情况不包含
                else -> false
            }

            if (shouldInclude) {
                prefixLines.add(0, line)
            } else if (prefixLines.isNotEmpty()) {
                // 如果已经有内容且遇到不相关行，停止扫描
                continuousContext = false
            }

            lineIndex--
        }

        // 向下扫描获取相关上下文
        lineIndex = position.line + 1
        continuousContext = true
        blockLevel = 0

        while (lineIndex < content.lineCount && continuousContext && suffixLines.size < 5) {
            val line = content.getLine(lineIndex).toString()
            val trimmedLine = line.trim()

            // 更新代码块层级
            blockLevel += when {
                trimmedLine.startsWith("function") ||
                        trimmedLine.startsWith("if") ||
                        trimmedLine.startsWith("for") ||
                        trimmedLine.startsWith("while") ||
                        trimmedLine.startsWith("do") -> 1

                trimmedLine.startsWith("end") -> -1
                else -> 0
            }

            // 检查是否应该包含这一行
            val indent = line.takeWhile { it.isWhitespace() }.length
            val shouldInclude = when {
                // 同一代码块内的行
                indent == currentIndent -> true
                // 当前代码块的结束行
                indent <= currentIndent && trimmedLine.startsWith("end") -> true
                // 内层代码块的行
                indent > currentIndent && blockLevel > 0 -> true
                // 其他情况不包含
                else -> false
            }

            if (shouldInclude) {
                suffixLines.add(line)
            } else if (suffixLines.isNotEmpty()) {
                // 如果已经有内容且遇到不相关行，停止扫描
                continuousContext = false
            }

            lineIndex++
        }

        // 构建最终的上下文
        return buildString {
            prefixLines.forEach { appendLine(it) }
            append(prefixContent)
        }.replace("\n", "<newline>") to buildString {
            append(suffixContent)
            suffixLines.forEach { appendLine(it) }
        }.replace("\n", "<newline>")
    }

    // role, content
    private suspend fun sendRequest(messages: List<Pair<String, String>>): String {
        println(messages)
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { (role, content) ->
                    put(JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }
            })
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "edit_code")
                        put(
                            "description",
                            "Replaces a block of code with a new version to complete it."
                        )
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("original_code", JSONObject().apply {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "The original code to replace. For this task, this should be '{FILL_CODE_HERE}'."
                                    )
                                })
                                put("new_code", JSONObject().apply {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "The new code you generated to insert as the completion."
                                    )
                                })
                            })
                            put("required", JSONArray().apply {
                                put("original_code")
                                put("new_code")
                            })
                        })
                    })
                })
            })

            put("temperature", 0)
            put("thinking", JSONObject().apply {
                put("type", "disabled")
            })
            put("n", 1)
            put("stream", true) // 启用 SSE
        }

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )

        val responseBuilder = StringBuilder()
        postSSE("$baseUrl/chat/completions", requestBody, headers)
            .collect { event ->
                if (event.data == "[DONE]") {
                    return@collect
                }

                if (event.data.isNotBlank()) {
                    try {
                        val delta = JSONObject(event.data)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("delta")

                        println(delta.toString())

                        delta.optJSONArray("tool_calls")?.let { toolCalls ->
                            val toolCallChunk = toolCalls.getJSONObject(0)
                            toolCallChunk.optJSONObject("function")?.let { functionChunk ->
                                functionChunk.optString("arguments")?.let { argumentsChunk ->
                                    responseBuilder.append(argumentsChunk)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OpenAICodeCompleter", "Error parsing SSE tool call chunk", e)
                    }
                }
            }

        return responseBuilder.toString()
        //    return post("$baseUrl/chat/completions", requestBody, headers)
    }
}

