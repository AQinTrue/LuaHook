package com.kulipai.luahook.ui.script.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doBeforeTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kulipai.luahook.R
import com.kulipai.luahook.ui.script.editor.app.AppsEdit
import com.kulipai.luahook.core.file.WorkspaceFileManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

class MultiScriptActivity : AppCompatActivity() {



    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val rec: RecyclerView by lazy { findViewById(R.id.rec) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }


    private lateinit var adapter: MultScriptAdapter
    private lateinit var currentPackageName: String
    private lateinit var appName: String
    lateinit var scriptList: MutableList<MutableMap.MutableEntry<String, Any?>>


    val launcher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            lifecycleScope.launch {
                scriptList = readConf()
                adapter.updateData(scriptList)
            }
        }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_multi_script)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        //接收传递信息
        val intent = getIntent()
        if (intent != null) {
            currentPackageName = intent.getStringExtra("packageName").toString()
            appName = intent.getStringExtra("appName").toString()
            toolbar.title = appName +" "+ resources.getString(R.string.script_manage)
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }


        scriptList = readConf()

        rec.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        adapter = MultScriptAdapter(scriptList, currentPackageName, appName, this, launcher)
        rec.adapter = adapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, // 不支持拖拽移动
            ItemTouchHelper.LEFT // 允许向左滑动
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // 如果不需要拖拽排序，返回 false
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                val position = viewHolder.bindingAdapterPosition
                // 执行删除操作
                // 假设您的适配器有一个删除方法
                adapter.removeItem(position,this@MultiScriptActivity)
                // removeItem 方法内部会调用 notifyItemRemoved(position) 来更新 UI
            }

            @SuppressLint("ResourceType", "UseCompatLoadingForDrawables")
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                // 可选：在这里绘制滑动时的背景、图标等
                // 例如，您可以在滑动时显示一个红色的背景和删除图标
                val itemView = viewHolder.itemView
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val p = Paint()
                    p.color = getDynamicColor(this@MultiScriptActivity, androidx.appcompat.R.attr.colorError)
                    val background = RectF(
                        itemView.right + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat()
                    )
                    c.drawRect(background, p)

                    // 绘制删除图标（示例，您可能需要加载实际的 Drawable）
                    val deleteIcon = getDrawable(R.drawable.delete_24px)
                    val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconBottom = iconTop + deleteIcon.intrinsicHeight
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin

                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    deleteIcon.setTint(getDynamicColor(this@MultiScriptActivity,
                        com.google.android.material.R.attr.colorOnError))
                    deleteIcon.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(rec)




        fab.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_two_edit, null)
            val inputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
            val edit = view.findViewById<TextInputEditText>(R.id.edit)
            val inputLayout2 = view.findViewById<TextInputLayout>(R.id.text_input_layout2)
            val edit2 = view.findViewById<TextInputEditText>(R.id.edit2)
            inputLayout.hint = resources.getString(R.string.script_name)
            inputLayout2.hint = resources.getString(R.string.description)
            edit.doBeforeTextChanged { text, start, count, after ->
                // text: 改变前的内容
                // start: 改变开始的位置
                // count: 将被替换的旧内容长度
                // after: 新内容长度
                edit.error = null

            }
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.new_script))
                .setView(view)
                .setPositiveButton(resources.getString(R.string.sure), { dialog, which ->

                    if (edit.text.isNullOrEmpty()) {
                        edit.error = resources.getString(R.string.input_sth)
                    } else if (scriptList.any { entry -> entry.key == edit.text.toString() }) {
                        edit.error = resources.getString(R.string.existed)
                    } else {
                        // TODO 内容过长判断
                        createScript(edit.text.toString(), edit2.text.toString())
                    }
                })
                .setNegativeButton(resources.getString(R.string.cancel), { dialog, which ->
                    dialog.dismiss()
                })
                .show()


        }


    }


    // TODO)) 封装
    fun readConf(): MutableList<MutableMap.MutableEntry<String, Any?>> {
        val path = WorkspaceFileManager.AppConf + "/" + currentPackageName + ".txt"
        val list = WorkspaceFileManager.readMap(path).entries.toMutableList()
        transformBooleanValuesToJsonArrayInMaps(list)
        return list

    }

    // TODO)) 封装
    fun createScript(name: String, description: String) {
        // 写配置
        val path = WorkspaceFileManager.AppConf + "/" + currentPackageName + ".txt"
        val map = WorkspaceFileManager.readMap(path)
        map[name] = arrayOf<Any?>(true, description, "v1.0")
        WorkspaceFileManager.writeMap(path, map)
        WorkspaceFileManager.ensureDirectoryExists(WorkspaceFileManager.DIR + "/" + WorkspaceFileManager.AppScript + "/" + currentPackageName)

        // 进入编辑界面
        val intent = Intent(this, AppsEdit::class.java)
        intent.putExtra("packageName", currentPackageName)
        intent.putExtra("appName", appName)
        intent.putExtra("scripName", name)
        intent.putExtra("scriptDescription", description)
        launcher.launch(intent)


    }


    fun getDynamicColor(context: Context, @AttrRes colorAttributeResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(colorAttributeResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }
    fun transformBooleanValuesToJsonArrayInMaps(
        dataList: MutableList<MutableMap.MutableEntry<String, Any?>>
    ) {
        for (entry in dataList) {
            entry.key      // 获取 Entry 的 key
            val value = entry.value  // 获取 Entry 的 value

            if (value is Boolean) {
                try {
                    // 创建 JSONArray，包含布尔值和另外两个字符串
                    // Kotlin 的 arrayOf(value, "", "v1.0") 会创建 Object[]
                    val newArray = JSONArray(arrayOf<Any?>(value, "", "v1.0"))

                    // 由于 entry 是 MutableMap.MutableEntry，你需要通过它的 setValue 方法来修改值
                    // 或者，如果 MyMutableEntry 是你自定义的可变 Entry 类，可以直接赋值
                    entry.setValue(newArray) // 使用 setValue 方法更新 Entry 的值

                } catch (_: JSONException) {
                    // 如果出现 JSON 相关的异常，这里可以处理
                    // ("Error creating JSONArray for key '$key': ${e.message}")
                } catch (_: Exception) {
                    // 捕获其他可能的异常
                    //  ("Unexpected error for key '$key': ${e.message}")
                }
            }
        }
    }
}