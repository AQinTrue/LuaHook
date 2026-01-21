package com.kulipai.luahook.ui.project.config

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.kulipai.luahook.R
import com.kulipai.luahook.core.base.BaseActivity
import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.core.project.ProjectManager
import com.kulipai.luahook.core.project.Project
import com.kulipai.luahook.databinding.ActivityProjectConfigBinding
import com.kulipai.luahook.ui.project.create.CreateProjectActivity
import com.kulipai.luahook.ui.script.selector.ScopeSelectorActivity
import java.io.File

class ProjectConfigActivity : BaseActivity<ActivityProjectConfigBinding>() {

    private lateinit var originalProjectName: String
    private var currentProject: Project? = null

    private var selectedIconPath: String? = null
    private var selectedIconUnicode: String? = null
    private val selectedScope = mutableListOf<String>()

    private val scopeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val data = result.data
             val list = data?.getStringArrayListExtra("selected_scope")
             if (list != null) {
                 selectedScope.clear()
                 selectedScope.addAll(list)
                 updateScopeChips()
             }
        }
    }
    
    // Icon Picker
    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
             selectedIconPath = it.toString()
             selectedIconUnicode = null
             binding.imgProjectIcon.setImageURI(it)
        }
    }

    override fun inflateBinding(inflater: LayoutInflater): ActivityProjectConfigBinding {
        return ActivityProjectConfigBinding.inflate(inflater)
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = "Project Settings"
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnCreate.text = "Save Changes" // Reusing layout, button ID is btn_create
    }

    override fun initData() {
        originalProjectName = intent.getStringExtra("project_name") ?: ""
        if (originalProjectName.isEmpty()) {
            finish()
            return
        }
        
        // Find Project Data
        // Ideally we should have a getProject(name)
        val projects = ProjectManager.getProjects() // Reading parsing all again is not super efficient but robust for now
        currentProject = projects.find { it.name == originalProjectName }
        
        if (currentProject == null) {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Populate UI
        binding.etProjectName.setText(currentProject!!.name)
        binding.etProjectDesc.setText(currentProject!!.description)
        binding.etProjectAuthor.setText(currentProject!!.author)
        binding.etProjectLauncher.setText(currentProject!!.launcher)
        
        // Disable rename for now? Usually renaming projects implies moving folders.
        // Let's allow it if user requested "modify name". 
        // We will have to handle folder move.
        
        // Icon
        if (currentProject!!.icon.length == 1 && currentProject!!.icon.codePointAt(0) > 255) { // Assuming unicode
             val symbol = currentProject!!.icon
             selectedIconUnicode = symbol
             val font = try {
                 androidx.core.content.res.ResourcesCompat.getFont(this, R.font.material_symbols)
             } catch(e: Exception) { null }
             binding.imgProjectIcon.setImageBitmap(textToBitmap(symbol, font))
        } else {
             // File path relative to project
             val iconFile = File("${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/${currentProject!!.name}", currentProject!!.icon)
             if (iconFile.exists()) {
                 binding.imgProjectIcon.setImageBitmap(android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath))
             } else {
                 binding.imgProjectIcon.setImageResource(R.mipmap.ic_launcher)
             }
        }
        
        // Scope
        selectedScope.addAll(currentProject!!.scope)
        if (selectedScope.isEmpty()) selectedScope.add("all")
        updateScopeChips()
    }

    override fun initEvent() {
        binding.cardIconPreview.setOnClickListener {
            showIconSelectionDialog()
        }
        
        binding.btnSelectScope.setOnClickListener {
             val intent = Intent(this, ScopeSelectorActivity::class.java)
             val current = if (selectedScope.contains("all")) ArrayList() else ArrayList(selectedScope)
             intent.putStringArrayListExtra("current_scope", current)
             scopeLauncher.launch(intent)
        }
        
        binding.btnAddManualScope.setOnClickListener {
            showAddScopeDialog()
        }

        binding.btnCreate.setOnClickListener {
            saveChanges()
        }
    }
    
    private fun saveChanges() {
        val newName = binding.etProjectName.text.toString().trim()
        val newDesc = binding.etProjectDesc.text.toString().trim()
        
        if (newName.isEmpty()) {
             binding.etProjectName.error = "Name cannot be empty"
             return
        }
        
        // Handle Rename if changed
        if (newName != originalProjectName) {
            // Check if exists
             if (WorkspaceFileManager.directoryExists("${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$newName")) {
                 Toast.makeText(this, "Project name already exists", Toast.LENGTH_SHORT).show()
                 return
             }
             // Move directory
             val oldDir = "${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$originalProjectName"
             val newDir = "${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$newName"
             
             // Simple move utilizing shell
             val mvResult = com.kulipai.luahook.core.shell.ShellManager.shell("mv \"$oldDir\" \"$newDir\"")
             // Note: Renaming also requires updating info.json key!
             
             if (mvResult !is com.kulipai.luahook.core.shell.ShellResult.Success) {
                 Toast.makeText(this, "Failed to rename folder", Toast.LENGTH_SHORT).show()
                 return
             }
             
             // Update info.json
             val infoMap = WorkspaceFileManager.readMap(ProjectManager.INFO_JSON)
             val wasEnabled = infoMap[originalProjectName] as? Boolean ?: false
             infoMap.remove(originalProjectName)
             infoMap[newName] = wasEnabled
             WorkspaceFileManager.writeMap(ProjectManager.INFO_JSON, infoMap)
             
             originalProjectName = newName // Update reference
        }
        
        // Handle Icon Update
        var iconValue = currentProject?.icon ?: "icon.png"
        val projectFullPath = "${WorkspaceFileManager.DIR}${WorkspaceFileManager.Project}/$newName"
        
        if (selectedIconPath != null) {
                try {
                    val inputStream = contentResolver.openInputStream(Uri.parse(selectedIconPath))
                    val destIcon = "$projectFullPath/icon.png"
                    // Write to temp then mv/cp
                    val tempFile = File(externalCacheDir, "temp_icon_update.png")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val script = "cat \"${tempFile.absolutePath}\" > \"$destIcon\"\nchmod 666 \"$destIcon\""
                    com.kulipai.luahook.core.shell.ShellManager.shell(script)
                    iconValue = "icon.png"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
        } else if (selectedIconUnicode != null) {
                val bmp = textToBitmap(selectedIconUnicode!!)
                val destIcon = "$projectFullPath/icon.png" 
                val tempFile = File(externalCacheDir, "temp_icon_unicode_update.png")
                try {
                    tempFile.outputStream().use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                     val script = "cat \"${tempFile.absolutePath}\" > \"$destIcon\"\nchmod 666 \"$destIcon\""
                    com.kulipai.luahook.core.shell.ShellManager.shell(script)
                    
                    // We also update init.lua to use the Unicode CHAR? 
                    // Or keep using icon.png that we generated?
                    // Previous logic in "Create" allowed passing Unicode.
                    // If we want consistency, maybe store Unicode in init.lua but also save png for ProjectAdapter fallback?
                    // ProjectAdapter prefers unicode if it looks like unicode.
                    // Let's store unicode in init.lua for symbol, and generated png for file manager.
                    iconValue = selectedIconUnicode!!
                } catch(e: Exception) { e.printStackTrace() }
        }

        val newAuthor = binding.etProjectAuthor.text.toString().trim()
        val newLauncher = binding.etProjectLauncher.text.toString().trim()

        // Re-generate init.lua
        // We preserve author
        val scopeLua = if (selectedScope.contains("all")) "\"all\"" else {
            "{\n" + selectedScope.joinToString(",\n") { "    \"$it\"" } + "\n}"
        }
        
        val newInitLua = """
name = "$newName"
description = "$newDesc"
author = "$newAuthor"
icon = "$iconValue"
launcher = "$newLauncher"
scope = ${scopeLua.trimIndent()}
""".trimIndent()

        WorkspaceFileManager.write("${WorkspaceFileManager.Project}/$newName/init.lua", newInitLua)
        
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    // ... Duplicated helper methods from CreateProjectActivity (updateScopeChips, showSymbolDialog, textToBitmap...)
    // Ideally these should be in a shared Mixin or Helper but I will duplicate for speed as they are private logic
    
    private fun updateScopeChips() {
        binding.chipGroupScope.removeAllViews()
        for (pkg in selectedScope) {
            val chip = com.google.android.material.chip.Chip(this)
            chip.text = if (pkg == "all") "All Apps" else pkg
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                selectedScope.remove(pkg)
                updateScopeChips()
            }
            binding.chipGroupScope.addView(chip)
        }
    }
    
    private fun showSymbolSelectionDialog() {
         // Use custom layout to avoid conflicting with other modules
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_glyph_picker, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_glyphs)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        
        // Dense Grid: 6 columns
        recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 6)
        
        // Use local Adapter class to avoid global namespace pollution if possible, or rename
        val adapter = CreateProjectActivity.ProjectGlyphAdapter { symbol ->
            selectedIconUnicode = symbol
            selectedIconPath = null
            
            // Render
            val font = try {
                androidx.core.content.res.ResourcesCompat.getFont(this, R.font.material_symbols)
            } catch (e: Exception) { null }
            
            binding.imgProjectIcon.setImageBitmap(textToBitmap(symbol, font))
            // Dismiss dialog handled via captured reference
        }
        recyclerView.adapter = adapter

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Symbol")
            .setView(dialogView)
            .setPositiveButton("Cancel", null)
            .create()

        adapter.onItemClick = { symbol ->
             selectedIconUnicode = symbol
             selectedIconPath = null
             val font = try {
                 androidx.core.content.res.ResourcesCompat.getFont(this, R.font.material_symbols)
             } catch (e: Exception) { null }
             
             binding.imgProjectIcon.setImageBitmap(textToBitmap(symbol, font))
             dialog.dismiss()
        }

        dialog.show()
        
        // Async Load
        Thread {
            val validSymbols = mutableListOf<String>()
            val paint = android.graphics.Paint()
            try {
                val typeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.material_symbols)
                paint.typeface = typeface
                for (code in 0xe000..0xeFFF) {
                    val str = String(Character.toChars(code))
                    if (paint.hasGlyph(str)) {
                        validSymbols.add(str)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                adapter.setData(validSymbols)
            }
        }.start()
    }
     
    private fun showIconSelectionDialog() {
        val options = arrayOf("Select from Gallery", "Select Symbol")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Choose Icon")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickIcon.launch("image/*")
                    1 -> showSymbolSelectionDialog()
                }
            }
            .show()
    }
    
    private fun showAddScopeDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = "com.example.package"
        val layout = com.google.android.material.textfield.TextInputLayout(this)
        layout.addView(input)
        layout.setPadding(32, 16, 32, 0)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Add Package Scope")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty() && !selectedScope.contains(text)) {
                    // Remove 'all' if adding specific
                    if (selectedScope.contains("all")) selectedScope.remove("all")
                    selectedScope.add(text)
                    updateScopeChips()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun textToBitmap(text: String, typeface: android.graphics.Typeface? = null): android.graphics.Bitmap {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 100f
        
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        paint.color = typedValue.data
        
        paint.textAlign = android.graphics.Paint.Align.LEFT
        if (typeface != null) paint.typeface = typeface
        
        val baseline = -paint.ascent() 
        val width = (paint.measureText(text) + 0.5f).toInt().coerceAtLeast(1)
        val height = (baseline + paint.descent() + 0.5f).toInt().coerceAtLeast(1)
        val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(image)
        canvas.drawText(text, 0f, baseline, paint)
        return image
    }

}
