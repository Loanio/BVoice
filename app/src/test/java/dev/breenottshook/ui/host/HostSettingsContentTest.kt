package dev.breenottshook.ui.host

import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.ui.SettingsOperation
import dev.breenottshook.ui.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HostSettingsContentTest {
    @Test
    fun hostActionsFollowTheSharedSettingsOperationState() {
        val checking = HostSettingsPresentation.from(
            SettingsUiState(
                persistedVersion = 1,
                persisted = TtsConfig(),
                draft = TtsConfig(),
                operation = SettingsOperation.TESTING_CONNECTION,
                isBusy = true
            )
        )
        assertFalse(checking.controlsEnabled)
        assertFalse(checking.previewEnabled)

        val previewing = HostSettingsPresentation.from(
            SettingsUiState(
                persistedVersion = 1,
                persisted = TtsConfig(),
                draft = TtsConfig(),
                operation = SettingsOperation.PREVIEWING,
                isPreviewing = true
            )
        )
        assertFalse(previewing.controlsEnabled)
        assertTrue(previewing.previewEnabled)
        assertEquals("停止试听", previewing.previewLabel)
    }

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
    fun catalogSelection_keepsValidVoiceAndFallsBackToFirstAvailableEmotion() {
        val catalog = CharacterCatalog(
            linkedMapOf(
                "小布" to listOf("开心", "平静"),
                "花火" to listOf("默认")
            )
        )

        assertEquals(
            HostVoiceSelection("小布", "平静"),
            HostSettingsInteractionPolicy.selectCatalogVoice(catalog, "小布", "平静")
        )
        assertEquals(
            HostVoiceSelection("小布", "开心"),
            HostSettingsInteractionPolicy.selectCatalogVoice(catalog, "小布", "不存在")
        )
        assertEquals(
            HostVoiceSelection("小布", "平静"),
            HostSettingsInteractionPolicy.selectCatalogVoice(catalog, "不存在", "平静")
        )
    }

    @Test
    fun initialCatalogBind_restoresPersistedDraftWhenSpinnersAreStillEmpty() {
        val catalog = CharacterCatalog(
            linkedMapOf(
                "默认角色" to listOf("默认"),
                "已保存角色" to listOf("开心", "平静")
            )
        )
        val draft = TtsConfig(character = "已保存角色", emotion = "平静")

        assertEquals(
            HostVoiceSelection("已保存角色", "平静"),
            HostSettingsInteractionPolicy.selectInitialCatalogVoice(catalog, draft)
        )
    }

    @Test
    fun embeddedPageInteractionPolicy_defersLoadCheckAndDebouncesAutoSave() {
        assertEquals(250L, HostSettingsInteractionPolicy.initialCheckDelayMillis)
        assertEquals(500L, HostSettingsInteractionPolicy.autoSaveDelayMillis)
    }

    @Test
    fun manualVoiceSeed_preservesExistingValuesAndFillsOnlyTheBlankCounterpart() {
        assertEquals(
            HostVoiceSelection("菲谢尔", "default"),
            HostSettingsInteractionPolicy.seedManualVoice("", "default", "菲谢尔", "开心")
        )
        assertEquals(
            HostVoiceSelection("自定义角色", "开心"),
            HostSettingsInteractionPolicy.seedManualVoice("自定义角色", "", "菲谢尔", "开心")
        )
    }

    @Test
    fun localizedHostLabels_useEnglishWhenHostLocaleIsEnglish() {
        assertEquals("Basic", HostStrings.sectionTitle(dev.breenottshook.ui.SettingsSection.BASIC, Locale.ENGLISH))
        assertEquals("Voice", HostStrings.sectionTitle(dev.breenottshook.ui.SettingsSection.VOICE, Locale.ENGLISH))
        assertEquals("Role", HostStrings.fieldLabel("character", Locale.ENGLISH))
        assertEquals("Emotion", HostStrings.fieldLabel("emotion", Locale.ENGLISH))
        assertEquals("Try current voice", HostStrings.previewLabel(Locale.ENGLISH))
    }
}
