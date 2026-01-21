package com.kulipai.luahook.ui.project.editor

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kulipai.luahook.R
import com.kulipai.luahook.core.base.BaseActivity
import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.databinding.ActivityProjectEditorBinding
import com.kulipai.luahook.ui.script.editor.SoraEditorDelegate
import com.kulipai.luahook.ui.script.editor.SoraEditorDelegate.initLuaEditor
import com.kulipai.luahook.ui.script.editor.SoraEditorDelegate.initEditor
import com.kulipai.luahook.ui.script.editor.SymbolAdapter
import java.io.File

class ProjectEditorActivity : BaseActivity<ActivityProjectEditorBinding>() {

    private lateinit var projectName: String
    private lateinit var projectDir: String
    private var currentFile: String = "main.lua" // Relative path
    private val openFiles = mutableListOf<String>()

    private lateinit var fileAdapter: FileListAdapter

    override fun inflateBinding(inflater: LayoutInflater): ActivityProjectEditorBinding {
        return ActivityProjectEditorBinding.inflate(inflater)
    }

    override fun initView() {
        // Let DrawerLayout handle insets via fitsSystemWindows

        setSupportActionBar(binding.toolbar)

        // 监听窗口边距变化（包括状态栏、导航栏、IME键盘）
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            binding.navView.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // SoraEditor integration
        initLuaEditor(binding.editor, binding.errorMessage)

        binding.fileListRecycler.layoutManager = LinearLayoutManager(this)
    }

    private var currentExplorerPath: String = ""

    override fun initData() {
        projectName = intent.getStringExtra("project_name") ?: ""
        if (projectName.isEmpty()) finish()

        title = projectName
        projectDir = "${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$projectName"
        currentExplorerPath = projectDir

        fileAdapter = FileListAdapter(mutableListOf()) { file ->
            if (file.isDirectory) {
                if (file.name == "..") {
                    // Go up
                    val parent = File(currentExplorerPath).parentFile
                    if (parent != null && parent.absolutePath.startsWith(projectDir)) {
                        currentExplorerPath = parent.absolutePath
                        loadFileList()
                    } else {
                        // Already at root or error
                        currentExplorerPath = projectDir
                        loadFileList()
                    }
                } else {
                    // Go down
                    currentExplorerPath = file.path
                    loadFileList()
                }
            } else {
                // Check file type
                if (file.name.endsWith(".png") || file.name.endsWith(".jpg") || file.name.endsWith(".jpeg")) {
                    showImagePreview(file)
                } else {
                    // Open for editing
                    // Calculate relative path for openFile/switchToFile
                    // file.path is absolute. switchToFile expects relative to projectDir? (Actually initData calls openFile("main.lua"))
                    // Let's check openFile logic. It stores filename. switchToFile constructs path using projectName.
                    // The current openFile/switchToFile implementation assumes files are in root of project?
                    // switchToFile: WorkspaceFileManager.read("${WorkspaceFileManager.Project}/$projectName/$filename")
                    // This assumes filename is relative to project root.

                    // We need to support subclasses.
                    // Calculate relative path.
                    val relPath = if (file.path.startsWith(projectDir)) {
                        file.path.substring(projectDir.length + 1)
                    } else {
                        file.name
                    }
                    openFile(relPath)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        }
        binding.fileListRecycler.adapter = fileAdapter

        loadFileList()
        openFile("main.lua")
    }

    // ... (rest of class)

    override fun initEvent() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle Back Press for Drawer
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Find header in drawer to set refresh listener
        val navParams = binding.navView.layoutParams // Accessing to ensure it exists
        val headerView = binding.navView.getChildAt(0)
        headerView.setOnClickListener {
            refreshFileList()
        }

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.text?.let { filename ->
                    if (filename.toString() != currentFile) {
                        saveCurrentFile()
                        switchToFile(filename.toString())
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun loadFileList() {
        // Use currentExplorerPath
        val dir = File(currentExplorerPath)
        val files = mutableListOf<FileItem>()

        // Add ".." if not at root
        if (currentExplorerPath != projectDir && currentExplorerPath.startsWith(projectDir)) {
            files.add(FileItem("..", File(currentExplorerPath).parent ?: projectDir, true))
        }

        // Try Shell ls -F
        val result = com.kulipai.luahook.core.shell.ShellManager.shell("ls -F \"$currentExplorerPath\"")
        if (result is com.kulipai.luahook.core.shell.ShellResult.Success) {
            val lines = result.stdout.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    var isDir = false
                    var name = trimmed

                    if (name.endsWith("/")) {
                        isDir = true
                        name = name.dropLast(1)
                    } else if (name.endsWith("*") || name.endsWith("@") || name.endsWith("=") || name.endsWith("|")) {
                        name = name.dropLast(1)
                    }

                    files.add(FileItem(name, "$currentExplorerPath/$name", isDir))
                }
            }
        } else {
            // Fallback to Java File if shell fails (unlikely if root/shizuku, but maybe permission denied on partial path)
            dir.listFiles()?.forEach {
                files.add(FileItem(it.name, it.path, it.isDirectory))
            }
        }

        // Sort: Directories first, then files
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        fileAdapter.updateData(sorted)
    }

    private fun showImagePreview(file: FileItem) {
        val dialog = android.app.AlertDialog.Builder(this)
        val imageView = android.widget.ImageView(this)
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
        imageView.setImageBitmap(bitmap)
        imageView.adjustViewBounds = true
        dialog.setView(imageView)
        dialog.setPositiveButton("Close", null)
        dialog.show()
    }

    private fun refreshFileList() {
        Toast.makeText(this, "Refreshing files...", Toast.LENGTH_SHORT).show()
        loadFileList()
    }

    private fun openFile(filename: String) {
        if (!openFiles.contains(filename)) {
            openFiles.add(filename)
            val tab = binding.tabLayout.newTab().setText(filename)
            binding.tabLayout.addTab(tab)
            tab.select()
        } else {
            for (i in 0 until binding.tabLayout.tabCount) {
                if (binding.tabLayout.getTabAt(i)?.text == filename) {
                    binding.tabLayout.getTabAt(i)?.select()
                    break
                }
            }
        }
        switchToFile(filename)
    }

    private fun switchToFile(filename: String) {
        currentFile = filename
        val content = WorkspaceFileManager.read("${WorkspaceFileManager.Project}/$projectName/$filename")
        binding.editor.setText(content)
    }

    private fun saveCurrentFile() {
        WorkspaceFileManager.write("${WorkspaceFileManager.Project}/$projectName/$currentFile", binding.editor.text.toString())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 0, 0, "Run")?.setIcon(R.drawable.play_arrow_24px)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 1, 0, "Save")?.setIcon(R.drawable.save_24px)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 2, 0, "Undo")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 3, 0, "Redo")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            0 -> { // Run
                saveCurrentFile()
                runProject()
                true
            }
            1 -> { // Save
                saveCurrentFile()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                true
            }
            2 -> { binding.editor.undo(); true }
            3 -> { binding.editor.redo(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun runProject() {
        // Run on IO to avoid main thread block if scanning projects
        Thread {
            val projects = com.kulipai.luahook.core.project.ProjectManager.getProjects()
            val project = projects.find { it.name == projectName }
            runOnUiThread {
                if (project != null) {
                    if (project.scope.contains("all")) {
                        Toast.makeText(this, "Scope is 'All'. Please open any app to test.", Toast.LENGTH_LONG).show()
                    } else if (project.scope.isNotEmpty()) {
                        val pkg = project.scope[0]
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                            if (launchIntent != null) {
                                Toast.makeText(this, "Launching $pkg...", Toast.LENGTH_SHORT).show()
                                startActivity(launchIntent)
                            } else {
                                Toast.makeText(this, "Could not find launch intent for $pkg", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error launching: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No scope defined.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Project info not found.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentFile()
    }
}

data class FileItem(val name: String, val path: String, val isDirectory: Boolean)

class FileListAdapter(private val files: MutableList<FileItem>, private val onClick: (FileItem) -> Unit) : androidx.recyclerview.widget.RecyclerView.Adapter<FileListAdapter.ViewHolder>() {
    
    class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val textView: android.widget.TextView = view.findViewById(R.id.file_name)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_project_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = files[position]
        holder.textView.text = item.name

        val iconRes = when {
            item.isDirectory -> R.drawable.folder
            item.name.endsWith(".lua") -> R.drawable.lua
            item.name.endsWith(".json") -> R.drawable.json
            item.name.endsWith(".png") || item.name.endsWith(".jpg") || item.name.endsWith(".jpeg") -> R.drawable.description_24px
            else -> R.drawable.file_open_24px
        }
        
        holder.textView.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        holder.itemView.setOnClickListener { onClick(item) }
    }




    override fun getItemCount() = files.size

    fun updateData(newFiles: List<FileItem>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }
}
