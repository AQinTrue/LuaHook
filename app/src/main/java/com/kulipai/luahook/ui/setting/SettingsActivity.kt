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
        val languages = arrayOf("English", "简体中文", "繁體中文")
        val languageCodes = arrayOf(
            LanguageUtils.LANGUAGE_ENGLISH, LanguageUtils.LANGUAGE_CHINESE,
            LanguageUtils.LANGUAGE_CHINESE_TRADITIONAL
        )
        val currentLanguage = LanguageUtils.getCurrentLanguage(context)
        val checkedItem = languageCodes.indexOf(currentLanguage)

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
