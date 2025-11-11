package io.dingyi222666.sora.lua.ai

data class AutocompleteTemplate(
    val template: String,
    val stopSequences: List<String>
) {
    companion object {
        fun getTemplateForModel(): AutocompleteTemplate {
            return AutocompleteTemplate(
                template = buildString {
                    appendLine("You are an AI assistant that completes code by using tools.")
                    appendLine("Analyze the code context below and complete the code at the `{FILL_CODE_HERE}` placeholder.")
                    appendLine("You MUST respond with a call to the `edit_code` tool and nothing else.")

                    appendLine("```json")

                    appendLine("```")
                    appendLine()
                    appendLine("CODE_CONTEXT:")
                    appendLine("```")
                    appendLine("{prefix}{FILL_CODE_HERE}{suffix}")
                    appendLine("```")
                    appendLine()
                    appendLine("INSTRUCTION:")
                    appendLine("Call the `edit_code` tool now. Set `original_code` to \"{FILL_CODE_HERE}\" and `new_code` to your code completion.")
                },
                stopSequences = listOf(")", "}")
            )
        }
    }
}