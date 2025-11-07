package com.kulipai.luahook

import AViewModel
import DataRepository.ShellInit
import LanguageUtil
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kulipai.luahook.activity.AppsEdit
import com.kulipai.luahook.activity.EditActivity
import com.kulipai.luahook.activity.SettingsActivity
import com.kulipai.luahook.fragment.AppsFragment
import com.kulipai.luahook.fragment.HomeFragment
import com.kulipai.luahook.fragment.PluginsFragment
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.ShellManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import androidx.core.view.size

class MainActivity : AppCompatActivity() {

    private val bottomBar by lazy { findViewById<BottomNavigationView>(R.id.bottomBar) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val viewPager2 by lazy { findViewById<ViewPager2>(R.id.viewPager2) }
    private val viewModel by viewModels<AViewModel>()

    private lateinit var lastLanguage: String
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>

    private val shizukuRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageUtil.applyLanguage(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        registerActivityResult()
        setupToolbar()
        setupInsets()
        observeViewModel()
        checkLastState()
        initAppData()
        setupViewPager()
        setupBottomBar()
    }

    // --------------------- Toolbar ---------------------

    private fun setupToolbar() {
        toolbar.menu.add(0, 1, 0, getString(R.string.setting))
            .setIcon(R.drawable.settings_24px)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        toolbar.setOnMenuItemClickListener {
            if (it.itemId == 1) {
                lastLanguage = LanguageUtil.getCurrentLanguage(this)
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            }
            true
        }
    }

    private fun registerActivityResult() {
        settingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (LanguageUtil.getCurrentLanguage(this) != lastLanguage) recreate()
        }
    }

    // --------------------- ViewModel ---------------------

    private fun observeViewModel() {
        viewModel.data.observe(this) {
            if (!Shell.isAppGrantedRoot()!! &&
                Shizuku.getBinder() != null &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.addBinderReceivedListener(binderReceivedListener)
                Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
                updatePermissionStatus()
            }
        }
    }

    // --------------------- 状态恢复 ---------------------

    private fun checkLastState() {
        val prefs = getSharedPreferences("status", MODE_PRIVATE)
        when (prefs.getString("current", null)) {
            "apps" -> {
                startActivity(Intent(this, AppsEdit::class.java).apply {
                    putExtra("packageName", prefs.getString("packageName", ""))
                    putExtra("appName", prefs.getString("appName", ""))
                })
            }

            "global" -> startActivity(Intent(this, EditActivity::class.java))
        }
        prefs.edit { putString("current", "null") }
    }

    // --------------------- 数据加载 ---------------------

    private fun initAppData() {
        val app = application as MyApplication
        lifecycleScope.launch {
            app.getAppListAsync()
            if (ShellManager.getMode() != ShellManager.Mode.NONE) {
                val savedList = getSavedAppList()
                if (savedList.isNotEmpty()) {
                    MyApplication.instance.getAppInfoList(savedList)
                }
            }
        }
    }

    private fun getSavedAppList(): MutableList<String> {
        val serialized = LShare.read("/apps.txt")
        return if (serialized.isNotBlank()) serialized.split(",").toMutableList()
        else mutableListOf()
    }

    // --------------------- Insets ---------------------

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, 0)
            insets
        }
    }

    // --------------------- BottomBar + ViewPager ---------------------

    private fun setupViewPager() {
        val fragments = listOf(
            HomeFragment(),
            AppsFragment(),
            PluginsFragment()
        )
        viewPager2.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment = fragments[position]
            override fun getItemCount() = fragments.size
        }
    }

    private fun setupBottomBar() {
        val menu = bottomBar.menu
        menu.add(Menu.NONE, 0, 0, getString(R.string.home))
            .setIcon(R.drawable.home_24px)
        menu.add(Menu.NONE, 1, 1, getString(R.string.apps))
            .setIcon(R.drawable.apps_24px)
        menu.add(Menu.NONE, 2, 2, getString(R.string.plugins))
            .setIcon(R.drawable.extension_24px)

        bottomBar.setOnItemSelectedListener {
            viewPager2.currentItem = it.itemId
            true
        }

        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomBar.menu[position].isChecked = true
                updateBottomIcons(position)
            }
        })
    }

    private fun updateBottomIcons(position: Int) {
        val menu = bottomBar.menu
        val icons = listOf(
            R.drawable.home_24px to R.drawable.home_fill_24px,
            R.drawable.apps_24px to R.drawable.apps_24px,
            R.drawable.extension_24px to R.drawable.extension_fill_24px
        )
        for (i in 0 until menu.size) {
            menu[i].setIcon(if (i == position) icons[i].second else icons[i].first)
        }
    }

    // --------------------- Shizuku 权限逻辑 ---------------------

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { updatePermissionStatus() }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, result ->
            if (requestCode == shizukuRequestCode) {
                val granted = result == PackageManager.PERMISSION_GRANTED
                Toast.makeText(
                    this,
                    if (granted) "Shizuku 权限已授予" else "Shizuku 权限被拒绝",
                    Toast.LENGTH_SHORT
                ).show()
                updatePermissionStatus()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun isShizukuAvailable() = Shizuku.pingBinder()

    private fun checkShizukuPermission(): Boolean {
        return !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private fun requestShizukuPermission() {
        when {
            Shizuku.isPreV11() ->
                Toast.makeText(this, "Shizuku 版本过低，请使用 ADB 启动", Toast.LENGTH_LONG).show()
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                Shizuku.requestPermission(shizukuRequestCode)
        }
    }

    private fun updatePermissionStatus() {
        when {
            !isShizukuAvailable() -> Unit
            checkShizukuPermission() -> {
                ShellInit(applicationContext)
                Sui.init(packageName)
            }
            else -> requestShizukuPermission()
        }
    }
}