package com.kulipai.luahook


import android.app.Application
import com.google.android.material.color.DynamicColors
import com.kulipai.luahook.util.DataRepository.shellInit
import com.kulipai.luahook.util.LanguageUtil
import com.kulipai.luahook.util.XposedScope


class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        LanguageUtil.applyLanguage(this)
        // 预加载 shell，确保 MainActivity 能及时拿到状态
        shellInit(applicationContext)
        XposedScope.init()
    }
}