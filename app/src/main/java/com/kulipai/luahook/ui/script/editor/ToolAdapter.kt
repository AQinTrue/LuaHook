package com.kulipai.luahook.ui.script.editor

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kulipai.luahook.R
import com.kulipai.luahook.core.log.d
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.regex.Pattern
import kotlin.text.get

class ToolAdapter(
    private val symbols: List<String>,
    private val editor: CodeEditor,
    private val context: Context
) :
    RecyclerView.Adapter<ToolAdapter.ToolViewHolder>() {


    @SuppressLint("MissingInflatedId")
    inner class ToolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val toolTextView: TextView = itemView.findViewById(R.id.toolTextView) // 使用自定义布局中的 ID
        val toolItem: MaterialCardView = itemView.findViewById(R.id.toolItem) // 使用自定义布局中的 ID

        init {
            toolItem.setOnClickListener {
                val symbol = symbols[bindingAdapterPosition]
                if (editor.isSelected && symbol == "\"") {
//                    editor.insert(editor.selectionStart, symbol)
//                    editor.insert(editor.selectionEnd, symbol)
                }
                when (bindingAdapterPosition) {
                    0 -> {


                        val view = LayoutInflater.from(context)
                            .inflate(R.layout.dialog_hook_code_gen, null)
                        // 获取组件引用
                        val genCodeMenu =
                            view.findViewById<AutoCompleteTextView>(R.id.gen_code_mode_menu)
                        val layoutHookMethod = view.findViewById<View>(R.id.layout_hook_method)
                        val layoutHookCtorMethod =
                            view.findViewById<View>(R.id.layout_hookctor_method)
                        val layoutChangeReturn =
                            view.findViewById<View>(R.id.layout_change_return)
                        val layoutChangeParams =
                            view.findViewById<View>(R.id.layout_change_params)
                        // 定义下拉菜单的选项列表
                        val hookModes = listOf(
                            "Hook方法",
                            "Hook构造方法",
                            "拦截执行",
                            "修改返回值",
                            "修改参数",
//                            "Hook静态变量",
//                            "Hook实例变量",
                        )


                        fun genHookMethod() {

                            val className: String =
                                layoutHookMethod.findViewById<TextInputEditText>(R.id.className).text.toString()
                            val funcName: String =
                                layoutHookMethod.findViewById<TextInputEditText>(R.id.funcName).text.toString()
                            val param: String =
                                layoutHookMethod.findViewById<TextInputEditText>(R.id.param).text.toString()


                            val checkBox_print_all_params: CheckBox =
                                layoutHookMethod.findViewById<CheckBox>(R.id.checkBox_print_all_params)
                            val checkBox_print_return: CheckBox =
                                layoutHookMethod.findViewById<CheckBox>(R.id.checkBox_print_return)
                            val checkBox_print_stack: CheckBox =
                                layoutHookMethod.findViewById<CheckBox>(R.id.checkBox_print_stack)
                            var p = ""
                            if (param.trim().isNotEmpty()) {
                                p = param.split(",")
                                    .joinToString(",") { "\"${it.trim()}\"" } + ",\n"
                            }

                            val hookLua = """
                                    |hook {
                                    |  class = "$className",
                                    |  classloader = lpparam.classLoader,
                                    |  method = "$funcName",
                                    |  params = {${p}},
                                    |  before = function(it)${if (checkBox_print_all_params.isChecked || checkBox_print_return.isChecked || checkBox_print_stack.isChecked) "\n    log('==='..it.method.name..'()===')" else ""}
                                    |    ${if (checkBox_print_all_params.isChecked) "for i=0,#it.args-1 do\n      log('args['..tostring(i)..']='..tostring(it.args[i]))\n    end" else ""}
                                    |  end,
                                    |  after = function(it)
                                    |    ${if (checkBox_print_return.isChecked) "log('return='..tostring(it.result))" else ""}
                                    |    ${if (checkBox_print_stack.isChecked) "printStackTrace()" else ""}
                                    |  end,
                                    |}
                                """.trimMargin()


//                                    "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n${p}function(it)\n\nend,\nfunction(it)\n\nend)"
                            editor.insertText( hookLua,editor.left)
                        }

                        fun genHookCtorMethod() {
                            val className: String =
                                layoutHookCtorMethod.findViewById<TextInputEditText>(R.id.className).text.toString()
                            val param: String =
                                layoutHookCtorMethod.findViewById<TextInputEditText>(R.id.param).text.toString()
                            val checkBox_print_all_params: CheckBox =
                                layoutHookCtorMethod.findViewById<CheckBox>(R.id.checkBox_print_all_params)
                            val checkBox_print_return: CheckBox =
                                layoutHookCtorMethod.findViewById<CheckBox>(R.id.checkBox_print_return)
                            val checkBox_print_stack: CheckBox =
                                layoutHookCtorMethod.findViewById<CheckBox>(R.id.checkBox_print_stack)
                            var p = ""
                            if (param.trim().isNotEmpty()) {
                                p = param.split(",")
                                    .joinToString(",") { "\"${it.trim()}\"" } + ",\n"
                            }

                            val hookLua = """
                                    |hookctor  {
                                    |  class = "$className",
                                    |  classloader = lpparam.classLoader,
                                    |  params = {${p}},
                                    |  before = function(it)${if (checkBox_print_all_params.isChecked || checkBox_print_return.isChecked || checkBox_print_stack.isChecked) "\n    log('==='..it.method.name..'()===')" else ""}
                                    |    ${if (checkBox_print_all_params.isChecked) "for i=0,#it.args-1 do\n      log('args['..tostring(i)..']='..tostring(it.args[i]))\n    end" else ""}
                                    |  end,
                                    |  after = function(it)
                                    |    ${if (checkBox_print_return.isChecked) "log('return='..tostring(it.result))" else ""}
                                    |    ${if (checkBox_print_stack.isChecked) "printStackTrace()" else ""}
                                    |  end,
                                    |}
                                """.trimMargin()
//                                    "hookcotr(\"$className\",\nlpparam.classLoader,\n${p}function(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
                            editor.insertText( hookLua,editor.left)

                        }

                        fun genReplaceMethod() {
                            val className: String =
                                layoutHookMethod.findViewById<TextInputEditText>(R.id.className).text.toString()
                            val funcName: String =
                                layoutHookMethod.findViewById<TextInputEditText>(R.id.funcName).text.toString()
                            val param: String =
                                layoutHookMethod.findViewById<TextInputEditText>(R.id.param).text.toString()
                            val checkBox_print_all_params: CheckBox =
                                layoutHookMethod.findViewById<CheckBox>(R.id.checkBox_print_all_params)
                            val checkBox_print_return: CheckBox =
                                layoutHookMethod.findViewById<CheckBox>(R.id.checkBox_print_return)
                            val checkBox_print_stack: CheckBox =
                                layoutHookMethod.findViewById<CheckBox>(R.id.checkBox_print_stack)
                            var p = ""
                            if (param.trim().isNotEmpty()) {
                                p = param.split(",")
                                    .joinToString(",") { "\"${it.trim()}\"" } + ",\n"
                            }
                            val hookLua = """
                                    |replace {
                                    |  class = "$className",
                                    |  classloader = lpparam.classLoader,
                                    |  method = "$funcName",
                                    |  params = {${p}},
                                    |  replace = function(it)${if (checkBox_print_all_params.isChecked || checkBox_print_return.isChecked || checkBox_print_stack.isChecked) "\n    log('==='..it.method.name..'()===')" else ""}
                                    |    ${if (checkBox_print_all_params.isChecked) "for i=0,#it.args-1 do\n      log('args['..tostring(i)..']='..tostring(it.args[i]))\n    end" else ""}
                                    |    ${if (checkBox_print_return.isChecked) "log('return='..tostring(it.result))" else ""}
                                    |    ${if (checkBox_print_stack.isChecked) "printStackTrace()" else ""}
                                    |  end,
                                    |}
                                """.trimMargin()

//                                    "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n${p}function(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
                            editor.insertText( hookLua,editor.left)

                        }

                        fun genChangeReturn() {
                            val className: String =
                                layoutChangeReturn.findViewById<TextInputEditText>(R.id.className).text.toString()
                            val funcName: String =
                                layoutChangeReturn.findViewById<TextInputEditText>(R.id.funcName).text.toString()
                            val param: String =
                                layoutChangeReturn.findViewById<TextInputEditText>(R.id.param).text.toString()
                            val returns: String =
                                layoutChangeReturn.findViewById<TextInputEditText>(R.id.returns).text.toString()
                            var p = ""
                            if (param.trim().isNotEmpty()) {
                                p = param.split(",")
                                    .joinToString(",") { "\"${it.trim()}\"" } + ",\n"
                            }
                            val hookLua = """
                                    |hook {
                                    |  class = "$className",
                                    |  classloader = lpparam.classLoader,
                                    |  method = "$funcName",
                                    |  params = {${p}},
                                    |  before = function(it)
                                    |
                                    |  end,
                                    |  after = function(it)
                                    |    it.result = $returns
                                    |  end,
                                    |}
                                """.trimMargin()

//                                    "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n${p}function(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
                            editor.insertText( hookLua,editor.left)

                        }

                        fun genChangeParams() {
                            val className: String =
                                layoutChangeParams.findViewById<TextInputEditText>(R.id.className).text.toString()
                            val funcName: String =
                                layoutChangeParams.findViewById<TextInputEditText>(R.id.funcName).text.toString()
                            val param: String =
                                layoutChangeParams.findViewById<TextInputEditText>(R.id.param).text.toString()
                            val params: String =
                                layoutChangeParams.findViewById<TextInputEditText>(R.id.params).text.toString()
                            var p = ""
                            if (param.trim().isNotEmpty()) {
                                p = param.split(",")
                                    .joinToString(",") { "\"${it.trim()}\"" } + ",\n"
                            }
                            var ps = "" // change params code list
                            if (params.trim().isNotEmpty()) {
                                ps = params.split(",").map { it.trim() }
                                    .mapIndexed { index, element ->
                                        // 拼接成您需要的格式
                                        "    it.args[$index] = $element"
                                    }.joinToString("\n")

                            }
                            val hookLua = """
                                    |hook {
                                    |  class = "$className",
                                    |  classloader = lpparam.classLoader,
                                    |  method = "$funcName",
                                    |  params = {${p}},
                                    |  before = function(it)
                                    |$ps
                                    |  end,
                                    |  after = function(it)
                                    |
                                    |  end,
                                    |}
                                """.trimMargin()

//                                    "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n${p}function(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
                            editor.insertText( hookLua,editor.left)

                        }


                        // 当前模式
                        var mode = 0


                        fun updateContentBasedOnMode(mode: Int) {
                            // 隐藏所有布局
                            layoutHookMethod.visibility = View.GONE
                            layoutHookCtorMethod.visibility = View.GONE
                            layoutChangeReturn.visibility = View.GONE
                            layoutChangeParams.visibility = View.GONE


                            when (mode) {
                                //"Hook方法"
                                0 -> {
                                    layoutHookMethod.visibility = View.VISIBLE
                                }
                                //"Hook构造方法"
                                1 -> {
                                    layoutHookCtorMethod.visibility = View.VISIBLE
                                }
                                //"拦截执行"
                                2 -> {
                                    layoutHookMethod.visibility = View.VISIBLE
                                }
                                // 修改返回值
                                3 -> {
                                    layoutChangeReturn.visibility = View.VISIBLE
                                }
                                // 修改参数
                                4 -> {
                                    layoutChangeParams.visibility = View.VISIBLE
                                }

                            }
                        }

                        // 创建适配器并将其绑定到 AutoCompleteTextView
                        val adapter = ArrayAdapter(
                            context,
                            // 使用 Material 库提供的简单下拉项布局
                            android.R.layout.simple_dropdown_item_1line,
                            hookModes
                        )
                        genCodeMenu.setAdapter(adapter)

                        genCodeMenu.setOnItemClickListener { parent, view, position, id ->
                            //val selectedMode = parent.getItemAtPosition(position).toString()
                            mode = position
                            updateContentBasedOnMode(position)
                        }

                        // 设置默认选中第一个项
                        if (hookModes.isNotEmpty()) {
                            val defaultMode = hookModes[0]
                            genCodeMenu.setText(defaultMode, false) // false 表示不触发下拉

                            // **【新增】初始化时调用，确保显示默认模式的布局**
                            updateContentBasedOnMode(0)
                        }


                        MaterialAlertDialogBuilder(context)
                            .setTitle(context.resources.getString(R.string.gen_hook_code))
                            .setView(view)
                            .setPositiveButton(context.resources.getString(R.string.sure)) { dialog, which ->
                                when (mode) {
                                    0 -> {
                                        genHookMethod()
                                    }

                                    1 -> {
                                        genHookCtorMethod()
                                    }

                                    2 -> {
                                        genReplaceMethod()
                                    }

                                    3 -> {
                                        genChangeReturn()
                                    }

                                    4 -> {
                                        genChangeParams()
                                    }

                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton(context.resources.getString(R.string.cancel)) { dialog, which ->
                                dialog.dismiss()
                            }
                            .show()

/*
//// bottomSheetDialog
//                        val bottomSheetDialog = BottomSheetDialog(context)
//                        bottomSheetDialog.setTitle("Hook方法")
//                        bottomSheetDialog.setContentView(view)
//                        val ok = view.findViewById<MaterialButton>(R.id.ok)
//                        ok.setOnClickListener {
//                            val className: String =
//                                view.findViewById<TextInputEditText>(R.id.className).text.toString()
//                            val funcName: String =
//                                view.findViewById<TextInputEditText>(R.id.funcName).text.toString()
//                            val param: String =
//                                view.findViewById<TextInputEditText>(R.id.param).text.toString()
//                            var p = param.split(",").joinToString(",") { "\"${it.trim()}\"" }
//                            val hookLua =
//                                "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n$p,\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
//                            bottomSheetDialog.dismiss()
//                        }
//                        val cancel = view.findViewById<MaterialButton>(R.id.cancel)
//                        cancel.setOnClickListener {
//                            bottomSheetDialog.dismiss()
//                        }
//                        bottomSheetDialog.show()

 */

                    }

                    /*
//                    1 -> {
//                        val view =
//                            LayoutInflater.from(context).inflate(R.layout.dialog_hookctor, null)
//
////                        val bottomSheetDialog = BottomSheetDialog(context)
////                        bottomSheetDialog.setTitle("Hook构造方法")
////                        bottomSheetDialog.setContentView(view)
////                        val ok = view.findViewById<MaterialButton>(R.id.ok)
////                        ok.setOnClickListener {
////                            val className: String =
////                                view.findViewById<TextInputEditText>(R.id.className).text.toString()
////                            val param: String =
////                                view.findViewById<TextInputEditText>(R.id.param).text.toString()
////                            var p = param.split(",").joinToString(",") { "\"${it.trim()}\"" }
////
////                            val hookLua =
////                                "hookcotr(\"$className\",\nlpparam.classLoader,\n$p,\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
////                            editor.insert(editor.selectionStart, hookLua)
////                            bottomSheetDialog.dismiss()
////                        }
////                        val cancel = view.findViewById<MaterialButton>(R.id.cancel)
////                        cancel.setOnClickListener {
////                            bottomSheetDialog.dismiss()
////                        }
////                        bottomSheetDialog.show()
//                        MaterialAlertDialogBuilder(context)
//                            .setTitle(context.resources.getString(R.string.hook_constructor))
//                            .setView(view)
//                            .setPositiveButton(context.resources.getString(R.string.sure)) { dialog, which ->
//                                val className: String =
//                                    view.findViewById<TextInputEditText>(R.id.className).text.toString()
//                                val param: String =
//                                    view.findViewById<TextInputEditText>(R.id.param).text.toString()
//                                var p = ""
//                                if (param.trim().isNotEmpty()) {
//                                    p = param.split(",")
//                                        .joinToString(",") { "\"${it.trim()}\"" } + ",\n"
//                                }
//
//                                val hookLua = """
//                                    |hookctor  {
//                                    |  class = "$className",
//                                    |  classloader = lpparam.classLoader,
//                                    |  params = {${p}},
//                                    |  before = function(it)
//                                    |
//                                    |  end,
//                                    |  after = function(it)
//                                    |
//                                    |  end,
//                                    |}
//                                """.trimMargin()
////                                    "hookcotr(\"$className\",\nlpparam.classLoader,\n${p}function(it)\n\nend,\nfunction(it)\n\nend)"
//                                editor.insert(editor.selectionStart, hookLua)
//                                dialog.dismiss()
//                            }
//                            .setNegativeButton(context.resources.getString(R.string.cancel)) { dialog, which ->
//                                dialog.dismiss()
//                            }
//                            .show()
//                    }
*/

                    1 -> {

                        val view =
                            LayoutInflater.from(context).inflate(R.layout.dialog_funcsign, null)

                        /*


//                        val bottomSheetDialog = BottomSheetDialog(context)
//                        bottomSheetDialog.setTitle("Hook方法")
//                        bottomSheetDialog.setContentView(view)
//                        val ok = view.findViewById<MaterialButton>(R.id.ok)
//                        ok.setOnClickListener {
//                            val input: String =
//                                view.findViewById<TextInputEditText>(R.id.input).text.toString()
//
//                            var methodInfo: MethodInfo
//                            try {
//                                methodInfo = parseMethodSignature(input)
//
//                            } catch (e: Exception) {
//                                Toast.makeText(context, context.resources.getString(R.string.param_err), Toast.LENGTH_SHORT).show()
//                                return@setOnClickListener
//                            }
//
//
//                            val par = methodInfo.parameterTypes
//                            par.size.toString().d()
//                            var p =
//                                if (par.isEmpty()) "" else "\n" + par.joinToString(",") { "\"${it.trim()}\"" } + ","
//
//                            var hookLua: String
//                            if (methodInfo.methodName == "<init>") {
//                                hookLua =
//                                    "hookcotr(\"${methodInfo.className}\",\nlpparam.classLoader,$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//                            } else {
//                                hookLua =
//                                    "hook(\"${methodInfo.className}\",\nlpparam.classLoader,\n\"${methodInfo.methodName}\",$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//
//                            }
//
//                            editor.insert(editor.selectionStart, hookLua)
//                            bottomSheetDialog.dismiss()
//                        }
//                        val cancel = view.findViewById<MaterialButton>(R.id.cancel)
//                        cancel.setOnClickListener {
//                            bottomSheetDialog.dismiss()
//                        }
//                        bottomSheetDialog.show()
 */
                        MaterialAlertDialogBuilder(context)
                            .setTitle(context.resources.getString(R.string.import_smali))
                            .setView(view)
                            .setPositiveButton(context.resources.getString(R.string.sure)) { dialog, which ->
                                val input: String =
                                    view.findViewById<TextInputEditText>(R.id.input).text.toString()
                                val checkBox_print_all_params: CheckBox =
                                    view.findViewById<CheckBox>(R.id.checkBox_print_all_params)
                                val checkBox_print_return: CheckBox =
                                    view.findViewById<CheckBox>(R.id.checkBox_print_return)
                                val checkBox_print_stack: CheckBox =
                                    view.findViewById<CheckBox>(R.id.checkBox_print_stack)
                                try {
                                    if (input.startsWith("invoke")) {
                                        val invokeInfo = parseDalvikInstruction(input)!!
                                        var invokeLua = ""
                                        if (invokeInfo["methodName"] == "<init>") {
                                            invokeLua = """
                                                imports "${invokeInfo["className"].toString()}"
                                                ${
                                                invokeInfo["className"].toString()
                                                    .substringAfterLast(".")
                                            }()
                                            """.trimIndent()
                                        } else if (input.startsWith("invoke-static")) {
                                            invokeLua = """
                                                imports "${invokeInfo["className"].toString()}"
                                                ${
                                                invokeInfo["className"].toString()
                                                    .substringAfterLast(".")
                                            }.${invokeInfo["methodName"].toString()}()
                                            """.trimIndent()
                                        } else {
                                            invokeLua = """
                                                imports "${invokeInfo["className"].toString()}"
                                                ${
                                                invokeInfo["className"].toString()
                                                    .substringAfterLast(".")
                                            }().${invokeInfo["methodName"].toString()}()
                                            """.trimIndent()
                                        }
//                                        editor.insert(editor.selectionStart, invokeLua)
                                        editor.insertText( invokeLua,editor.left)

                                        dialog.dismiss()

                                    } else {

                                        val methodInfo = parseMethodSignature(input)
                                        val par = methodInfo.parameterTypes
                                        par.size.toString().d()
                                        var p =
                                            if (par.isEmpty()) "" else "\n" + par.joinToString(",") { "\"${it.trim()}\"" } + ","
                                        var hookLua: String =
                                            if (methodInfo.methodName == "<init>") {
                                                """
                                                    |hookctor  {
                                                    |  class = "${methodInfo.className}",
                                                    |  classloader = lpparam.classLoader,
                                                    |  params = {$p},
                                                    |  before = function(it)${if (checkBox_print_all_params.isChecked || checkBox_print_return.isChecked || checkBox_print_stack.isChecked) "\n    log('==='..it.method.name..'()===')" else ""}
                                                    |    ${if (checkBox_print_all_params.isChecked) "for i=0,#it.args-1 do\n      log('args['..tostring(i)..']='..tostring(it.args[i]))\n    end" else ""}
                                                    |  end,
                                                    |  after = function(it)
                                                    |    ${if (checkBox_print_return.isChecked) "log('return='..tostring(it.result))" else ""}
                                                    |    ${if (checkBox_print_stack.isChecked) "printStackTrace()" else ""}
                                                    |  end,
                                                    |}
                                                """.trimMargin()
//                                                "hookcotr(\"${methodInfo.className}\",\nlpparam.classLoader,$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
                                            } else {
                                                """
                                                    |hook {
                                                    |  class = "${methodInfo.className}",
                                                    |  classloader = lpparam.classLoader,
                                                    |  method = "${methodInfo.methodName}",
                                                    |  params = {$p},
                                                    |  before = function(it)${if (checkBox_print_all_params.isChecked || checkBox_print_return.isChecked || checkBox_print_stack.isChecked) "\n    log('==='..it.method.name..'()===')" else ""}
                                                    |    ${if (checkBox_print_all_params.isChecked) "for i=0,#it.args-1 do\n      log('args['..tostring(i)..']='..tostring(it.args[i]))\n    end" else ""}
                                                    |  end,
                                                    |  after = function(it)
                                                    |    ${if (checkBox_print_return.isChecked) "log('return='..tostring(it.result))" else ""}
                                                    |  end,
                                                    |}
                                                """.trimMargin()

//                                                "hook(\"${methodInfo.className}\",\nlpparam.classLoader,\n\"${methodInfo.methodName}\",$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
                                            }
//                                        editor.insert(editor.selectionStart, hookLua)
                                        editor.insertText( hookLua,editor.left)

                                        dialog.dismiss()
                                    }

                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        context.resources.getString(R.string.param_err),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@setPositiveButton
                                }


                            }
                            .setNegativeButton(context.resources.getString(R.string.cancel)) { dialog, which ->
                                dialog.dismiss()
                            }
                            .show()
                        view.findViewById<TextInputEditText>(R.id.input).setText(getClipboardText())
                    }

                    2 -> {

                        /**
                         * 判断当前位置的冒号是否是方法调用的冒号
                         */
                        fun isMethodCallContext(code: String, colonIndex: Int): Boolean {
                            // 冒号前必须是有效的标识符或右括号/右方括号结尾
                            if (colonIndex <= 0) return false

                            // 检查冒号后是否有标识符
                            var hasIdentifierAfterColon = false
                            var j = colonIndex + 1
                            while (j < code.length && (code[j].isWhitespace() || code[j] == '\n')) {
                                j++
                            }

                            // 冒号后必须是有效的标识符开头
                            if (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) {
                                hasIdentifierAfterColon = true
                            } else {
                                return false
                            }

                            // 检查冒号前是否有有效的对象引用（标识符、闭合的括号或方括号）
                            var i = colonIndex - 1
                            // 跳过空白字符
                            while (i >= 0 && (code[i].isWhitespace() || code[i] == '\n')) {
                                i--
                            }

                            if (i < 0) return false

                            // 如果是右括号，判断是否是闭合的
                            if (code[i] == ')') {
                                var parenCount = 1
                                i--
                                while (i >= 0 && parenCount > 0) {
                                    if (code[i] == ')') parenCount++
                                    if (code[i] == '(') parenCount--
                                    i--
                                }
                                return parenCount == 0
                            }

                            // 如果是右方括号，判断是否是闭合的
                            if (code[i] == ']') {
                                var bracketCount = 1
                                i--
                                while (i >= 0 && bracketCount > 0) {
                                    if (code[i] == ']') bracketCount++
                                    if (code[i] == '[') bracketCount--
                                    i--
                                }
                                return bracketCount == 0
                            }

                            // 如果是标识符的一部分
                            if (code[i].isLetterOrDigit() || code[i] == '_') {
                                // 回溯到整个标识符的开头
                                while (i >= 0 && (code[i].isLetterOrDigit() || code[i] == '_')) {
                                    i--
                                }
                                return true
                            }

                            return false
                        }

                        /**
                         * 将Lua代码中的冒号调用转换为点调用
                         */
                        fun convertLuaColonToDot(code: String): String {
                            val result = StringBuilder()
                            var i = 0
                            val length = code.length

                            var inSingleLineComment = false
                            var inMultiLineComment = false
                            var inSingleQuoteString = false
                            var inDoubleQuoteString = false
                            var inMultiLineString = false
                            var multiLineStringLevel = 0

                            while (i < length) {
                                // 处理字符串的开始和结束
                                if (!inSingleLineComment && !inMultiLineComment) {
                                    // 处理单引号字符串
                                    if (code[i] == '\'' && (i == 0 || code[i - 1] != '\\') && !inDoubleQuoteString && !inMultiLineString) {
                                        inSingleQuoteString = !inSingleQuoteString
                                    }

                                    // 处理双引号字符串
                                    if (code[i] == '"' && (i == 0 || code[i - 1] != '\\') && !inSingleQuoteString && !inMultiLineString) {
                                        inDoubleQuoteString = !inDoubleQuoteString
                                    }

                                    // 处理多行字符串开始 [[
                                    if (i < length - 1 && code[i] == '[' && code[i + 1] == '[' && !inSingleQuoteString && !inDoubleQuoteString && !inMultiLineString) {
                                        inMultiLineString = true
                                        multiLineStringLevel = 0
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++
                                        continue
                                    }

                                    // 处理多行字符串结束 ]]
                                    if (i < length - 1 && code[i] == ']' && code[i + 1] == ']' && inMultiLineString) {
                                        inMultiLineString = false
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++
                                        continue
                                    }
                                }

                                // 处理注释
                                if (!inSingleQuoteString && !inDoubleQuoteString && !inMultiLineString && !inSingleLineComment && !inMultiLineComment) {
                                    // 单行注释
                                    if (i < length - 1 && code[i] == '-' && code[i + 1] == '-') {
                                        inSingleLineComment = true
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++

                                        // 检查是否是多行注释的开始 --[[
                                        if (i < length - 2 && code[i] == '[' && code[i + 1] == '[') {
                                            inMultiLineComment = true
                                            inSingleLineComment = false
                                        }

                                        continue
                                    }
                                }

                                // 结束单行注释
                                if (inSingleLineComment && code[i] == '\n') {
                                    inSingleLineComment = false
                                }

                                // 结束多行注释
                                if (inMultiLineComment && i < length - 1 && code[i] == ']' && code[i + 1] == ']') {
                                    inMultiLineComment = false
                                    result.append(code[i])
                                    i++
                                    result.append(code[i])
                                    i++
                                    continue
                                }

                                // 在字符串或注释中，直接复制字符
                                if (inSingleQuoteString || inDoubleQuoteString || inMultiLineString || inSingleLineComment || inMultiLineComment) {
                                    result.append(code[i])
                                    i++
                                    continue
                                }

                                // 处理冒号调用
                                if (i < length - 1 && code[i] == ':' && isMethodCallContext(
                                        code,
                                        i
                                    )
                                ) {
                                    result.append('.')
                                } else {
                                    result.append(code[i])
                                }

                                i++
                            }

                            return result.toString()
                        }


                        /**
                         * 判断当前位置的import是否是一个导入语句
                         * 匹配以下形式：
                         * - import "module"
                         * - import("module")
                         * - import"module"
                         */
                        fun isImportStatement(code: String, importIndex: Int): Boolean {
                            // 检查import前面是否是标识符的一部分
                            if (importIndex > 0) {
                                val prevChar = code[importIndex - 1]
                                if (prevChar.isLetterOrDigit() || prevChar == '_') {
                                    return false // 如果前面是标识符的一部分，则不是独立的import语句
                                }
                            }

                            // 检查import后面的内容
                            var j = importIndex + 6

                            // 跳过空白字符
                            while (j < code.length && code[j].isWhitespace()) {
                                j++
                            }

                            if (j >= code.length) {
                                return false
                            }

                            // 检查是否是 import "module" 或 import("module") 或 import"module" 格式
                            return code[j] == '"' || code[j] == '(' || code[j] == '\''
                        }


                        /**
                         * 将Lua代码中的import语句转换为imports
                         */
                        fun convertImportToImports(code: String): String {
                            val result = StringBuilder()
                            var i = 0
                            val length = code.length

                            var inSingleLineComment = false
                            var inMultiLineComment = false
                            var inSingleQuoteString = false
                            var inDoubleQuoteString = false
                            var inMultiLineString = false

                            while (i < length) {
                                // 处理字符串的开始和结束
                                if (!inSingleLineComment && !inMultiLineComment) {
                                    // 处理单引号字符串
                                    if (code[i] == '\'' && (i == 0 || code[i - 1] != '\\') && !inDoubleQuoteString && !inMultiLineString) {
                                        inSingleQuoteString = !inSingleQuoteString
                                    }

                                    // 处理双引号字符串
                                    if (code[i] == '"' && (i == 0 || code[i - 1] != '\\') && !inSingleQuoteString && !inMultiLineString) {
                                        inDoubleQuoteString = !inDoubleQuoteString
                                    }

                                    // 处理多行字符串开始 [[
                                    if (i < length - 1 && code[i] == '[' && code[i + 1] == '[' && !inSingleQuoteString && !inDoubleQuoteString && !inMultiLineString) {
                                        inMultiLineString = true
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++
                                        continue
                                    }

                                    // 处理多行字符串结束 ]]
                                    if (i < length - 1 && code[i] == ']' && code[i + 1] == ']' && inMultiLineString) {
                                        inMultiLineString = false
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++
                                        continue
                                    }
                                }

                                // 处理注释
                                if (!inSingleQuoteString && !inDoubleQuoteString && !inMultiLineString && !inSingleLineComment && !inMultiLineComment) {
                                    // 单行注释
                                    if (i < length - 1 && code[i] == '-' && code[i + 1] == '-') {
                                        inSingleLineComment = true
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++

                                        // 检查是否是多行注释的开始 --[[
                                        if (i < length - 2 && code[i] == '[' && code[i + 1] == '[') {
                                            inMultiLineComment = true
                                            inSingleLineComment = false
                                        }

                                        continue
                                    }
                                }

                                // 结束单行注释
                                if (inSingleLineComment && code[i] == '\n') {
                                    inSingleLineComment = false
                                }

                                // 结束多行注释
                                if (inMultiLineComment && i < length - 1 && code[i] == ']' && code[i + 1] == ']') {
                                    inMultiLineComment = false
                                    result.append(code[i])
                                    i++
                                    result.append(code[i])
                                    i++
                                    continue
                                }

                                // 在字符串或注释中，直接复制字符
                                if (inSingleQuoteString || inDoubleQuoteString || inMultiLineString || inSingleLineComment || inMultiLineComment) {
                                    result.append(code[i])
                                    i++
                                    continue
                                }

                                // 检查并替换import语句
                                if (i + 6 < length && code.substring(
                                        i,
                                        i + 6
                                    ) == "import" && isImportStatement(code, i)
                                ) {
                                    result.append("imports")
                                    i += 6
                                } else {
                                    result.append(code[i])
                                    i++
                                }
                            }

                            return result.toString()
                        }


                        /**
                         * 将代码中的 it.args[ index] 转换为 it.args[index-1]
                         * 如果 index 已经是 index-1 形式，则保持不变
                         */
                        fun convertItArgsIndex(code: String): String {
                            val result = StringBuilder()
                            var i = 0
                            val length = code.length

                            var inSingleLineComment = false
                            var inMultiLineComment = false
                            var inSingleQuoteString = false
                            var inDoubleQuoteString = false
                            var inMultiLineString = false

                            while (i < length) {
                                // 处理字符串的开始和结束
                                if (!inSingleLineComment && !inMultiLineComment) {
                                    // 处理单引号字符串
                                    if (code[i] == '\'' && (i == 0 || code[i - 1] != '\\') && !inDoubleQuoteString && !inMultiLineString) {
                                        inSingleQuoteString = !inSingleQuoteString
                                    }

                                    // 处理双引号字符串
                                    if (code[i] == '"' && (i == 0 || code[i - 1] != '\\') && !inSingleQuoteString && !inMultiLineString) {
                                        inDoubleQuoteString = !inDoubleQuoteString
                                    }

                                    // 处理多行字符串开始 [[
                                    if (i < length - 1 && code[i] == '[' && code[i + 1] == '[' && !inSingleQuoteString && !inDoubleQuoteString && !inMultiLineString) {
                                        inMultiLineString = true
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++
                                        continue
                                    }

                                    // 处理多行字符串结束 ]]
                                    if (i < length - 1 && code[i] == ']' && code[i + 1] == ']' && inMultiLineString) {
                                        inMultiLineString = false
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++
                                        continue
                                    }
                                }

                                // 处理注释
                                if (!inSingleQuoteString && !inDoubleQuoteString && !inMultiLineString && !inSingleLineComment && !inMultiLineComment) {
                                    // 单行注释
                                    if (i < length - 1 && code[i] == '-' && code[i + 1] == '-') {
                                        inSingleLineComment = true
                                        result.append(code[i])
                                        i++
                                        result.append(code[i])
                                        i++

                                        // 检查是否是多行注释的开始 --[[
                                        if (i < length - 2 && code[i] == '[' && code[i + 1] == '[') {
                                            inMultiLineComment = true
                                            inSingleLineComment = false
                                        }

                                        continue
                                    }
                                }

                                // 结束单行注释
                                if (inSingleLineComment && code[i] == '\n') {
                                    inSingleLineComment = false
                                }

                                // 结束多行注释
                                if (inMultiLineComment && i < length - 1 && code[i] == ']' && code[i + 1] == ']') {
                                    inMultiLineComment = false
                                    result.append(code[i])
                                    i++
                                    result.append(code[i])
                                    i++
                                    continue
                                }

                                // 在字符串或注释中，直接复制字符
                                if (inSingleQuoteString || inDoubleQuoteString || inMultiLineString || inSingleLineComment || inMultiLineComment) {
                                    result.append(code[i])
                                    i++
                                    continue
                                }

                                // 检查是否匹配 it.args[index] 模式
                                if (i + 8 < length && code.substring(i, i + 8) == "it.args[") {
                                    result.append("it.args[")
                                    i += 8

                                    // 记录当前位置，解析索引表达式
                                    val startPos = i
                                    var bracketCount = 1
                                    var hasMinusOne = false

                                    // 找到匹配的右括号
                                    while (i < length && bracketCount > 0) {
                                        if (code[i] == '[') bracketCount++
                                        if (code[i] == ']') bracketCount--
                                        i++
                                    }

                                    if (bracketCount == 0) {
                                        // 提取索引表达式
                                        val indexExpr = code.substring(startPos, i - 1)

                                        // 检查索引是否已经是 index-1 形式
                                        hasMinusOne = indexExpr.contains("-") &&
                                                !indexExpr.contains("--") &&
                                                (indexExpr.trim().endsWith("-1") ||
                                                        indexExpr.matches(Regex(".*-\\s*1\\s*")))

                                        // 如果不是 index-1 形式，则转换
                                        if (!hasMinusOne) {
                                            result.append("$indexExpr-1")
                                        } else {
                                            result.append(indexExpr)
                                        }
                                        result.append("]")
                                    } else {
                                        // 未找到匹配的右括号，回到原始位置
                                        i = startPos
                                        result.append(code[i])
                                        i++
                                    }
                                } else {
                                    result.append(code[i])
                                    i++
                                }
                            }

                            return result.toString()
                        }


                        MaterialAlertDialogBuilder(context)
                            .setTitle(context.resources.getString(R.string.grammer_converse))
                            .setMessage(context.resources.getString(R.string.confirm_converse))
                            .setPositiveButton(context.resources.getString(R.string.sure)) { dialog, which ->
                                editor.setText(
                                    convertLuaColonToDot(
                                        convertImportToImports(
                                            convertItArgsIndex(
                                                editor.text.toString()
                                            )
                                        )
                                    )
                                )
                                dialog.dismiss()
                            }
                            .setNegativeButton(context.resources.getString(R.string.cancel)) { dialog, which ->
                                dialog.dismiss()
                            }
                            .show()


                    }

                    else -> {}
                }


                // 在实际应用中，这里会将符号插入到编辑框中
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tool, parent, false) // 加载自定义布局
        return ToolViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.toolTextView.text = symbols[position]
    }

    override fun getItemCount(): Int {
        return symbols.size
    }


    fun parseDalvikInstruction(instruction: String): Map<String, String>? {
        // 匹配 invoke-direct
        val directPattern =
            Pattern.compile("invoke-direct \\{([^}]+)\\}, L([^;]+);->([^(]+)\\((.*)\\)(.*)\\s*?")
        // 匹配 invoke-virtual
        val virtualPattern =
            Pattern.compile("invoke-virtual \\{([^}]+)\\}, L([^;]+);->([^(]+)\\((.*)\\)(.*)\\s*?")
        // 匹配 invoke-static
        val staticPattern =
            Pattern.compile("invoke-static \\{([^}]+)\\}, L([^;]+);->([^(]+)\\((.*)\\)(.*)\\s*?")

        val directMatcher = directPattern.matcher(instruction)
        if (directMatcher.matches()) {
            return mapOf(
                "instructionType" to "invoke-direct",
                "registers" to directMatcher.group(1), // p1
                "className" to directMatcher.group(2)!!
                    .replace('/', '.'), // Ljava/lang/StringBuilder -> java.lang.StringBuilder
                "methodName" to directMatcher.group(3), // <init>
                "parameters" to directMatcher.group(4), // 参数类型，这里是空
                "returnType" to directMatcher.group(5) // 返回类型，这里是 V
            )
        }

        val virtualMatcher = virtualPattern.matcher(instruction)
        if (virtualMatcher.matches()) {
            return mapOf(
                "instructionType" to "invoke-virtual",
                "registers" to virtualMatcher.group(1), // p1
                "className" to virtualMatcher.group(2)!!
                    .replace('/', '.'), // Landroid/widget/Toast -> android.widget.Toast
                "methodName" to virtualMatcher.group(3), // show
                "parameters" to virtualMatcher.group(4), // 参数类型，这里是空
                "returnType" to virtualMatcher.group(5) // 返回类型，这里是 V
            )
        }

        val staticMatcher = staticPattern.matcher(instruction)
        if (staticMatcher.matches()) {
            return mapOf(
                "instructionType" to "invoke-static",
                "registers" to staticMatcher.group(1), // p1, p2, v0
                "className" to staticMatcher.group(2)!!
                    .replace('/', '.'), // Landroid/widget/Toast -> android.widget.Toast
                "methodName" to staticMatcher.group(3), // makeText
                "parameters" to staticMatcher.group(4), // 参数类型，Landroid/content/Context;Ljava/lang/CharSequence;I
                "returnType" to staticMatcher.group(5) // 返回类型，Landroid/widget/Toast;
            )
        }

        return null // 如果不匹配任何已知模式则返回 null
    }


    fun parseMethodSignature(signature: String): MethodInfo {
        // 正则表达式匹配方法签名
        val regex =
            """L(?<className>[^;]+);->(?<methodName>[^\\(]+)\((?<parameterTypes>.*)\)(?<returnType>.+)""".toRegex()
        val matchResult = regex.matchEntire(signature)

        if (matchResult != null) {
            val classNameWithSlashes = matchResult.groups["className"]?.value ?: ""
            val className = classNameWithSlashes.replace("/", ".")
            val methodName = matchResult.groups["methodName"]?.value ?: ""
            val parameterTypesStr = matchResult.groups["parameterTypes"]?.value ?: ""
            val returnTypeSig = matchResult.groups["returnType"]?.value ?: ""

            val parameterTypes = parseParameterTypes(parameterTypesStr)
            val returnType = parseType(returnTypeSig)

            return MethodInfo(className, methodName, parameterTypes, returnType)
        } else {
            throw IllegalArgumentException("Invalid method signature: $signature")
        }
    }

    fun parseParameterTypes(parameterTypesStr: String): List<String> {
        val types = mutableListOf<String>()
        var i = 0
        while (i < parameterTypesStr.length) {
            val type = parseType(parameterTypesStr.substring(i))
            types.add(type)
            i += getTypeLength(parameterTypesStr.substring(i))
        }
        return types
    }

    fun parseType(typeSig: String): String {
        return when (typeSig.first()) {
            'L' -> {
                val semiIndex = typeSig.indexOf(';')
                if (semiIndex == -1) throw IllegalArgumentException("Invalid object type signature: $typeSig")
                typeSig.substring(1, semiIndex).replace("/", ".")
            }

            '[' -> parseType(typeSig.substring(1)) + "[]"
            'Z' -> "boolean"
            'B' -> "byte"
            'C' -> "char"
            'D' -> "double"
            'F' -> "float"
            'I' -> "int"
            'J' -> "long"
            'S' -> "short"
            'V' -> "void"
            else -> throw IllegalArgumentException("Unknown type signature: $typeSig")
        }
    }

    fun getTypeLength(typeSig: String): Int {
        return when (typeSig.first()) {
            'L' -> typeSig.indexOf(';') + 1
            '[' -> 1 + getTypeLength(typeSig.substring(1))
            else -> 1
        }

    }

    data class MethodInfo(
        val className: String,
        val methodName: String,
        val parameterTypes: List<String>,
        val returnType: String
    )

    private fun getClipboardText(): String {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text
            if (text != null) {
                return text.toString()
            }
        }
        return ""
    }

}