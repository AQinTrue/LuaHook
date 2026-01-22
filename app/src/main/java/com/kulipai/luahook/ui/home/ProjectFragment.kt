package com.kulipai.luahook.ui.home

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.kulipai.luahook.core.project.ProjectManager
import com.kulipai.luahook.core.base.BaseFragment
import com.kulipai.luahook.databinding.FragmentHomeProjectBinding
import com.kulipai.luahook.ui.project.create.CreateProjectActivity
import com.kulipai.luahook.ui.project.editor.ProjectEditorActivity

class ProjectFragment: BaseFragment<FragmentHomeProjectBinding>() {
    
    private lateinit var adapter: ProjectAdapter
    private var isFabOpen = false

    private val pickFileLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val success = ProjectManager.importProject(requireContext(), it)
                if (success) {
                    Toast.makeText(requireContext(), "Import Success", Toast.LENGTH_SHORT).show()
                    loadProjects()
                } else {
                    Toast.makeText(requireContext(), "Import Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
            },
            onProjectLongClick = { project ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除项目")
                    .setMessage("确定要删除项目 ${project.name} 吗？此操作不可恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        try {
                            ProjectManager.deleteProject(project.name)
                            loadProjects()
                             Toast.makeText(requireContext(), "已删除 ${project.name}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
        binding.projectRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.projectRecyclerView.adapter = adapter
    }
    
    override fun initEvent() {
        binding.fab.setOnClickListener {
            toggleFab()
        }

        binding.fabCreateProject.setOnClickListener {
            toggleFab()
            val intent = Intent(requireContext(), CreateProjectActivity::class.java)
            startActivity(intent)
        }

        binding.fabImportProject.setOnClickListener {
            toggleFab()
            pickFileLauncher.launch(arrayOf("*/*"))
        }
    }
    
    private fun toggleFab() {
        val rotateAnimator = ObjectAnimator.ofFloat(
            binding.fab,
            "rotation",
            binding.fab.rotation,
            binding.fab.rotation + 45f
        )
        rotateAnimator.duration = 300
        rotateAnimator.start()

        if (isFabOpen) {
            hideFabWithAnimation(binding.fabCreateProject)
            hideFabWithAnimation(binding.fabImportProject, 300)
        } else {
            showFabWithAnimation(binding.fabImportProject)
            showFabWithAnimation(binding.fabCreateProject, 350)
        }
        isFabOpen = !isFabOpen
    }

    private fun showFabWithAnimation(fab: View, time: Long = 300) {
        if (fab.isVisible && fab.alpha == 1f && fab.scaleX == 1f) return

        fab.alpha = 0f
        fab.scaleX = 0f
        fab.scaleY = 0f
        fab.visibility = View.VISIBLE

        val scaleXAnimator = ObjectAnimator.ofFloat(fab, "scaleX", 0f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(fab, "scaleY", 0f, 1f)
        val alphaAnimator = ObjectAnimator.ofFloat(fab, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
            duration = time
            interpolator = AccelerateDecelerateInterpolator()
        }.start()
    }

    private fun hideFabWithAnimation(fab: View, time: Long = 250) {
        if (fab.isInvisible && fab.alpha == 0f && fab.scaleX == 0f) return

        val scaleXAnimator = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f)
        val scaleYAnimator = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
        val alphaAnimator = ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f)

        AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
            duration = time
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    fab.visibility = View.GONE
                }
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }.start()
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }
    
    private fun loadProjects() {
        val projects = ProjectManager.getProjects()
        adapter.updateData(projects)
    }
}
