package com.kulipai.luahook.ui.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kulipai.luahook.R
import com.kulipai.luahook.ui.about.AboutActivity
import com.kulipai.luahook.util.LanguageUtil

class SettingsActivity : AppCompatActivity() {

    private val language: MaterialCardView by lazy { findViewById(R.id.language) }
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val about: MaterialCardView by lazy { findViewById(R.id.about) }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)


        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v: View?, insets: WindowInsetsCompat? ->
            val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            v!!.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }


        language.setOnClickListener {
//            var menu = SimpleMenuPopupWindow(this)
//            menu.entries = arrayOf("中文","English").toList()
//            menu.show(language, language.parent as View,100)
////
//            val menu =
            showLanguagePickerDialog(this)

        }

        about.setOnClickListener {
        val intent= Intent(this, AboutActivity::class.java)
        startActivity(intent)

        }


    }

    fun showLanguagePickerDialog(context: Context) {
        val languages = arrayOf("English", "简体中文","繁體中文")
        val languageCodes = arrayOf(
            LanguageUtil.LANGUAGE_ENGLISH, LanguageUtil.LANGUAGE_CHINESE,
            LanguageUtil.LANGUAGE_CHINESE_TRADITIONAL)
        val currentLanguage = LanguageUtil.getCurrentLanguage(context)
        val checkedItem = languageCodes.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(context)
            .setTitle(resources.getString(R.string.Select_language))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLanguageCode = languageCodes[which]
                LanguageUtil.changeLanguage(context, selectedLanguageCode)
                (context as Activity).recreate()
                dialog.dismiss()
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}