package dev.breenottshook.ui.host

import dev.breenottshook.config.TtsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostInputValidationTest {
    @Test
    fun `reports malformed concurrency on its field without changing config`() {
        val original = TtsConfig(maxConcurrentSynthesis = 3)

        val result = HostInputValidation.validate(
            original,
            listOf(HostInputValue("maxConcurrentSynthesis", "many"))
        )

        assertEquals(
            HostValidationResult.Invalid("maxConcurrentSynthesis", "请输入整数"),
            result
        )
        assertEquals(3, original.maxConcurrentSynthesis)
    }

    @Test
    fun `reports semantic interval range errors on its field`() {
        val result = HostInputValidation.validate(
            TtsConfig(),
            listOf(HostInputValue("playbackIntervalMs", "5001"))
        )

        assertEquals(
            HostValidationResult.Invalid("playbackIntervalMs", "播放间隔必须位于 0 到 5000 毫秒"),
            result
        )
    }

    @Test
    fun `accepts maximum integer concurrency without clamping`() {
        val result = HostInputValidation.validate(
            TtsConfig(),
            listOf(
                HostInputValue("maxConcurrentSynthesis", Int.MAX_VALUE.toString()),
                HostInputValue("playbackIntervalMs", "450")
            )
        )

        assertTrue(result is HostValidationResult.Valid)
        val config = (result as HostValidationResult.Valid).config
        assertEquals(Int.MAX_VALUE, config.maxConcurrentSynthesis)
        assertEquals(450L, config.playbackIntervalMs)
    }
}
