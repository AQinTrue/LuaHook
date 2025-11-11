package io.dingyi222666.sora.lua.ai

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content

interface AICodeCompleter {

    suspend fun singleFileCompletion(
        content: Content,
        position: CharPosition,
    ): List<DiffPatch>
}