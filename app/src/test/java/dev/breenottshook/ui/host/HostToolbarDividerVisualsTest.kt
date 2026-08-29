package dev.breenottshook.ui.host

import org.junit.Assert.assertEquals
import org.junit.Test

class HostToolbarDividerVisualsTest {
    @Test
    fun `divider colors match the original COUI theme`() {
        assertEquals(0x1f000000, HostToolbarDividerVisuals.color(isNight = false))
        assertEquals(0x33ffffff, HostToolbarDividerVisuals.color(isNight = true))
    }

    @Test
    fun `divider is transparent and inset by 24dp at the top`() {
        val state = HostToolbarDividerVisuals.state(
            scrollOffsetPx = 0,
            fullWidthPx = 1080,
            density = 3f
        )

        assertEquals(0f, state.alpha)
        assertEquals(936, state.widthPx)
    }

    @Test
    fun `divider fades and stretches over the first 50dp of scrolling`() {
        val halfway = HostToolbarDividerVisuals.state(
            scrollOffsetPx = 75,
            fullWidthPx = 1080,
            density = 3f
        )
        val complete = HostToolbarDividerVisuals.state(
            scrollOffsetPx = 150,
            fullWidthPx = 1080,
            density = 3f
        )

        assertEquals(0.5f, halfway.alpha)
        assertEquals(1008, halfway.widthPx)
        assertEquals(1f, complete.alpha)
        assertEquals(1080, complete.widthPx)
    }
}
