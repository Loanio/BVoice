package dev.breenottshook.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsUtteranceTest {
    @Test
    fun `splits Chinese punctuation boundaries and retains punctuation`() {
        assertEquals(
            listOf(
                TtsUtterance(0, "你好。"),
                TtsUtterance(1, "世界！"),
                TtsUtterance(2, "真的？")
            ),
            splitUtterances(listOf("你好。世界！真的？"))
        )
    }

    @Test
    fun `splits newline boundaries`() {
        assertEquals(
            listOf(TtsUtterance(0, "第一行"), TtsUtterance(1, "第二行")),
            splitUtterances(listOf("第一行\n第二行"))
        )
    }

    @Test
    fun `joins chunks before splitting so source order is preserved`() {
        assertEquals(
            listOf(TtsUtterance(0, "你好。"), TtsUtterance(1, "世界！")),
            splitUtterances(listOf("你", "好。世", "界！"))
        )
    }

    @Test
    fun `ignores empty chunks and does not emit blank utterances`() {
        assertEquals(
            listOf(TtsUtterance(0, "你好。")),
            splitUtterances(listOf("", "  ", "\n", "你好。", "\n\n"))
        )
    }
}
