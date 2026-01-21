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

    override fun initData() {
        projectName = intent.getStringExtra("project_name") ?: ""
        if (projectName.isEmpty()) finish()
        
        title = projectName
        projectDir = "${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$projectName"
        
        fileAdapter = FileListAdapter(mutableListOf()) { file ->
             openFile(file.name)
             binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        binding.fileListRecycler.adapter = fileAdapter
        
        loadFileList()
        openFile("main.lua")
    }
    
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
        val dir = File(projectDir)
        var files = dir.listFiles()?.filter { it.isFile }?.map { FileItem(it.name, it.path) } ?: emptyList()
        
        if (files.isEmpty()) {
            val result = com.kulipai.luahook.core.shell.ShellManager.shell("ls -F \"$projectDir\"")
            if (result is com.kulipai.luahook.core.shell.ShellResult.Success) {
                val lines = result.stdout.split("\n")
                val shellFiles = mutableListOf<FileItem>()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.endsWith("/")) {
                         var name = trimmed
                         if (name.endsWith("*") || name.endsWith("@") || name.endsWith("=") || name.endsWith("|")) {
                             name = name.dropLast(1)
                         }
                         shellFiles.add(FileItem(name, "$projectDir/$name"))
                    }
                }
                files = shellFiles
            }
        }
        
        files = files.sortedBy { it.name }
        fileAdapter.updateData(files)
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

data class FileItem(val name: String, val path: String)

class FileListAdapter(private val files: MutableList<FileItem>, private val onClick: (FileItem) -> Unit) : androidx.recyclerview.widget.RecyclerView.Adapter<FileListAdapter.ViewHolder>() {
    class ViewHolder(val view: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.widget.TextView(parent.context)
        view.setPadding(32, 32, 32, 32)
        view.setTextAppearance(android.R.style.TextAppearance_Material_Body1)
        // Add file icon
        view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.file_open_24px, 0, 0, 0)
        view.compoundDrawablePadding = 16
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.text = files[position].name
        holder.view.setOnClickListener { onClick(files[position]) }
    }
    
    override fun getItemCount() = files.size
    
    fun updateData(newFiles: List<FileItem>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }
}

