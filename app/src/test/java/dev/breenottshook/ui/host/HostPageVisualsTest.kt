package dev.breenottshook.ui.host

import org.junit.Assert.assertEquals
import org.junit.Test

class HostPageVisualsTest {
    @Test
    fun `enter transition starts at the right edge and ends at zero`() {
        assertEquals(1000f, HostPageVisuals.enterTranslation(1000, 0f))
        assertEquals(500f, HostPageVisuals.enterTranslation(1000, 0.5f))
        assertEquals(0f, HostPageVisuals.enterTranslation(1000, 1f))
    }

    @Test
    fun `exit transition starts at zero and ends at the right edge`() {
        assertEquals(0f, HostPageVisuals.exitTranslation(1000, 0f))
        assertEquals(500f, HostPageVisuals.exitTranslation(1000, 0.5f))
        assertEquals(1000f, HostPageVisuals.exitTranslation(1000, 1f))
    }

    @Test
    fun `background fallback follows the current ui mode`() {
        assertEquals(0xfff7f7f7.toInt(), HostPageVisuals.backgroundColor(null, false))
        assertEquals(0xff121212.toInt(), HostPageVisuals.backgroundColor(null, true))
    }

    @Test
    fun `resolved host background wins over fallback`() {
        assertEquals(0xff202124.toInt(), HostPageVisuals.backgroundColor(0xff202124.toInt(), false))
    }
}
