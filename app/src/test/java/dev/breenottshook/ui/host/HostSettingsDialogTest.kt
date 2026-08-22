package dev.breenottshook.ui.host

import org.junit.Assert.assertEquals
import org.junit.Test

class HostSettingsDialogTest {
    @Test
    fun hostSwitchAnnouncesLabelAndCurrentState() {
        assertEquals(
            "启用第三方 TTS，已开启，双击切换",
            HostFieldFactory.switchContentDescription("启用第三方 TTS", true)
        )
        assertEquals(
            "启用第三方 TTS，已关闭，双击切换",
            HostFieldFactory.switchContentDescription("启用第三方 TTS", false)
        )
    }

    @Test
    fun hostPreviewUsesOneActionLabelForEachState() {
        assertEquals("试听", HostSettingsDialog.previewActionLabel(false))
        assertEquals("停止试听", HostSettingsDialog.previewActionLabel(true))
    }
}
