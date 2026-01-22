package com.kulipai.luahook.ui.script.selector

import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.kulipai.luahook.R
import com.kulipai.luahook.app.MyApplication
import com.kulipai.luahook.core.base.BaseActivity
import com.kulipai.luahook.core.file.WorkspaceFileManager
import com.kulipai.luahook.core.xposed.XposedScope
import com.kulipai.luahook.data.model.AppInfo
import com.kulipai.luahook.databinding.ActivitySelectAppsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SelectApps : BaseActivity<ActivitySelectAppsBinding>() {

    private var selectApps = mutableListOf<String>()
    private var searchJob: Job? = null
    private lateinit var allApps: List<AppInfo>
    private lateinit var availableAppsToShow: List<AppInfo>
    private lateinit var adapter: SelectAppsAdapter
    private var isLoaded = false
    private var showSystemApps = false

    override fun inflateBinding(inflater: LayoutInflater): ActivitySelectAppsBinding {
        return ActivitySelectAppsBinding.inflate(inflater)
    }

    override fun initView() {
        DynamicColors.applyToActivityIfAvailable(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        binding.rec.layoutManager = LinearLayoutManager(this)

        binding.rec.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.top = (88 * resources.displayMetrics.density).toInt()
                }
            }
        })
    }

    override fun initData() {
        val selectedPackageNames = WorkspaceFileManager.readStringList("/apps.txt")
        selectApps = selectedPackageNames.toMutableList()

        adapter = SelectAppsAdapter(emptyList(), this, selectApps)
        binding.rec.adapter = adapter

        val app = application as MyApplication
        lifecycleScope.launch {
            allApps = app.getAppListAsync()
            refreshAppList()
            isLoaded = true
        }
    }

    override fun initEvent() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.searchbar.setOnClickListener {
            binding.searchBarTextView.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBarTextView, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.searchBarTextView.doAfterTextChanged { s ->
            searchJob?.cancel()
            searchJob = CoroutineScope(Dispatchers.Main).launch {
                if (isLoaded) {
                    delay(100)
                    filterAppList(s.toString().trim())
                }
            }
        }

        binding.clearText.setOnClickListener {
            binding.searchBarTextView.setText("")
            binding.clearText.visibility = View.INVISIBLE
        }

        binding.fab.setOnClickListener {
            val selectedPackageNames = WorkspaceFileManager.readStringList("/apps.txt")
            WorkspaceFileManager.writeStringList("/apps.txt", selectApps)
            XposedScope.requestManyScope(
                this,
                (selectApps - selectedPackageNames.toSet()).toMutableList(),
                0
            )
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun refreshAppList() {
        val selectedPackagesSet = selectApps.toSet()
        availableAppsToShow = allApps.filter { appInfo ->
            !selectedPackagesSet.contains(appInfo.packageName) &&
                    (showSystemApps || !isSystemApp(appInfo))
        }
        adapter.updateData(availableAppsToShow)
    }

    private fun isSystemApp(appInfo: AppInfo): Boolean {
        return try {
            val pm = packageManager
            val app = pm.getApplicationInfo(appInfo.packageName, 0)
            (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun filterAppList(query: String) {
        val filteredList = if (query.isEmpty()) {
            binding.clearText.visibility = View.INVISIBLE
            availableAppsToShow
        } else {
            binding.clearText.visibility = View.VISIBLE
            availableAppsToShow.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filteredList)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_select_apps, menu)
        menu?.findItem(R.id.action_show_system)?.isChecked = showSystemApps
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_show_system -> {
                showSystemApps = !showSystemApps
                item.isChecked = showSystemApps
                refreshAppList()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
