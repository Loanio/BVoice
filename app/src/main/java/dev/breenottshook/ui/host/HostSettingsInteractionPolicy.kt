package dev.breenottshook.ui.host

import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.ui.SettingsOperation
import dev.breenottshook.ui.SettingsUiState

/** Small, UI-free decisions shared by the host's embedded settings page. */
internal object HostSettingsInteractionPolicy {
    const val initialCheckDelayMillis = 250L
    const val autoSaveDelayMillis = 500L

    fun selectCatalogVoice(
        catalog: CharacterCatalog,
        configuredCharacter: String,
        configuredEmotion: String
    ): HostVoiceSelection {
        val character = configuredCharacter.takeIf { it in catalog.characters }
            ?: catalog.characters.keys.firstOrNull().orEmpty()
        val emotions = catalog.characters[character].orEmpty()
        val emotion = configuredEmotion.takeIf { it in emotions }
            ?: emotions.firstOrNull().orEmpty()
        return HostVoiceSelection(character, emotion)
    }

    fun selectInitialCatalogVoice(
        catalog: CharacterCatalog,
        draft: TtsConfig
    ): HostVoiceSelection = selectCatalogVoice(
        catalog,
        draft.character,
        draft.emotion
    )

    fun seedManualVoice(
        manualCharacter: String,
        manualEmotion: String,
        currentCharacter: String,
        currentEmotion: String
    ): HostVoiceSelection = HostVoiceSelection(
        character = manualCharacter.ifBlank { currentCharacter },
        emotion = manualEmotion.ifBlank { currentEmotion }
    )
}

internal data class HostVoiceSelection(
    val character: String,
    val emotion: String
)

internal data class HostSettingsPresentation(
    val controlsEnabled: Boolean,
    val previewEnabled: Boolean,
    val previewLabel: String
) {
    companion object {
        fun from(state: SettingsUiState): HostSettingsPresentation = HostSettingsPresentation(
            controlsEnabled = state.operation == SettingsOperation.IDLE && !state.isBusy,
            previewEnabled = state.operation == SettingsOperation.IDLE || state.isPreviewing,
            previewLabel = if (state.isPreviewing) "停止试听" else "试听当前音色"
        )
    }
}
