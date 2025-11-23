package com.kulipai.luahook.util

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.subscribeAlways
import io.kulipai.sora.luaj.AndroLuaLanguage
import io.kulipai.sora.luaj.WrapperLanguage
import org.eclipse.tm4e.core.registry.IThemeSource
import org.luaj.lib.jse.JsePlatform

object SoraEditorHelper {
    fun Context.initLuaEditor(editor: CodeEditor, errMessage: TextView) {

        fun loadDefaultThemes() {
            val themes = arrayOf("darcula", "abyss", "quietlight", "solarized_drak")
            val themeRegistry = ThemeRegistry.getInstance()
            themes.forEach { name ->
                val path = "textmate/$name.json"
                try {
                    themeRegistry.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(
                                FileProviderRegistry.getInstance().tryGetInputStream(path),
                                path,
                                null
                            ), name
                        ).apply {
                            if (name != "quietlight") isDark = true
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (isNightMode(this)) {
                themeRegistry.setTheme("darcula")
            } else {
                themeRegistry.setTheme("quietlight")
            }
        }

        fun loadDefaultLanguages() {
            try {
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        fun setupTextmate() {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(applicationContext.assets)
            )
            loadDefaultThemes()
            loadDefaultLanguages()
        }


        fun resetColorScheme() {
            val colorScheme = editor.colorScheme
            editor.colorScheme = colorScheme
        }

        fun ensureTextmateTheme() {
            var editorColorScheme = editor.colorScheme
            if (editorColorScheme !is TextMateColorScheme) {
                editorColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                editor.colorScheme = editorColorScheme
            }
        }



        try {
            val typeface = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")
            editor.typefaceText = typeface
        } catch (e: Exception) {
            // 字体加载失败处理，防止崩溃
            e.printStackTrace()
        }



        setupTextmate()
        resetColorScheme()
        ensureTextmateTheme()
        val language = TextMateLanguage.create(
            "source.lua", true
        )
        val androLuaLanguage = AndroLuaLanguage()
        val diagnosticsContainer = DiagnosticsContainer()
        editor.diagnostics = diagnosticsContainer
        editor.apply {
            // typefaceText = typeface // 已移入 try-catch
            props.stickyScroll = true
            setLineSpacing(2f, 1.1f)
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION

            subscribeAlways<ContentChangeEvent> {
                val c = "=".repeat(99)
                val code = editor.text.toString()
                // 简单的语法检查，需确保 load 函数在环境中可用
                try {
                    val err =
                        JsePlatform.standardGlobals().load("_,err = load([$c[$code]$c]);return err")
                            .call()
                    if (err.toString() == "nil") {
                        errMessage.visibility = View.GONE
                    } else {
                        errMessage.text = err.toString()
                        errMessage.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    // 避免 Lua 解析崩溃导致 App 闪退
                }
            }
//            subscribeAlways<PublishSearchResultEvent> { updatePositionText() }

            subscribeAlways<KeyBindingEvent> { event ->
                if (event.eventType == EditorKeyEvent.Type.DOWN) {
                    // Keybinding handling
                }
            }

            EditorSpanInteractionHandler(this)
            getComponent<EditorAutoCompletion>().setEnabledAnimation(true)
        }
        androLuaLanguage.setOnDiagnosticListener {
            // diagnostics listener
        }

        editor.setEditorLanguage(WrapperLanguage(language, androLuaLanguage))


    }


    fun Context.initEditor(editor: CodeEditor) {

        fun loadDefaultThemes() {
            val themes = arrayOf("darcula", "abyss", "quietlight", "solarized_drak")
            val themeRegistry = ThemeRegistry.getInstance()
            themes.forEach { name ->
                val path = "textmate/$name.json"
                try {
                    themeRegistry.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(
                                FileProviderRegistry.getInstance().tryGetInputStream(path),
                                path,
                                null
                            ), name
                        ).apply {
                            if (name != "quietlight") isDark = true
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (isNightMode(this)) {
                themeRegistry.setTheme("darcula")
            } else {
                themeRegistry.setTheme("quietlight")
            }
        }

        fun loadDefaultLanguages() {
            try {
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        fun setupTextmate() {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(applicationContext.assets)
            )
            loadDefaultThemes()
            loadDefaultLanguages()
        }


        fun resetColorScheme() {
            val colorScheme = editor.colorScheme
            editor.colorScheme = colorScheme
        }

        fun ensureTextmateTheme() {
            var editorColorScheme = editor.colorScheme
            if (editorColorScheme !is TextMateColorScheme) {
                editorColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                editor.colorScheme = editorColorScheme
            }
        }



        try {
            val typeface = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")
            editor.typefaceText = typeface
        } catch (e: Exception) {
            // 字体加载失败处理，防止崩溃
            e.printStackTrace()
        }



        setupTextmate()
        resetColorScheme()
        ensureTextmateTheme()
        val language = TextMateLanguage.create(
            "source.lua", true
        )
        val androLuaLanguage = AndroLuaLanguage()
        val diagnosticsContainer = DiagnosticsContainer()
        editor.diagnostics = diagnosticsContainer
        editor.apply {
            // typefaceText = typeface // 已移入 try-catch
            props.stickyScroll = true
            setLineSpacing(2f, 1.1f)
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION

            subscribeAlways<ContentChangeEvent> {

            }
//            subscribeAlways<PublishSearchResultEvent> { updatePositionText() }

            subscribeAlways<KeyBindingEvent> { event ->
                if (event.eventType == EditorKeyEvent.Type.DOWN) {
                    // Keybinding handling
                }
            }

            EditorSpanInteractionHandler(this)
            getComponent<EditorAutoCompletion>().setEnabledAnimation(true)
        }
        androLuaLanguage.setOnDiagnosticListener {
            // diagnostics listener
        }

        editor.setEditorLanguage(WrapperLanguage(language, androLuaLanguage))


    }


}