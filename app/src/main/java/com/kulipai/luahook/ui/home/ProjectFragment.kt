package com.kulipai.luahook.ui.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.kulipai.luahook.core.project.ProjectManager
import com.kulipai.luahook.core.base.BaseFragment
import com.kulipai.luahook.databinding.FragmentHomeProjectBinding
import com.kulipai.luahook.ui.project.create.CreateProjectActivity
import com.kulipai.luahook.ui.project.editor.ProjectEditorActivity

class ProjectFragment: BaseFragment<FragmentHomeProjectBinding>() {
    
    private lateinit var adapter: ProjectAdapter

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeProjectBinding {
        return FragmentHomeProjectBinding.inflate(inflater, container, false)
    }
    
    override fun initView() {
        adapter = ProjectAdapter(
            mutableListOf(),
            onProjectClick = { project ->
                 val intent = Intent(requireContext(), ProjectEditorActivity::class.java)
                 intent.putExtra("project_name", project.name)
                 startActivity(intent)
            },
            onProjectToggle = { project, isEnabled ->
                ProjectManager.setProjectEnabled(project.name, isEnabled)
            }
        )
        binding.projectRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.projectRecyclerView.adapter = adapter
    }
    
    override fun initEvent() {
        binding.fabCreateProject.setOnClickListener {
            val intent = Intent(requireContext(), CreateProjectActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadProjects()
    }
    
    private fun loadProjects() {
        // Run on UI thread to be safe although simple list
        val projects = ProjectManager.getProjects()
        // If list is empty, maybe toast or log?
        // To debug:
        // Toast.makeText(requireContext(), "Loaded ${projects.size} projects", Toast.LENGTH_SHORT).show()
        adapter.updateData(projects)
    }
}
