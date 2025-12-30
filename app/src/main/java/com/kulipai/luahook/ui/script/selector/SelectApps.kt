package com.kulipai.luahook.ui.script.selector

import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.R
import com.kulipai.luahook.app.MyApplication
import com.kulipai.luahook.ui.home.AppInfo
import com.kulipai.luahook.core.file.LShare
import com.kulipai.luahook.core.xposed.XposedScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SelectApps : AppCompatActivity() {

    private var selectApps = mutableListOf<String>()
    private var searchJob: Job? = null
    private lateinit var allApps: List<AppInfo> // 全部 app
    private lateinit var availableAppsToShow: List<AppInfo> // 当前显示 app
    private lateinit var adapter: SelectAppsAdapter
    private var isLoaded = false
    private var showSystemApps = false // ← 是否显示系统应用的开关状态

    private val rec: RecyclerView by lazy { findViewById(R.id.rec) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val searchEdit: EditText by lazy { findViewById(R.id.search_bar_text_view) }
    private val clearImage: ImageView by lazy { findViewById(R.id.clear_text) }
    private val searchbar: MaterialCardView by lazy { findViewById(R.id.searchbar) }
    private val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_select_apps)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        val selectedPackageNames = LShare.readStringList("/apps.txt")
        selectApps = selectedPackageNames.toMutableList()

        adapter = SelectAppsAdapter(emptyList(), this, selectApps)
        rec.layoutManager = LinearLayoutManager(this)
        rec.adapter = adapter

        val app = application as MyApplication
        lifecycleScope.launch {
            allApps = app.getAppListAsync()
            refreshAppList()
            isLoaded = true
        }

        rec.addItemDecoration(object : RecyclerView.ItemDecoration() {
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

        searchbar.setOnClickListener {
            searchEdit.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        searchEdit.doAfterTextChanged { s ->
            searchJob?.cancel()
            searchJob = CoroutineScope(Dispatchers.Main).launch {
                if (isLoaded) {
                    delay(100)
                    filterAppList(s.toString().trim(), clearImage)
                }
            }
        }

        clearImage.setOnClickListener {
            searchEdit.setText("")
            clearImage.visibility = View.INVISIBLE
        }

        fab.setOnClickListener {
            LShare.writeStringList("/apps.txt", selectApps)
            XposedScope.requestManyScope(
                this,
                (selectApps - selectedPackageNames).toMutableList(),
                0
            )
            finish()
        }
    }

    /** 重新根据 showSystemApps 筛选列表 */
    private fun refreshAppList() {
        val selectedPackagesSet = selectApps.toSet()
        availableAppsToShow = allApps.filter { appInfo ->
            !selectedPackagesSet.contains(appInfo.packageName) &&
                    (showSystemApps || !isSystemApp(appInfo))
        }
        adapter.updateData(availableAppsToShow)
    }

    /** 判断是否是系统应用 */
    private fun isSystemApp(appInfo: AppInfo): Boolean {
        return try {
            val pm = packageManager
            val app = pm.getApplicationInfo(appInfo.packageName, 0)
            (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun filterAppList(query: String, clearImage: ImageView) {
        val filteredList = if (query.isEmpty()) {
            clearImage.visibility = View.INVISIBLE
            availableAppsToShow
        } else {
            clearImage.visibility = View.VISIBLE
            availableAppsToShow.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filteredList)
    }

    /** 创建菜单 */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_select_apps, menu)
        menu?.findItem(R.id.action_show_system)?.isChecked = showSystemApps
        return true
    }

    /** 菜单点击 */
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