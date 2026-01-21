package com.kulipai.luahook.ui.project.create

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.kulipai.luahook.R
import com.kulipai.luahook.core.base.BaseActivity
import com.kulipai.luahook.core.project.ProjectManager
import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.databinding.ActivityCreateProjectBinding
import com.kulipai.luahook.ui.script.selector.ScopeSelectorActivity
import java.io.File

class CreateProjectActivity : BaseActivity<ActivityCreateProjectBinding>() {

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
                 if (selectedScope.isEmpty()) {
                     binding.tvScope.text = "全部"
                     selectedScope.add("all")
                 } else {
                     binding.tvScope.text = "Selected ${selectedScope.size} apps"
                     // If user explicitly selected apps, we assume they mean specific scope.
                 }
             }
        }
    }

    override fun inflateBinding(inflater: LayoutInflater): ActivityCreateProjectBinding {
        return ActivityCreateProjectBinding.inflate(inflater)
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.tvScope.text = "全部"
        selectedScope.add("all")
    }

    override fun initEvent() {
        binding.btnSelectIcon.setOnClickListener {
            pickIcon.launch("image/*")
        }
        
        binding.btnSelectScope.setOnClickListener {
             val intent = Intent(this, ScopeSelectorActivity::class.java)
             // If we have current selection, pass it.
             // If "all" is in list, we pass empty to indicate no specific selection? 
             // Or clear logic.
             val current = if (selectedScope.contains("all")) ArrayList() else ArrayList(selectedScope)
             intent.putStringArrayListExtra("current_scope", current)
             scopeLauncher.launch(intent)
        }

        binding.btnCreate.setOnClickListener {
            val name = binding.etProjectName.text.toString()
            val desc = binding.etProjectDesc.text.toString()
            val author = getSharedPreferences("conf", MODE_PRIVATE).getString("author", "") ?: ""
            
            if (name.isBlank()) {
                binding.etProjectName.error = resources.getString(R.string.input_id)
                return@setOnClickListener
            }
            
            var cacheIconPath: String? = null
            if (selectedIconPath != null) {
                try {
                    val inputStream = contentResolver.openInputStream(Uri.parse(selectedIconPath))
                    val tempFile = File(externalCacheDir, "temp_icon.png")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    cacheIconPath = tempFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val success = ProjectManager.createProject(
                name = name,
                description = desc,
                author = author,
                iconPath = cacheIconPath,
                iconUnicode = selectedIconUnicode,
                scope = selectedScope
            )
            
            if (success) {
                Toast.makeText(this, resources.getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Created Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
             selectedIconPath = it.toString()
             binding.imgProjectIcon.setImageURI(it)
        }
    }
}


