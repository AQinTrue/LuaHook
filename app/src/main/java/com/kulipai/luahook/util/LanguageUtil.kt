package com.kulipai.luahook.util

import android.content.Context
import android.content.res.Configuration
import java.util.*
import androidx.core.content.edit

/**
 * 语言工具类
 */

object LanguageUtil {

    private const val KEY_LANGUAGE = "key_language"
    const val LANGUAGE_CHINESE = "zh"
    const val LANGUAGE_CHINESE_TRADITIONAL  = "zh-TW"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_DEFAULT = "zh" // 表示默认语言

    // 设置语言
    fun changeLanguage(context: Context, language: String) {
        val locale = when (language) {
            LANGUAGE_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_CHINESE_TRADITIONAL -> Locale.TRADITIONAL_CHINESE
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            else -> Locale.getDefault()
        }

        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)

        val appContext = context.applicationContext
        val appResources = appContext.resources
        val appConfig = Configuration(appResources.configuration)
        appConfig.setLocale(locale)
        appResources.updateConfiguration(appConfig, appResources.displayMetrics)

        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit { putString(KEY_LANGUAGE, language) }
    }

    // 切换到默认语言
    fun resetToDefaultLanguage(context: Context) {
        // 清空保存的语言设置
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit() { remove(KEY_LANGUAGE) }

        // 应用默认语言
        val configuration = context.resources.configuration
        configuration.setLocale(Locale.getDefault())
        context.createConfigurationContext(configuration)
    }

    // 应用语言设置
    fun applyLanguage(context: Context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val language = preferences.getString(KEY_LANGUAGE, LANGUAGE_DEFAULT) ?: LANGUAGE_DEFAULT
        changeLanguage(context, language)
    }

    // 获取当前语言
    fun getCurrentLanguage(context: Context): String {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return preferences.getString(KEY_LANGUAGE, LANGUAGE_DEFAULT) ?: LANGUAGE_DEFAULT
    }
}