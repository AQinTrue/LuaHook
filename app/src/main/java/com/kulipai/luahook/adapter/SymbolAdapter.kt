package com.kulipai.luahook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.androlua.LuaEditor
import com.google.android.material.card.MaterialCardView
import com.kulipai.luahook.R
import io.github.rosemoe.sora.widget.CodeEditor

class SymbolAdapter(private val editor: CodeEditor) :
    RecyclerView.Adapter<SymbolAdapter.SymbolViewHolder>() {

    val symbols =
        listOf(
            "log",
            "lp",
            "(",
            ")",
            "[",
            "]",
            "{",
            "}",
            "\"",
            "=",
            ".",
            ",",
            ";",
            "_",
            "+",
            "-",
            "*",
            "/",
            "\\",
            "%",
            "#",
            "^",
            "$",
            "?",
            "!",
            "&",
            "!",
            ":",
            "<",
            ">",
            "~",
            "'",
        )

    inner class SymbolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val symbolTextView: TextView = itemView.findViewById(R.id.symbolTextView) // 使用自定义布局中的 ID
        val symbolItem: MaterialCardView = itemView.findViewById(R.id.symbolItem) // 使用自定义布局中的 ID

        init {
            symbolItem.setOnClickListener {
                val symbol = symbols[bindingAdapterPosition]
                var idx = editor.offsetX+editor.offsetY

                if (editor.isSelected && symbol == "\"") {
                    editor.insertText( symbol,editor.offsetX)
                    editor.insertText(symbol, editor.offsetX)
                }
                when (symbol) {
                    "log" -> {editor.insertText(
                        """log()""",
                        idx

                    )
                        editor.setSelection(editor.offsetX,editor.offsetY)
                    }

                    "lp" -> editor.insertText("lpparam",idx, )
                    else -> editor.insertText(symbol,idx )
                }


                // 在实际应用中，这里会将符号插入到编辑框中
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_symbol, parent, false) // 加载自定义布局
        return SymbolViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SymbolViewHolder, position: Int) {
        holder.symbolTextView.text = symbols[position]
    }

    override fun getItemCount(): Int {
        return symbols.size
    }
}