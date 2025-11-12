package com.kulipai.luahook.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.R
import com.kulipai.luahook.adapter.SymbolAdapter
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.LShare.read
import com.kulipai.luahook.util.isNightMode
import com.myopicmobile.textwarrior.common.AutoIndent
import com.myopicmobile.textwarrior.common.Flag
import com.myopicmobile.textwarrior.common.LuaParser
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.subscribeAlways
import io.kulipai.sora.luaj.AndroLuaLanguage
import io.kulipai.sora.luaj.WrapperLanguage
import org.eclipse.tm4e.core.registry.IThemeSource
import org.luaj.Globals
import org.luaj.lib.jse.JsePlatform
import java.io.File

class EditActivity : AppCompatActivity() {

    private val errMessage: TextView by lazy { findViewById(R.id.errMessage) }


    private fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName!!
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }


    fun getDynamicColor(context: Context, @AttrRes colorAttributeResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(colorAttributeResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }


    @SuppressLint("SetTextI18n")
    private fun showLsposedInfoDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_info, null)

        val appLogoImageView: ImageView = view.findViewById(R.id.app_logo)
        val appNameTextView: TextView = view.findViewById(R.id.app_name)
        val appVersionTextView: TextView = view.findViewById(R.id.app_version)
        val appDescriptionTextView: TextView = view.findViewById(R.id.app_description)

        // 设置应用信息
        appLogoImageView.setImageResource(R.drawable.logo)
        appNameTextView.text = "LuaHook"
        appVersionTextView.text = getAppVersionName(this)

        // 构建包含可点击链接的 SpannableString (与之前的示例代码相同)
        val descriptionText =
            resources.getString(R.string.find_us) + "\n" + resources.getString(R.string.find_us2)

        val spannableString = SpannableString(descriptionText)

        // 设置 GitHub 链接
        val githubStartIndex = descriptionText.indexOf("GitHub")
        val githubEndIndex = githubStartIndex + "GitHub".length
        if (githubStartIndex != -1) {
            val clickableSpanGithub = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrl("https://github.com/KuLiPai/LuaHook")
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = getDynamicColor(
                        this@EditActivity,
                        androidx.appcompat.R.attr.colorPrimary
                    )
                    ds.isUnderlineText = true
                }
            }
            spannableString.setSpan(
                clickableSpanGithub,
                githubStartIndex,
                githubEndIndex,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 设置 Telegram 链接
        val telegramStartIndex = descriptionText.indexOf("Telegram")
        val telegramEndIndex = telegramStartIndex + "Telegram".length
        if (telegramStartIndex != -1) {
            val clickableSpanTelegram = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrl("https://t.me/LuaXposed")
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = getDynamicColor(
                        this@EditActivity,
                        androidx.appcompat.R.attr.colorPrimary
                    )
                    ds.isUnderlineText = true
                }
            }
            spannableString.setSpan(
                clickableSpanTelegram,
                telegramStartIndex,
                telegramEndIndex,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 设置 QQ 链接
//        val qqStartIndex = descriptionText.indexOf("QQ")
//        val qqEndIndex = descriptionText.indexOf(" ", qqStartIndex) + " ".length
//        if (qqStartIndex != -1 && qqEndIndex != -1) {
//            val clickableSpanQq = object : ClickableSpan() {
//                override fun onClick(widget: View) {
//                    openUrl("https://qm.qq.com/cgi-bin/qm/qr?k=your_qq_key")
//                }
//
//                @SuppressLint("ResourceType")
//                override fun updateDrawState(ds: TextPaint) {
//                    super.updateDrawState(ds)
//                    ds.color = getDynamicColor(
//                        this@MainActivity,
//                        com.google.android.material.R.attr.colorPrimary
//                    )
//                    ds.isUnderlineText = true
//
//                }
//            }
//            spannableString.setSpan(
//                clickableSpanQq,
//                qqStartIndex,
//                qqEndIndex,
//                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
//            )
//        }

        // 设置 TextView 的文本和 MovementMethod
        appDescriptionTextView.text = spannableString
        appDescriptionTextView.movementMethod = LinkMovementMethod.getInstance()

        // 使用 MaterialAlertDialogBuilder 构建并显示对话框
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .show()
    }


    //菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Undo")
            ?.setIcon(R.drawable.undo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        menu?.add(0, 2, 0, "Redo")
            ?.setIcon(R.drawable.redo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu?.add(0, 3, 0, resources.getString(R.string.format))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, resources.getString(R.string.log))  //LogCat
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, resources.getString(R.string.manual))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 6, 0, resources.getString(R.string.about))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 9, 0, resources.getString(R.string.search))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                // "Undo"
                editor.undo()
                true
            }

            2 -> {
                // "Redo"
                editor.redo()
                true
            }

            3 -> {
                // 格式化
//                editor.format()
                true
            }

            4 -> {
                //LogCat
                val intent = Intent(this, LogCatActivity::class.java)
                startActivity(intent)
                true
            }

            5 -> {
                //示例
                val intent = Intent(this, Manual::class.java)
                startActivity(intent)
                true
            }

            6 -> {
                showLsposedInfoDialog()
                true
            }

            9 -> {
                LuaParser.lexer(editor.text, Globals(), Flag());
                editor.setText(AutoIndent.format(editor.text, 2))

//                editor.search()
                true
            }


            else -> false
        }
    }

    private val editor: CodeEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val rootLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }
    private val bottomSymbolBar: LinearLayout by lazy { findViewById(R.id.bottomBar) }

    companion object


    fun saveScript(text: String) {
        LShare.write("/global.lua", text)
    }


    override fun onStop() {
        super.onStop()
        saveScript(editor.text.toString())
    }

    override fun onPause() {
        super.onPause()
        saveScript(editor.text.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_global_edit)
        setSupportActionBar(toolbar)

        val symbolRecyclerView: RecyclerView = findViewById(R.id.symbolRecyclerView)
        symbolRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        symbolRecyclerView.adapter = SymbolAdapter(editor)


        //窗口处理
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            insets.getInsets(WindowInsetsCompat.Type.statusBars())

            // 调整底部符号栏的位置，使其位于输入法上方
            bottomSymbolBar.translationY = -imeInsets.bottom.toFloat()
            fab.translationY = -imeInsets.bottom.toFloat()

            editor.setPadding(0, 0, 0, navigationBarInsets.bottom)


            // 设置根布局的底部内边距
            if (imeInsets.bottom > 0) {
                // 输入法可见时，不需要额外的底部内边距来避免被导航栏遮挡，
                // 因为 bottomSymbolBar 已经移动到输入法上方
                view.setPadding(
                    navigationBarInsets.left,
                    0,
                    navigationBarInsets.right,
                    0
                )

            } else {
                // 输入法不可见时，设置底部内边距以避免内容被导航栏遮挡
                view.setPadding(
                    navigationBarInsets.left,
                    0,
                    navigationBarInsets.right,
                    navigationBarInsets.bottom
                )
            }

            insets
        }

        // 确保在布局稳定后请求 WindowInsets，以便监听器能够正确工作
        ViewCompat.requestApplyInsets(rootLayout)



        val luaScript = read("/global.lua")
        if (luaScript == "") {
            val lua = """
        """.trimIndent()
            saveScript(lua)
        }


        //////////////////============sora=====================
        val typeface = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")
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
            typefaceText = typeface
            props.stickyScroll = true
            setLineSpacing(2f, 1.1f)
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION

            // Update display dynamically
            // Use CodeEditor#subscribeEvent to add listeners of different events to editor
//            subscribeAlways<SelectionChangeEvent> {
//                updatePositionText()
//                completionAdapter.submitList(emptyList())
//                completionTrigger.tryEmit(completionTrigger.value + 1)
//            }
            subscribeAlways<ContentChangeEvent> {
                val c = "=".repeat( 99)
                val err = JsePlatform.standardGlobals().load("_,err = load([$c[${editor.text}]$c]);return err").call()
                if (err.toString() == "nil") {
                    errMessage.visibility = View.GONE
                }else
                {
                    errMessage.text = err.toString()
                    errMessage.visibility = View.VISIBLE

                }
            }
            subscribeAlways<PublishSearchResultEvent> { updatePositionText() }
//            subscribeAlways<ContentChangeEvent> {
//                postDelayedInLifecycle(
//                    ::updateBtnState,
//                    50
//                )
//                completionAdapter.submitList(emptyList())
//                completionTrigger.tryEmit(completionTrigger.value + 1)
//            }
//            subscribeAlways<SideIconClickEvent> {
//                toast(R.string.tip_side_icon)
//            }
//            subscribeAlways<TextSizeChangeEvent> { event ->
//                Log.d(
//                    TAG,
//                    "TextSizeChangeEvent onReceive() called with: oldTextSize = [${event.oldTextSize}], newTextSize = [${event.newTextSize}]"
//                )
//            }

            subscribeAlways<KeyBindingEvent> { event ->
                if (event.eventType == EditorKeyEvent.Type.DOWN) {
//                    toast(
//                        "Keybinding event: " + generateKeybindingString(event),
//                        Toast.LENGTH_LONG
//                    )
                }
            }

            // Handle span interactions
            EditorSpanInteractionHandler(this)
            getComponent<EditorAutoCompletion>()
                .setEnabledAnimation(true)
        }
        androLuaLanguage.setOnDiagnosticListener {
            /*  diagnosticsContainer.reset()
              diagnosticsContainer.addDiagnostics(it)
              editor.diagnostics = diagnosticsContainer*/
        }


        editor.setEditorLanguage(WrapperLanguage(language, androLuaLanguage))
        //////////////////============sora=====================





        editor.setText(luaScript)

        fab.setOnClickListener {
            saveScript(editor.text.toString())
            Toast.makeText(this, resources.getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
        }





    }





    private fun setupTextmate() {
        // Add assets file provider so that files in assets can be loaded
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(
                applicationContext.assets // use application context
            )
        )
        loadDefaultThemes()
        loadDefaultLanguages()
    }

    private /*suspend*/ fun loadDefaultThemes() /*= withContext(Dispatchers.IO)*/ {
        val themes = arrayOf("darcula", "abyss", "quietlight", "solarized_drak")
        val themeRegistry = ThemeRegistry.getInstance()
        themes.forEach { name ->
            val path = "textmate/$name.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                    ), name
                ).apply {
                    if (name != "quietlight") {
                        isDark = true
                    }
                }
            )
        }
        if (isNightMode(this)) {
            themeRegistry.setTheme("abyss")

        } else {
            themeRegistry.setTheme("quietlight")

        }
    }

    private /*suspend*/ fun loadDefaultLanguages() /*= withContext(Dispatchers.Main)*/ {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    private fun resetColorScheme() {
        editor.apply {

            val colorScheme = this.colorScheme
            // reset
            this.colorScheme = colorScheme
        }
    }

    private fun ensureTextmateTheme() {
        var editorColorScheme = editor.colorScheme
        if (editorColorScheme !is TextMateColorScheme) {
            editorColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.colorScheme = editorColorScheme
        }
    }

    /**
     * Update editor position tracker text
     */
    private fun updatePositionText() {
        val cursor = editor.cursor
        var text =
            (1 + cursor.leftLine).toString() + ":" + cursor.leftColumn + ";" + cursor.left + " "

        text += if (cursor.isSelected) {
            "(" + (cursor.right - cursor.left) + " chars)"
        } else {
            val content = editor.text
            if (content.getColumnCount(cursor.leftLine) == cursor.leftColumn) {
                "(<" + content.getLine(cursor.leftLine).lineSeparator.let {
                    if (it == LineSeparator.NONE) {
                        "EOF"
                    } else {
                        it.name
                    }
                } + ">)"
            } else {
                "(" + content.getLine(cursor.leftLine)
                    .codePointStringAt(cursor.leftColumn)
                    .escapeCodePointIfNecessary() + ")"
            }
        }

        // Indicator for text matching
        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            val idx = searcher.currentMatchedPositionIndex
            val count = searcher.matchedPositionCount
            val matchText = if (count == 0) {
                "no match"
            } else if (count == 1) {
                "1 match"
            } else {
                "$count matches"
            }
            text += if (idx == -1) {
                "($matchText)"
            } else {
                "(${idx + 1} of $matchText)"
            }
        }

//        binding.positionDisplay.text = text
    }


    fun CharSequence.codePointStringAt(index: Int): String {
        val cp = Character.codePointAt(this, index)
        return String(Character.toChars(cp))
    }

    fun String.escapeCodePointIfNecessary() =
        when (this) {
            "\n" -> "\\n"
            "\t" -> "\\t"
            "\r" -> "\\r"
            " " -> "<ws>"
            else -> this
        }

//    private fun updateBtnState() {
//        undo?.isEnabled = binding.editor.canUndo()
//        redo?.isEnabled = binding.editor.canRedo()
//    }


}