package com.kulipai.luahook.ui.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kulipai.luahook.R
import com.kulipai.luahook.core.base.BaseActivity
import com.kulipai.luahook.core.language.LanguageUtils
import com.kulipai.luahook.databinding.ActivitySettingsBinding
import com.kulipai.luahook.ui.about.AboutActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivitySettingsBinding {
        return ActivitySettingsBinding.inflate(inflater)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun initEvent() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.language.setOnClickListener {
            showLanguagePickerDialog(this)
        }

        binding.about.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("RestrictedApi")
    fun showLanguagePickerDialog(context: Context) {
        val languages = arrayOf(
            resources.getString(R.string.follow_system),
            "Deutsch",
            "English",
            "Español",
            "Français",
            "हिन्दी",
            "日本語",
            "繁體中文",
            "简体中文",
            "한국어",
            "Português"
        )
        val languageCodes = arrayOf(
            LanguageUtils.LANGUAGE_FOLLOW_SYSTEM,
            LanguageUtils.LANGUAGE_GERMAN,
            LanguageUtils.LANGUAGE_ENGLISH,
            LanguageUtils.LANGUAGE_SPANISH,
            LanguageUtils.LANGUAGE_FRENCH,
            LanguageUtils.LANGUAGE_HINDI,
            LanguageUtils.LANGUAGE_JAPANESE,
            LanguageUtils.LANGUAGE_CHINESE_TRADITIONAL,
            LanguageUtils.LANGUAGE_CHINESE_SIMPLIFIED,
            LanguageUtils.LANGUAGE_KOREAN,
            LanguageUtils.LANGUAGE_PORTUGUESE
        )
        
        var currentLanguage = LanguageUtils.getCurrentLanguage(context)
        // Handle legacy "zh" if present in shared prefs
        if (currentLanguage == "zh") currentLanguage = LanguageUtils.LANGUAGE_CHINESE_SIMPLIFIED
        
        var checkedItem = languageCodes.indexOf(currentLanguage)
        // If not found (e.g. system default changed effectively or legacy code issue), default to first item (System) or English logic? 
        // Actually if currentLanguage is "system", it matches index 0.
        if (checkedItem == -1) checkedItem = 0 // Default to System if unknown

        MaterialAlertDialogBuilder(context)
            .setTitle(resources.getString(R.string.Select_language))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLanguageCode = languageCodes[which]
                LanguageUtils.changeLanguage(context, selectedLanguageCode)
                (context as Activity).recreate()
                dialog.dismiss()
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
