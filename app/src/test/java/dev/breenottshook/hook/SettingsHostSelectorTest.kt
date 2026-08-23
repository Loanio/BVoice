package dev.breenottshook.hook

import dev.breenottshook.ui.SettingsSchema
import dev.breenottshook.ui.host.HostFieldFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsHostSelectorTest {
    private data class Node(val title: String, val children: List<Node> = emptyList())
    private class SummaryTarget {
        fun setSummary(value: Int) = value
        fun setSummary(value: CharSequence) = value
    }
    private val verified = SettingsHostDescriptor(
        id = "verified-settings",
        versionName = "11.8.3",
        className = "com.heytap.speechassist.settings.VerifiedSettingsActivity"
    )

    @Test
    fun `available verified class selects one settings host`() {
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
            SettingsHostSelector(listOf(verified)).select("11.9.0", emptySet())
                is SettingsHostSelection.Unavailable
        )
    }

    @Test
    fun `host editor supports exactly the same configuration keys as module app`() {
        assertEquals(SettingsSchema.fields.map { it.key }.toSet(), HostFieldFactory.supportedKeys)
    }

    @Test
    fun `verified main settings activity is selected for both supported versions`() {
        val expectedClasses = mapOf(
            "11.8.3" to "com.heytap.speechassist.home.settings.ui.SettingsActivity",
            "12.9.9" to "com.heytap.speechassist.home.settings.p294ui.SettingsActivity"
        )

        expectedClasses.forEach { (version, expectedClass) ->
            val result = SettingsHostSelector(BreenoSettingsHosts.descriptors).select(
                version,
                setOf(expectedClass)
            )

            val selected = result as SettingsHostSelection.Selected
            assertEquals(expectedClass, selected.descriptor.className)
        }
    }

    @Test
    fun `12 9 9 selects the runtime non obfuscated settings activity when present`() {
        val runtimeClass = "com.heytap.speechassist.home.settings.ui.SettingsActivity"
        val result = SettingsHostSelector(BreenoSettingsHosts.descriptors).select(
            "12.9.9",
            setOf(runtimeClass)
        )

        val selected = result as SettingsHostSelection.Selected
        assertEquals(runtimeClass, selected.descriptor.className)
    }

    @Test
    fun `third party voice entry follows the native voice item`() {
        assertEquals("第三方音色", SettingsPreferenceEntry.title)
        assertEquals("点击配置", SettingsPreferenceEntry.defaultSummary)
        assertEquals(42, SettingsPreferenceEntry.orderAfter(41))
        assertEquals(Int.MAX_VALUE, SettingsPreferenceEntry.orderAfter(Int.MAX_VALUE))
    }

    @Test
    fun `third party voice entry supports English host anchors`() {
        assertTrue(SettingsPreferenceEntry.anchorTitles(Locale.ENGLISH).contains("Voice"))
        assertTrue(SettingsPreferenceEntry.anchorTitles(Locale.ENGLISH).contains("Voice color"))
        assertEquals("Third-party voice", SettingsPreferenceEntry.title(Locale.ENGLISH))
    }

    @Test
    fun `preference traversal finds an item inside a category`() {
        val voice = Node("小布音色")
        val root = Node("root", listOf(Node("个性化设置", listOf(voice))))

        assertEquals(voice, PreferenceTraversal.find(root, Node::children) { it.title == "小布音色" })
        assertEquals(
            "个性化设置",
            PreferenceTraversal.findWithParent(root, Node::children) { it.title == "小布音色" }
                ?.parent?.title
        )
    }

    @Test
    fun `summary reflection selects the text overload`() {
        val method = PreferenceReflection.textSummaryMethod(SummaryTarget::class.java.methods)

        assertEquals(CharSequence::class.java, method?.parameterTypes?.single())
    }
}
