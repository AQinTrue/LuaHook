package com.kulipai.luahook.activity

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.kulipai.luahook.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

class AboutActivity : AppCompatActivity() {
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val appBar: AppBarLayout by lazy { findViewById(R.id.app_bar) }
    private val cardDonate: MaterialCardView by lazy { findViewById(R.id.card_donate) }
    private val appLogo: ImageView by lazy { findViewById(R.id.app_logo) }
//    开源协议部分
//    private val cardLicense: MaterialCardView by lazy { findViewById(R.id.card_license) }

    private val github_card: MaterialCardView by lazy { findViewById(R.id.github_card) }
    private val developerKuliPaiRow: MaterialCardView by lazy { findViewById(R.id.developer_kuli_pai_row) }
    private val padi: MaterialCardView by lazy { findViewById(R.id.padi) }
    private val eleven: MaterialCardView by lazy { findViewById(R.id.eleven) }
    private val carrot: MaterialCardView by lazy { findViewById(R.id.carrot) }
    private val developerAnotherRow: MaterialCardView by lazy { findViewById(R.id.developer_another_row) }

    private val cardCheckUpdate: MaterialCardView by lazy { findViewById(R.id.card_check_update) }
    private val tvUpdateStatus: MaterialTextView by lazy { findViewById(R.id.tv_update_status) }

    // 获取 NestedScrollView 内部的内容布局引用
    private val contentLayout: LinearLayout by lazy { findViewById(R.id.content_layout) }

    private val mainLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }

    // 定义展开和折叠状态下的颜色
    private var expandedToolbarColor: Int = 0
    private var collapsedToolbarColor: Int = 0

    // 用于颜色插值器
    private val argbEvaluator = ArgbEvaluator()

    // OkHttp 客户端实例
    private val okHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        enableEdgeToEdge()

        // 获取展开和折叠状态的颜色
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        expandedToolbarColor = ContextCompat.getColor(this, typedValue.resourceId)

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        collapsedToolbarColor = ContextCompat.getColor(this, typedValue.resourceId)

        // 设置返回点击监听器
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // --- 设置 Toolbar 标题字符串 ---
        toolbar.title = getString(R.string.about)
        toolbar.setTitleTextColor(expandedToolbarColor)
        toolbar.navigationIcon?.let {
            DrawableCompat.setTint(it.mutate(), expandedToolbarColor)
        }

        // --- 开发者卡片点击事件 ---
        val LuaHookGithubUrl = "https://github.com/KuLiPai/LuaHook"
        val kuliPaiGithubUrl = "https://github.com/KuLiPai"
        val anotherDeveloperGithubUrl = "https://github.com/Samzhaohx"
        val padiGithub = "https://github.com/paditianxiu"
        val elevenGithub = "https://github.com/imconfident11"
        val carrotGithub = "https://github.com/TrialCarrot"
        github_card.setOnClickListener { openGithubUrl(LuaHookGithubUrl) }
        developerKuliPaiRow.setOnClickListener { openGithubUrl(kuliPaiGithubUrl) }
        developerAnotherRow.setOnClickListener { openGithubUrl(anotherDeveloperGithubUrl) }
        padi.setOnClickListener { openGithubUrl(padiGithub) }
        eleven.setOnClickListener { openGithubUrl(elevenGithub) }
        carrot.setOnClickListener { openGithubUrl(carrotGithub) }

        // --- 许可协议卡片功能 ---
//        cardLicense.setOnClickListener {
//            // TODO: 实现查看许可协议详情的逻辑，例如打开一个 WebView 或新的 activity 显示许可文本
//            Toast.makeText(this, "许可协议卡片被点击了！请实现查看详情逻辑。", Toast.LENGTH_SHORT).show()
//        }

        // --- 检查更新卡片点击事件 ---
        cardCheckUpdate.setOnClickListener { checkUpdate() }

        // --- 赞赏卡片点击事件 ---
        cardDonate.setOnClickListener { showDonatePopup(it) }

        // Insets 处理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            appBar.setPadding(0, systemBars.top, 0, 0)

            val contentPaddingTop = contentLayout.paddingTop
            contentLayout.setPadding(
                systemBars.left,
                contentPaddingTop,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // 用于控制 App Logo 透明度 和 Toolbar 内容颜色渐变
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val currentScroll = abs(verticalOffset)

            val scrollRatio =
                if (totalScrollRange == 0) 0f else currentScroll.toFloat() / totalScrollRange.toFloat()

            // --- 控制 App Logo 的透明度 ---
            val appLogoAlpha = (1f - scrollRatio).coerceIn(0f, 1f)
            appLogo.alpha = appLogoAlpha

            if (appLogoAlpha == 0f && appLogo.isVisible) {
                appLogo.visibility = View.INVISIBLE
            } else if (appLogoAlpha > 0f && appLogo.isInvisible) {
                appLogo.visibility = View.VISIBLE
            }
            if (verticalOffset == 0) {
                appLogo.visibility = View.VISIBLE
            }

            // --- 控制 Toolbar 内容颜色渐变 ---
            val colorFadeStartRatio = 0.5f
            val colorFadeEndRatio = 0.8f

            val colorInterpolationRatio = if (scrollRatio <= colorFadeStartRatio) {
                0f
            } else if (scrollRatio >= colorFadeEndRatio) {
                1f
            } else {
                (scrollRatio - colorFadeStartRatio) / (colorFadeEndRatio - colorFadeStartRatio)
            }

            val interpolatedColor = argbEvaluator.evaluate(
                colorInterpolationRatio,
                expandedToolbarColor,
                collapsedToolbarColor
            ) as Int

            toolbar.setTitleTextColor(interpolatedColor)
            toolbar.navigationIcon?.let {
                DrawableCompat.setTint(it.mutate(), interpolatedColor)
            }
        }

        // TODO: 实现动态加载应用名称和版本号
        // 获取并显示当前版本号（如果需要显示在UI上）
        try {
            packageManager.getPackageInfo(packageName, 0).versionName // 使用 0 作为 flags
            // 可以考虑将版本号显示在某个 TextView 中
            // val appVersionTextView: MaterialTextView? = findViewById(R.id.app_version)
            // appVersionTextView?.text = "V${currentVersion}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            // 处理错误
        }
    }

    // --- 打开 GitHub URL 函数 ---
    private fun openGithubUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, resources.getString(R.string.cant_open_links), Toast.LENGTH_SHORT).show()
        }
    }

    // --- 检查更新函数 (使用 OkHttp 获取 GitHub Release) ---
    private fun checkUpdate() {
        // 立即更新状态文本
        tvUpdateStatus.text = resources.getString(R.string.checking)

        // 替换为你的 GitHub 仓库信息
        "KuLiPai" // GitHub 用户名或组织名
        "LuaHook"   // GitHub 仓库名

        // GitHub API 获取最新 Release 的 URL
        val githubApiUrl = "https://api.github.com/repos/KuLiPai/LuaHook/releases/latest"

        val request = Request.Builder()
            .url(githubApiUrl)
            .build()

        // 使用 OkHttp 进行异步网络请求
        okHttpClient.newCall(request).enqueue(object : Callback {
            @SuppressLint("SetTextI18n")
            override fun onFailure(call: Call, e: IOException) {
                // 网络请求失败，切换到 UI 线程更新 UI
                runOnUiThread {
                    // 检查 activity 是否仍然有效
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    tvUpdateStatus.text = resources.getString(R.string.check_failed)+"${e.message}" // 显示错误信息
                    Toast.makeText(
                        this@AboutActivity,
                        resources.getString(R.string.check_update_false),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            @SuppressLint("SetTextI18n")
            override fun onResponse(call: Call, response: Response) {
                // 网络请求成功，切换到 UI 线程更新 UI
                runOnUiThread {
                    // 检查 activity 是否仍然有效
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body.string()
                            // 解析 JSON 响应
                            val jsonObject = JSONObject(responseBody)
                            val latestVersion = jsonObject.getString("tag_name")
                                .removePrefix("v") // 获取 tag_name，并移除前缀 'v' (如果存在)
                            val releasePageUrl =
                                jsonObject.getString("html_url") // 获取 Release 页面的 URL

                            // 获取当前应用版本号
                            val currentVersion = try {
                                packageManager.getPackageInfo(
                                    packageName,
                                    0
                                ).versionName // 使用 0 作为 flags
                            } catch (e: PackageManager.NameNotFoundException) {
                                e.printStackTrace()
                                "0.0.0" // 获取失败时设为默认值
                            }

                            // 比较版本号
                            if (compareVersions(latestVersion, currentVersion ?: "0.0.0") > 0) {
                                // 有新版本
                                tvUpdateStatus.text = resources.getString(R.string.new_version) +"$latestVersion"
                                AlertDialog.Builder(this@AboutActivity)
                                    .setTitle(resources.getString(R.string.find_new_version))
                                    .setMessage(resources.getString(R.string.current_version)+"$currentVersion"+resources.getString(R.string.n_latest_version)+"$latestVersion"+resources.getString(R.string.if_goto_github))
                                    .setPositiveButton(resources.getString(R.string.goto_github_release)) { dialog, _ ->
                                        // 打开 Release 页面链接
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            releasePageUrl.toUri()
                                        )
                                        if (intent.resolveActivity(packageManager) != null) {
                                            startActivity(intent)
                                        } else {
                                            Toast.makeText(
                                                this@AboutActivity,
                                                 resources.getString(R.string.cant_open_link_goto)+"${releasePageUrl}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        dialog.dismiss()
                                    }
                                    .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                                    .show()
                            } else {
                                // 已是最新版本
                                tvUpdateStatus.text = resources.getString(R.string.latest_version)
                                Toast.makeText(
                                    this@AboutActivity,
                                    resources.getString(R.string.latest_version),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: JSONException) {
                            // JSON 解析错误
                            tvUpdateStatus.text = resources.getString(R.string.check_failed_data_parse_error)
                            Toast.makeText(
                                this@AboutActivity,
                                resources.getString(R.string.check_update_failed_data_format),
                                Toast.LENGTH_SHORT
                            ).show()
                            e.printStackTrace()
                        } catch (e: Exception) {
                            // 其他可能的异常
                            tvUpdateStatus.text = resources.getString(R.string.check_failed)+"${e.message}"
                            Toast.makeText(
                                this@AboutActivity,
                                resources.getString(R.string.check_update_unknown_error),
                                Toast.LENGTH_SHORT
                            ).show()
                            e.printStackTrace()
                        } finally {
                            // OkHttp 的 enqueue 异步回调会自动关闭响应体，通常不需要手动调用 response.body?.close()
                        }
                    } else {
                        // HTTP 状态码不是 2xx
                        tvUpdateStatus.text = resources.getString(R.string.check_failed)+"${response.code}"
                        Toast.makeText(
                            this@AboutActivity,
                            resources.getString(R.string.check_update_failed_http)+"${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }


    // --- 版本号比较辅助函数 ---
    // 返回值：大于 0 表示 version1 更新，小于 0 表示 version2 更新，等于 0 表示相同
    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 } // 如果索引越界，取 0
            val p2 = parts2.getOrElse(i) { 0 } // 如果索引越界，取 0
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0 // 版本号完全相同
    }


    // --- 赞赏弹窗逻辑 ---
    private fun showDonatePopup(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.qrcode, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true // 可点击外部关闭
        ).apply {
            isOutsideTouchable = true
            animationStyle = R.style.PopupAnimation // 假设你有一个名为 PopupAnimation 的 Style
            elevation = 16f

            // 应用高斯模糊（如果支持且需要）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val renderEffect = RenderEffect.createBlurEffect(
                        50f, 50f, Shader.TileMode.CLAMP
                    )
                    // 确保 mainLayout 不为 null
                    mainLayout.setRenderEffect(renderEffect)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 处理不支持 RenderEffect 的情况，或者模糊值过大的情况
                }
            }

            // 弹窗关闭时的监听器
            setOnDismissListener {
                // 移除高斯模糊
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 确保 mainLayout 不为 null
                    mainLayout.setRenderEffect(null)
                }
            }
        }

        // 点击弹窗视图本身关闭弹窗
        popupView.setOnClickListener { popupWindow.dismiss() }

        // 显示弹窗
        // 确保 mainLayout 不为 null
        if (mainLayout != null) {
            popupWindow.showAtLocation(mainLayout, Gravity.CENTER, 0, 0)
        } else {
            // 如果 mainLayout 为 null，弹窗可能无法正确显示，可以考虑使用其他 View 作为 anchor
            popupWindow.showAtLocation(anchor.rootView, Gravity.CENTER, 0, 0)
        }
    }

}