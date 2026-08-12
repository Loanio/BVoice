package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSelectorTest {
    @Test
    fun `exact 11_8_3 profile is selected only when its transport class exists`() {
        val profile = Breeno1183Profile()
        val selector = ProfileSelector(listOf(profile))

        val result = selector.select(
            packageVersion = "11.8.3",
            classProbe = ClassProbe { it == Breeno1183Profile.REAL_WEB_SOCKET_CLASS }
        )

        assertEquals(ProfileSelection.Selected(profile), result)
    }

    @Test
    fun `exact 12_9_9 profile is selected only when its transport class exists`() {
        val profile = Breeno1299Profile()
        val selector = ProfileSelector(listOf(Breeno1183Profile(), profile))

        val result = selector.select(
            packageVersion = "12.9.9",
            classProbe = ClassProbe { it == profile.transport.className }
        )

        assertEquals(ProfileSelection.Selected(profile), result)
    }

    @Test
    fun `unsupported version is not hooked`() {
        val result = ProfileSelector(listOf(Breeno1183Profile(), Breeno1299Profile())).select(
            packageVersion = "12.9.10",
            classProbe = ClassProbe { true }
        )

        assertEquals(ProfileSelection.Unsupported("12.9.10"), result)
    }

    @Test
    fun `ambiguous matching profiles are rejected instead of guessing`() {
        val profiles = listOf(
            FakeProfile("first", "11.8.3"),
            FakeProfile("second", "11.8.3")
        )

        val result = ProfileSelector(profiles).select("11.8.3", ClassProbe { true })

        assertEquals(ProfileSelection.Ambiguous(listOf("first", "second")), result)
    }

    @Test
    fun `11_8_3 declares transport fallback but leaves unverified original player disabled`() {
        val capabilities = Breeno1183Profile().capabilities

        assertTrue(capabilities.transportFallback)
        assertFalse(capabilities.originalPlayer)
        assertFalse(capabilities.businessTtsEntry)
        assertTrue(capabilities.reason.isNotBlank())
    }

    @Test
    fun `12_9_9 declares transport fallback but leaves unverified capabilities disabled`() {
        val capabilities = Breeno1299Profile().capabilities

        assertTrue(capabilities.transportFallback)
        assertFalse(capabilities.originalPlayer)
        assertFalse(capabilities.businessTtsEntry)
        assertFalse(capabilities.settingsInjection)
        assertTrue(capabilities.reason.isNotBlank())
    }

    private data class FakeProfile(
        override val id: String,
        private val version: String
    ) : VersionProfile {
        override val capabilities = HookCapabilities(transportFallback = true)
        override val transport = verifiedRealWebSocketTransport
        override fun matches(packageVersion: String, classProbe: ClassProbe): Boolean =
            packageVersion == version
    }
}
