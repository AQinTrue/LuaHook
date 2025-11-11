package io.dingyi222666.sora.lua.ai

import android.R
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

data class DiffPatch(
    val patches: List<Patch>,
    val displayText: String
) {
    data class Patch(
        val oldText: String,
        val newText: String
    ) {
        fun apply(editor: Content) {

            val startIndex = editor.indexOf(oldText)

            println("$oldText $newText")
            if (startIndex == -1) {
                editor.insert(editor.cursor.leftLine, editor.cursor.leftColumn, newText)
                return
            }

            val endIndex = startIndex + oldText.length


            val indexer = editor.indexer
            val startPos = indexer.getCharPosition(startIndex)
            val endPos = indexer.getCharPosition(endIndex)


            editor.replace(startPos.line, startPos.column, endPos.line, endPos.column, newText)

        }


    }

    fun apply(editor: Content) {
        editor.beginBatchEdit()
        try {
            patches.forEach { patch ->
                patch.apply(editor)
            }
        } finally {
            editor.endBatchEdit()
        }
    }
}