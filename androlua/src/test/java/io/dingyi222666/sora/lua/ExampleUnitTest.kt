package io.dingyi222666.sora.lua

import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MyCharacter
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)

        val src = """
            local s = a.v()
            import "s.s"
        """.trimIndent()
        val content = Content("pa.s")
        val ref = ContentReference(content)
        val prefix = CompletionHelper.computePrefix(ref, CharPosition(0, 2)) { key: Char ->
            MyCharacter.isJavaIdentifierPart(
                key
            )
        }

        println(prefix)
    }
}