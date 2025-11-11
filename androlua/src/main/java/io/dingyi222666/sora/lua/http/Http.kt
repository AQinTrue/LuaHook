package io.dingyi222666.sora.lua.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

internal suspend fun sendRequest(
    url: String,
    method: String = "GET",
    body: Any? = null,
    headers: Map<String, String> = emptyMap(),
    isSse: Boolean = false
): BufferedReader {
    return withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method

        if (isSse) {
            connection.setRequestProperty("Accept", "text/event-stream")
        }

        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        if (method == "POST" && body != null) {
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                if (body is JSONObject) "application/json" else "text/plain"
            )
            connection.outputStream.use { os ->
                val input = when (body) {
                    is JSONObject -> body.toString().toByteArray(Charsets.UTF_8)
                    is String -> body.toByteArray(Charsets.UTF_8)
                    else -> throw IllegalArgumentException("Unsupported body type: ${body::class.java}")
                }
                os.write(input, 0, input.size)
            }
        }
        connection.inputStream.bufferedReader()
    }
}

suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = sendRequest(url, "GET", null, headers).readText()

suspend fun post(url: String, body: Any, headers: Map<String, String> = emptyMap()): String = sendRequest(url, "POST", body, headers).readText()

suspend fun postSSE(url: String, body: Any, headers: Map<String, String> = emptyMap()): Flow<SseEvent> = parseSse(sendRequest(url, "POST", body, headers, true))

fun parseSse(reader: BufferedReader): Flow<SseEvent> = flow {
    val eventBuilder = SseEvent.Builder()
    var line = ""

    while (reader.readLine().also { line = it } != null) {
        when {
            line.startsWith("data:") -> {
                eventBuilder.appendData(line.substring(5).trim())
            }

            line.startsWith("event:") -> {
                eventBuilder.event = line.substring(6).trim()
            }

            line.startsWith("id:") -> {
                eventBuilder.id = line.substring(3).trim()
            }

            line.isBlank() -> {
                emit(eventBuilder.build())
                eventBuilder.reset()
            }

            else -> {
                //  可以在这里添加对无效格式的处理，例如记录日志或抛出异常
                //  println("Ignoring invalid SSE line: $line")
            }
        }
    }
}
    .flowOn(Dispatchers.IO)

data class SseEvent(val id: String, val event: String, val data: String) {
    class Builder {
        var id: String = ""
        var event: String = ""
        private val dataBuffer = StringBuilder()

        fun appendData(data: String) {
            if (dataBuffer.isNotEmpty()) {
                dataBuffer.append("\n")
            }
            dataBuffer.append(data)
        }

        fun build(): SseEvent = SseEvent(id, event, dataBuffer.toString())

        fun reset() {
            id = ""
            event = ""
            dataBuffer.clear()
        }
    }
}