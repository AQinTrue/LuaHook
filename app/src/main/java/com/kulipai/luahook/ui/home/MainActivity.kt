package com.kulipai.luahook.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.kulipai.luahook.app.MyApplication
import com.kulipai.luahook.R
import com.kulipai.luahook.ui.script.editor.app.AppsEdit
import com.kulipai.luahook.ui.script.editor.global.EditActivity
import com.kulipai.luahook.ui.setting.SettingsActivity
import com.kulipai.luahook.databinding.ActivityMainBinding
import com.kulipai.luahook.core.shizuku.ShizukuApi
import com.kulipai.luahook.core.file.LShare
import com.kulipai.luahook.core.language.LanguageUtils
import com.kulipai.luahook.core.shell.ShellManager
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    // TODO)) 用一个object或者viewModel封装一下，在变化的时候响应式变化
    private lateinit var lastLanguage: String
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageUtils.applyLanguage(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)


        registerActivityResult()
        setupToolbar()
        setupInsets()
//        observeViewModel()
        checkLastState()
        initAppData()
        setupViewPager()
        setupBottomBar()
    }

    // --------------------- Toolbar ---------------------

    private fun setupToolbar() {
        binding.toolbar.menu.add(0, 1, 0, getString(R.string.setting))
            .setIcon(R.drawable.settings_24px)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == 1) {
                lastLanguage = LanguageUtils.getCurrentLanguage(this)
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            }
            true
        }
    }

    private fun registerActivityResult() {
        settingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (LanguageUtils.getCurrentLanguage(this) != lastLanguage) recreate()
        }
    }

    // --------------------- ViewModel ---------------------

    fun requestShizuku() {
        if (ShizukuApi.isBinderAvailable.value == true && ShizukuApi.isPermissionGranted.value == false) {
            Shizuku.requestPermission(114514)
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
            if (ShellManager.mode.value != ShellManager.Mode.NONE) {
                val savedList = getSavedAppList()
                if (savedList.isNotEmpty()) {
                    MyApplication.Companion.instance.getAppInfoList(savedList)
                }
            }
        }
    }

    // TODO)) 封装
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
        binding.viewPager2.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment = fragments[position]
            override fun getItemCount() = fragments.size
        }
    }

    private fun setupBottomBar() {

        val menu = binding.bottomBar.menu
        menu.add(Menu.NONE, 0, 0, getString(R.string.home))
            .setIcon(R.drawable.home_24px)
        menu.add(Menu.NONE, 1, 1, getString(R.string.apps))
            .setIcon(R.drawable.apps_24px)
        menu.add(Menu.NONE, 2, 2, getString(R.string.plugins))
            .setIcon(R.drawable.extension_24px)

        binding.bottomBar.setOnItemSelectedListener {
            binding.viewPager2.currentItem = it.itemId
            true
        }

        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomBar.menu[position].isChecked = true
                updateBottomIcons(position)
            }
        })
    }

    private fun updateBottomIcons(position: Int) {
        val menu = binding.bottomBar.menu
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
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            ShizukuApi.isPermissionGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        }

    override fun onStart() {
        super.onStart()
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onStop() {
        super.onStop()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }


}