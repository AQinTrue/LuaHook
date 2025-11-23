package com.kulipai.luahook.activity

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doBeforeTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kulipai.luahook.R
import com.kulipai.luahook.adapter.SymbolAdapter
import com.kulipai.luahook.adapter.ToolAdapter
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.ShellManager
import com.kulipai.luahook.util.d
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
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.subscribeAlways
import io.kulipai.sora.luaj.AndroLuaLanguage
import io.kulipai.sora.luaj.WrapperLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.luaj.Globals
import org.luaj.lib.jse.JsePlatform
import java.io.File
import androidx.core.view.isVisible

class AppsEdit : AppCompatActivity() {

    // 文件分享
    private val FILE_PROVIDER_AUTHORITY = "com.kulipai.luahook.fileprovider"

    // UI 绑定区
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val editor: CodeEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val rootLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }

    // 1. 懒加载搜索视图控件
    private val searchPanel: View by lazy { findViewById(R.id.search_panel) }
    private val etSearch: EditText by lazy { findViewById(R.id.et_search) }
    private val etReplace: EditText by lazy { findViewById(R.id.et_replace) }
    private val btnPrev: View by lazy { findViewById(R.id.btn_search_prev) }
    private val btnNext: View by lazy { findViewById(R.id.btn_search_next) }
    private val btnClose: View by lazy { findViewById(R.id.btn_search_close) }
    private val btnReplaceOne: View by lazy { findViewById(R.id.btn_replace_one) }
    private val btnReplaceAll: View by lazy { findViewById(R.id.btn_replace_all) }

    // 注意：如果 XML 里 bottomBar 在 Linear1 内部，这里还是能找到 ID 的，不需要改
    private val bottomSymbolBar: LinearLayout by lazy { findViewById(R.id.bottomBar) }
    private val symbolRecyclerView: RecyclerView by lazy { findViewById(R.id.symbolRecyclerView) }
    private val toolRec: RecyclerView by lazy { findViewById(R.id.toolRec) }
    // 确保 XML 里加上了这个 TextView 的 ID，否则会闪退
    private val errMessage: TextView by lazy { findViewById(R.id.errMessage) }

    // 全局变量
    private lateinit var currentPackageName: String
    private lateinit var scripName: String
    private lateinit var scriptDescription: String
    private var author: String = ""

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
        setContentView(R.layout.activity_apps_edit)
        setSupportActionBar(toolbar)


        val root = findViewById<View>(R.id.main)

        // 监听窗口边距变化（包括状态栏、导航栏、IME键盘）
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            // 重点：同时获取 systemBars (导航栏等) 和 ime (键盘) 的 Insets
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )

            // 将这些边距应用为 Padding
            // 这样，当键盘弹出时，bottom padding 会变成键盘高度
            // 因为 XML 中编辑器是 weight=1，它会自动缩小高度，底栏被顶在键盘上方
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)

            // 返回 insets 让其他部分也能处理（视情况而定，通常这就够了）
            insets
        }

        // 核心修复逻辑结束 ==============================================

        // 接收传递信息
        val intent = getIntent()
        if (intent != null) {
            currentPackageName = intent.getStringExtra("packageName").toString()
            scriptDescription = intent.getStringExtra("scriptDescription").toString()
            scripName = intent.getStringExtra("scripName").toString()
            title = scripName
        }

        symbolRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        symbolRecyclerView.adapter = SymbolAdapter(editor)

        //////////////////============sora=====================
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
                    val err = JsePlatform.standardGlobals().load("_,err = load([$c[$code]$c]);return err").call()
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
            subscribeAlways<PublishSearchResultEvent> { updatePositionText() }

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
        //////////////////============sora=====================

        val tool = listOf(
            resources.getString(R.string.gen_hook_code),
            resources.getString(R.string.funcSign),
            resources.getString(R.string.grammer_converse)
        )

        toolRec.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        toolRec.adapter = ToolAdapter(tool, editor, this)

        fun read(path: String): String {
            if (File(path).exists()) {
                return File(path).readText()
            }
            return ""
        }

        val scriptPath = "/data/local/tmp/LuaHook/${LShare.AppScript}/$currentPackageName/$scripName.lua"
        val script = read(scriptPath)

        editor.setText(script, null)

        fab.setOnClickListener {
            saveScript(editor.text.toString())
            Toast.makeText(this, resources.getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
        }
        initSearchPanel()
    }

    // 菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 0, 0, "Run")?.setIcon(R.drawable.play_arrow_24px)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 1, 0, "Undo")?.setIcon(R.drawable.undo_24px)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 2, 0, "Redo")?.setIcon(R.drawable.redo_24px)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 3, 0, resources.getString(R.string.format))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, resources.getString(R.string.log))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, resources.getString(R.string.manual))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 9, 0, resources.getString(R.string.search))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 15, 0, resources.getString(R.string.share))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 25, 0, resources.getString(R.string.setting))?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            0 -> {
                saveScript(editor.text.toString())
                ShellManager.shell("am force-stop $currentPackageName")
                launchApp(this, currentPackageName)
                true
            }
            1 -> {
                editor.undo()
                true
            }
            2 -> {
                editor.redo()
                true
            }
            3 -> {
                try {
                    val startLine = editor.cursor.leftLine
                    val luaCode = editor.text
                    LuaParser.lexer(luaCode, Globals(), Flag())
                    editor.setText(AutoIndent.format(luaCode, 2))
                    editor.setSelection(startLine, 0)
                } catch (e: Exception) {
                    Toast.makeText(this, "Format failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                true
            }
            4 -> {
                val intent = Intent(this, LogCatActivity::class.java)
                startActivity(intent)
                true
            }
            5 -> {
                val intent = Intent(this, Manual::class.java)
                startActivity(intent)
                true
            }
            9 -> {
                if (searchPanel.isVisible) {
                    closeSearchPanel()
                } else {
                    openSearchPanel()
                }
                true
            }
            15 -> {
                handleShare()
                true
            }
            25 -> {
                val intent = Intent(this, ScriptSetActivity::class.java)
                intent.putExtra(
                    "path",
                    LShare.DIR + LShare.AppScript + "/" + currentPackageName + "/" + scripName + ".lua"
                )
                startActivity(intent)
                true
            }
            else -> false
        }
    }

    private fun handleShare() {
        val prefs = getSharedPreferences("conf", MODE_PRIVATE)
        if (prefs.getString("author", "").isNullOrEmpty()) {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null)
            val inputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
            val edit = view.findViewById<TextInputEditText>(R.id.edit)
            inputLayout.hint = "作者名称"

            edit.doBeforeTextChanged { _, _, _, _ -> edit.error = null }

            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.author_info))
                .setView(view)
                .setPositiveButton(resources.getString(R.string.sure)) { _, _ ->
                    if (edit.text.isNullOrEmpty()) {
                        edit.error = resources.getString(R.string.input_id)
                    } else {
                        prefs.edit {
                            putString("author", edit.text.toString())
                            apply()
                        }
                        shareFileFromTmp(
                            this,
                            "/data/local/tmp/LuaHook/${LShare.AppScript}/$currentPackageName/$scripName.lua"
                        )
                    }
                }
                .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            shareFileFromTmp(
                this,
                "/data/local/tmp/LuaHook/${LShare.AppScript}/$currentPackageName/$scripName.lua"
            )
        }
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            try {
                context.startActivity(launchIntent)
                Log.i("LaunchApp", "Successfully launched $packageName")
                true
            } catch (e: Exception) {
                Log.e("LaunchApp", "Failed to launch $packageName: ${e.message}")
                false
            }
        } else {
            Log.w("LaunchApp", "Launch intent not found for $packageName")
            false
        }
    }

    fun saveScript(script: String) {
        val path = LShare.AppScript + "/" + currentPackageName + "/" + scripName + ".lua"
        LShare.write(path, script)
    }

    fun shareFileFromTmp(
        context: Context,
        sourceFilePath: String,
        title: String = resources.getString(R.string.share_doc),
        mimeType: String = "*/*"
    ) {
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            val originalFile = File(sourceFilePath)
            if (!originalFile.exists() || !originalFile.canRead()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.source_file_not_exist) + sourceFilePath,
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            val copiedFile: File? = try {
                val cacheDir = context.cacheDir
                val destinationFile = File(cacheDir, originalFile.name)
                val originalContent = originalFile.readText()
                author = getSharedPreferences("conf", MODE_PRIVATE).getString("author", "").toString()
                val headerContent = "-- name: $scripName\n-- descript: $scriptDescription\n-- package: $currentPackageName\n-- author: $author\n\n"
                val mergedContent = headerContent + originalContent
                destinationFile.writeText(mergedContent)
                destinationFile
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "文件复制或写入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                null
            }

            copiedFile?.let { fileToShare ->
                withContext(Dispatchers.Main) {
                    try {
                        val fileUri: Uri = getFileUri(context, fileToShare)
                        val shareIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            type = mimeType
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, title)
                        if (shareIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(chooser)
                        } else {
                            Toast.makeText(context, resources.getString(R.string.no_apps_share), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, resources.getString(R.string.errors_sharing) + "${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }

    private fun setupTextmate() {
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(applicationContext.assets)
        )
        loadDefaultThemes()
        loadDefaultLanguages()
    }

    private fun loadDefaultThemes() {
        val themes = arrayOf("darcula", "abyss", "quietlight", "solarized_drak")
        val themeRegistry = ThemeRegistry.getInstance()
        themes.forEach { name ->
            val path = "textmate/$name.json"
            try {
                themeRegistry.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
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

    private fun loadDefaultLanguages() {
        try {
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetColorScheme() {
        val colorScheme = editor.colorScheme
        editor.colorScheme = colorScheme
    }

    private fun ensureTextmateTheme() {
        var editorColorScheme = editor.colorScheme
        if (editorColorScheme !is TextMateColorScheme) {
            editorColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.colorScheme = editorColorScheme
        }
    }

    private fun updatePositionText() {
        // Position tracking logic...
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

    private fun initSearchPanel() {
        // 监听搜索框文本变化
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (text.isNotEmpty()) {
                    // search(text, options)
                    // 参数2 SearchOptions: (ignoreCase, normal/regex)
                    // 这里默认: 不区分大小写(false -> true才区分), 普通模式(false -> true才正则)
                    // 如果你想完全精确搜索，可以根据需求调整 SearchOptions
                    editor.searcher.search(text, EditorSearcher.SearchOptions(false, false))
                } else {
                    editor.searcher.stopSearch()
                }
            }
        })

        // 监听键盘的"搜索"按钮
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                editor.searcher.gotoNext()
                true
            } else {
                false
            }
        }

        // 上一个
        btnPrev.setOnClickListener {
            editor.searcher.gotoPrevious()
        }

        // 下一个
        btnNext.setOnClickListener {
            editor.searcher.gotoNext()
        }

        // 替换当前选中
        btnReplaceOne.setOnClickListener {
            if (editor.searcher.hasQuery()) {
                val replaceText = etReplace.text.toString()
                try {
                    editor.searcher.replaceCurrentMatch(replaceText)
                } catch (e: Exception) {
                    Toast.makeText(this, "Replace failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 替换所有
        btnReplaceAll.setOnClickListener {
            if (editor.searcher.hasQuery()) {
                val replaceText = etReplace.text.toString()
                try {
                    editor.searcher.replaceAll(replaceText)
                } catch (e: Exception) {
                    Toast.makeText(this, "Replace All failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 关闭面板
        btnClose.setOnClickListener {
            closeSearchPanel()
        }
    }

    private fun openSearchPanel() {
        searchPanel.visibility = View.VISIBLE
        etSearch.requestFocus()
        // 弹出键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)

        // 如果之前有选中的文本，自动填入搜索框
        if (editor.cursor.isSelected) {
            val selectedText = editor.text.substring(editor.cursor.left, editor.cursor.right)
            if (selectedText.length < 50 && !selectedText.contains("\n")) { // 限制长度防止填入大段代码
                etSearch.setText(selectedText)
                // 移动光标到末尾
                etSearch.setSelection(selectedText.length)
            }
        }
    }

    private fun closeSearchPanel() {
        searchPanel.visibility = View.GONE
        editor.searcher.stopSearch()
        // 关闭键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        // 焦点还给编辑器
        editor.requestFocus()
    }



    override fun onDestroy() {
        super.onDestroy()
        editor.release()
    }
}