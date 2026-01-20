package com.kulipai.luahook.core.update

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class AppUpdater(private val context: Context) {

    private val okHttpClient = OkHttpClient()

    interface UpdateCallback {
        fun onStart()
        fun onSuccess(latestVersion: String, releasePageUrl: String, currentVersion: String)
        fun onLatest(currentVersion: String)
        fun onError(message: String)
    }

    fun checkUpdate(callback: UpdateCallback) {
        callback.onStart()

        val githubApiUrl = "https://api.github.com/repos/KuLiPai/LuaHook/releases/latest"
        val request = Request.Builder().url(githubApiUrl).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onError("HTTP error: ${response.code}")
                    return
                }

                try {
                    val responseBody = response.body?.string() ?: ""
                    val jsonObject = JSONObject(responseBody)
                    val latestVersion = jsonObject.getString("tag_name").removePrefix("v")
                    val releasePageUrl = jsonObject.getString("html_url")

                    val currentVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (e: Exception) {
                        "0.0.0"
                    }

                    if (compareVersions(latestVersion, currentVersion ?: "0.0.0") > 0) {
                        callback.onSuccess(latestVersion, releasePageUrl, currentVersion ?: "0.0.0")
                    } else {
                        callback.onLatest(currentVersion ?: "0.0.0")
                    }
                } catch (e: JSONException) {
                    callback.onError("Data parse error")
                } catch (e: Exception) {
                    callback.onError("Unknown error: ${e.message}")
                }
            }
        })
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }
}
