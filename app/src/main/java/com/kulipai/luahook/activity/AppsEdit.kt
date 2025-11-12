package com.kulipai.luahook.activity

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.luaj.Globals
import org.luaj.lib.jse.JsePlatform
import java.io.File

class AppsEdit : AppCompatActivity() {

//    private lateinit var completionAdapter: CompletionAdapter

    // 文件分享
    private val FILE_PROVIDER_AUTHORITY = "com.kulipai.luahook.fileprovider"

    //ui绑定区
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }

    //    private val editor: LuaEditor by lazy { findViewById(R.id.editor) }
    private val editor: CodeEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val rootLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }
    private val bottomSymbolBar: LinearLayout by lazy { findViewById(R.id.bottomBar) }
    private val symbolRecyclerView: RecyclerView by lazy { findViewById(R.id.symbolRecyclerView) }
    private val toolRec: RecyclerView by lazy { findViewById(R.id.toolRec) }
    private val errMessage: TextView by lazy { findViewById(R.id.errMessage) }

    //全局变量
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


        //窗口处理
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            // 调整底部符号栏的位置，使其位于输入法上方
            bottomSymbolBar.translationY = -imeInsets.bottom.toFloat()
            fab.translationY = -imeInsets.bottom.toFloat()


            editor.setPadding(0, 0, 0, bottomSymbolBar.height + imeInsets.bottom)

            // 设置根布局的底部内边距
            if (imeInsets.bottom > 0) {
                // 输入法可见时，不需要额外的底部内边距来避免被导航栏遮挡，
                // 因为 bottomSymbolBar 已经移动到输入法上方

                view.setPadding(
                    navigationBarInsets.left,
                    statusBarInsets.top,
                    navigationBarInsets.right,
                    0
                )
            } else {
                // 输入法不可见时，设置底部内边距以避免内容被导航栏遮挡
                view.setPadding(
                    navigationBarInsets.left,
                    statusBarInsets.top,
                    navigationBarInsets.right,
                    navigationBarInsets.bottom
                )
            }


            insets
        }

        // 确保在布局稳定后请求 WindowInsets，以便监听器能够正确工作
        ViewCompat.requestApplyInsets(rootLayout)
//        Toast.makeText(this, editor.err(), Toast.LENGTH_SHORT).show()

        //接收传递信息
        val intent = getIntent()
        if (intent != null) {
            currentPackageName = intent.getStringExtra("packageName").toString()
//            appName = intent.getStringExtra("appName").toString()
            scriptDescription = intent.getStringExtra("scriptDescription").toString()
            scripName = intent.getStringExtra("scripName").toString()
//            toolbar.title = appName
            title = scripName


        }

        symbolRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        symbolRecyclerView.adapter = SymbolAdapter(editor)


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

        val tool =
            listOf(
                resources.getString(R.string.gen_hook_code),
                resources.getString(R.string.funcSign),
                resources.getString(R.string.grammer_converse)
            )

        toolRec.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        toolRec.adapter = ToolAdapter(tool, editor, this)


//        val prefs = getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE)

        //        val script = prefs.getString(currentPackageName, "")
        fun read(path: String): String {
            if (File(path).exists()) {
                return File(path).readText()
            }
            return ""
        }


        val script =
            read("/data/local/tmp/LuaHook/${LShare.AppScript}/$currentPackageName/$scripName.lua")

        editor.setText(script, null)

        fab.setOnClickListener {


            saveScript(editor.text.toString())
            Toast.makeText(this, resources.getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
        }


    }


    //菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menu?.add(0, 0, 0, "Run")
            ?.setIcon(R.drawable.play_arrow_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        menu?.add(0, 1, 0, "Undo")
            ?.setIcon(R.drawable.undo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        menu?.add(0, 2, 0, "Redo")
            ?.setIcon(R.drawable.redo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 3, 0, resources.getString(R.string.format))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, resources.getString(R.string.log))  //LogCat
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, resources.getString(R.string.manual))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 9, 0, resources.getString(R.string.search))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 15, 0, resources.getString(R.string.share))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 25, 0, resources.getString(R.string.setting))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            0 -> {
                saveScript(editor.text.toString())
//                operateAppAdvanced(this,currentPackageName)
                ShellManager.shell("am force-stop $currentPackageName")
                launchApp(this, currentPackageName)
                true

            }

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
                LuaParser.lexer(editor.text, Globals(), Flag())
                editor.setText(AutoIndent.format(editor.text, 2))

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

            9 -> {
//                editor.search()
                true
            }


            // 分享
            15 -> {
                if (getSharedPreferences("conf", MODE_PRIVATE).getString("author", "")
                        .isNullOrEmpty()
                ) {

                    val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null)
                    val inputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
                    val edit = view.findViewById<TextInputEditText>(R.id.edit)
                    inputLayout.hint = "作者名称"

                    edit.doBeforeTextChanged { text, start, count, after ->
                        // text: 改变前的内容
                        // start: 改变开始的位置
                        // count: 将被替换的旧内容长度
                        // after: 新内容长度
                        edit.error = null

                    }
                    MaterialAlertDialogBuilder(this)
                        .setTitle(resources.getString(R.string.author_info))
                        .setView(view)
                        .setPositiveButton(resources.getString(R.string.sure), { dialog, which ->

                            if (edit.text.isNullOrEmpty()) {
                                edit.error = resources.getString(R.string.input_id)
                            } else {
                                getSharedPreferences("conf", MODE_PRIVATE).edit {
                                    putString("author", edit.text.toString())
                                    apply()
                                }
                                shareFileFromTmp(
                                    this,
                                    "/data/local/tmp/LuaHook/${LShare.AppScript}/$currentPackageName/$scripName.lua"
                                )
                            }
                        })
                        .setNegativeButton(resources.getString(R.string.cancel), { dialog, which ->
                            dialog.dismiss()
                        })
                        .show()
                } else {
                    shareFileFromTmp(
                        this,
                        "/data/local/tmp/LuaHook/${LShare.AppScript}/$currentPackageName/$scripName.lua"
                    )
                }

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


//    // 写入 SharedPreferences 并修改权限
//    fun savePrefs(packageName: String, text: String) {
//        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
//        prefs.edit().apply {
//            putString(packageName, text)
//            apply()
//        }
//    }


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

                // --- 开始修改部分：先读取，再合并，再写入 ---
                // 1. 读取原始文件的所有内容
                val originalContent = originalFile.readText()

                // 获取作者名字
                author =
                    getSharedPreferences("conf", MODE_PRIVATE).getString("author", "").toString()

                // 2. 定义要写入开头的额外内容
                // 你可以根据需要修改 headerContent 的内容
                val headerContent =
                    "-- name: $scripName\n-- descript: $scriptDescription\n-- package: $currentPackageName\n-- author: $author\n\n"

                // 3. 将新内容和原始内容合并
                val mergedContent = headerContent + originalContent

                // 4. 将合并后的内容写入到目标文件，这会覆盖目标文件原有内容
                destinationFile.writeText(mergedContent)
                // --- 结束修改部分 ---

                destinationFile
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "文件复制或写入失败: ${e.message}", Toast.LENGTH_LONG)
                        .show()
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
                            Toast.makeText(
                                context,
                                resources.getString(R.string.no_apps_share),
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            context,
                            resources.getString(R.string.errors_sharing) + "${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
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

