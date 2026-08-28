package dev.breenottshook.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEntryUpdatePolicyTest {
    @Test
    fun blocksUnderlyingPreferenceUpdatesWhileInjectedEditorIsOpen() {
        assertFalse(NativeEntryUpdatePolicy.shouldUpdate(settingsPageOpen = true))
    }

    @Test
    fun allowsPreferenceUpdatesAfterInjectedEditorCloses() {
        assertTrue(NativeEntryUpdatePolicy.shouldUpdate(settingsPageOpen = false))
    }
}
