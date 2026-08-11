package dev.breenottshook.hook

import dev.breenottshook.ui.SettingsSchema
import dev.breenottshook.ui.host.HostFieldFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHostSelectorTest {
    private val verified = SettingsHostDescriptor(
        id = "verified-settings",
        versionName = "11.8.3",
        className = "com.heytap.speechassist.settings.VerifiedSettingsActivity"
    )

    @Test
    fun `exact version and available verified class select one settings host`() {
        val result = SettingsHostSelector(listOf(verified)).select(
            versionName = "11.8.3",
            availableClasses = setOf(verified.className)
        )

        assertEquals(SettingsHostSelection.Selected(verified), result)
    }

    @Test
    fun `ambiguous and unsupported hosts are rejected`() {
        val second = verified.copy(id = "second", className = "example.SecondSettingsActivity")
        assertEquals(
            SettingsHostSelection.Ambiguous(listOf("verified-settings", "second")),
            SettingsHostSelector(listOf(verified, second)).select(
                "11.8.3",
                setOf(verified.className, second.className)
            )
        )
        assertTrue(
            SettingsHostSelector(listOf(verified)).select("11.9.0", setOf(verified.className))
                is SettingsHostSelection.Unavailable
        )
    }

    @Test
    fun `host editor supports exactly the same configuration keys as module app`() {
        assertEquals(SettingsSchema.fields.map { it.key }.toSet(), HostFieldFactory.supportedKeys)
    }

    @Test
    fun `unverified 11_8_3 build ships with no guessed settings host`() {
        assertTrue(Breeno1183SettingsHosts.descriptors.isEmpty())
    }
}
