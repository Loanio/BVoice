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
    fun `unsupported version is not hooked`() {
        val result = ProfileSelector(listOf(Breeno1183Profile())).select(
            packageVersion = "11.9.0",
            classProbe = ClassProbe { true }
        )

        assertEquals(ProfileSelection.Unsupported("11.9.0"), result)
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

    private data class FakeProfile(
        override val id: String,
        private val version: String
    ) : VersionProfile {
        override val capabilities = HookCapabilities(transportFallback = true)
        override fun matches(packageVersion: String, classProbe: ClassProbe): Boolean =
            packageVersion == version
    }
}
