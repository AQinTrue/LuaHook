package com.kulipai.luahook.ui.error

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.kulipai.luahook.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ErrorActivity 是一个专门用于显示应用程序错误信息的 activity。
 * 它从 Intent 中接收错误消息和堆栈跟踪，并将其显示在 UI 上。
 */
class ErrorActivity : AppCompatActivity() {

    companion object {
        // 用于 Intent 传递错误信息的键
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_STACK_TRACE = "stack_trace"
        private const val LOG_FILE_PREFIX = "error_log_"
        private const val LOG_FILE_EXTENSION = ".txt"
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss"
    }

    private lateinit var tvErrorMessage: TextView
    private lateinit var errorMessage: String
    private lateinit var stackTrace: String

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_error) // 确保您有 activity_error.xml 布局文件


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        val btnRestartApp: MaterialCardView = findViewById(R.id.btnRestartApp)
        val closeApp: MaterialCardView = findViewById(R.id.closeApp)
        val sendErrMsg: MaterialCardView = findViewById(R.id.sendErrMsg)

        // 从 Intent 中获取错误信息
        errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "未知错误"
        stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "无堆栈跟踪信息"

        // 显示错误信息
        tvErrorMessage.text = errorMessage

        sendErrMsg.setOnClickListener {
            shareErrorLog()
        }

        closeApp.setOnClickListener {
            finish()
        }

        // 设置重启应用程序按钮的点击监听器
        btnRestartApp.setOnClickListener {
            // 重启应用程序到主 activity
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish() // 关闭当前错误 activity
        }
    }

    /**
     * 将错误信息和堆栈跟踪保存到文件并分享。
     */
    // TODO)) 封装一下
    private fun shareErrorLog() {
        // 创建日志文件的内容
        val logContent = """
            错误信息：
            $errorMessage

            堆栈跟踪：
            $stackTrace

            ---
            设备信息：
            制造商：${Build.MANUFACTURER}
            型号：${Build.MODEL}
            Android 版本：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            应用版本：${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "未知" }}
            包名：$packageName
            ---
            生成时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
        """.trimIndent()

        // 保存日志到文件
        val logFile = saveLogToFile(logContent)

        if (logFile != null) {
            val fileUri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider", // 对应 AndroidManifest.xml 中 <provider> 的 authorities
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain" // 设置MIME类型为纯文本
                putExtra(
                    Intent.EXTRA_SUBJECT, "应用错误日志 - ${
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(Date())}")
                putExtra(Intent.EXTRA_TEXT, "请查看附件中的错误日志。") // 邮件或消息正文
                putExtra(Intent.EXTRA_STREAM, fileUri) // 附加文件URI
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 授予读取URI权限给接收应用
            }

            // 检查是否有应用可以处理此 Intent
            if (shareIntent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(shareIntent, "分享错误日志"))
            } else {
                Toast.makeText(this, "没有找到可以分享的应用程序", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "保存日志文件失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将字符串内容保存到应用缓存目录下的日志文件。
     * @param content 要保存的文本内容。
     * @return 保存的 File 对象，如果失败则返回 null。
     */
    // TODO)) 封装一下
    private fun saveLogToFile(content: String): File? {
        val logDir = File(cacheDir, "logs") // 在应用缓存目录中创建 'logs' 文件夹
        if (!logDir.exists()) {
            logDir.mkdirs() // 如果文件夹不存在，则创建它
        }

        val timestamp = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        val fileName = "$LOG_FILE_PREFIX$timestamp$LOG_FILE_EXTENSION"
        val logFile = File(logDir, fileName)

        try {
            FileOutputStream(logFile).use { fos ->
                fos.write(content.toByteArray())
            }
//            Toast.makeText(this, "日志已保存到: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
            return logFile
        } catch (e: IOException) {
//            e.printStackTrace()
            Toast.makeText(this, "保存日志文件时发生错误: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }
}