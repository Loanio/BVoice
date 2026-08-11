package dev.breenottshook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.ui.components.BooleanSetting
import dev.breenottshook.ui.components.CharacterEmotionPicker
import dev.breenottshook.ui.components.ChoicePicker
import dev.breenottshook.ui.components.DiagnosticsPanel
import dev.breenottshook.ui.components.SettingsSectionCard

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEdit: (TtsConfig) -> Unit,
    onSave: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onTestConnection: () -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Breeno TTS Hook", style = MaterialTheme.typography.headlineMedium)
            Text(
                "模块 APP 与小布设置入口共用同一份版本化配置。",
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.draft.baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    "HTTP 连接未加密，请勿在不可信网络传输敏感文本。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ActionPanel(
                state = state,
                onRefreshCatalog = onRefreshCatalog,
                onTestConnection = onTestConnection,
                onPreview = onPreview,
                onStopPreview = onStopPreview,
                onSave = onSave,
                onResetDefaults = onResetDefaults
            )

            SettingsSection.entries.forEach { section ->
                SettingsSectionCard(title = section.title) {
                    if (section == SettingsSection.VOICE) {
                        CharacterEmotionPicker(
                            character = state.draft.character,
                            emotion = state.draft.emotion,
                            characters = state.characters,
                            emotions = state.emotions,
                            onCharacterChange = { character ->
                                val emotions = state.catalog?.characters?.get(character).orEmpty()
                                onEdit(
                                    state.draft.copy(
                                        character = character,
                                        emotion = state.draft.emotion.takeIf { it in emotions }
                                            ?: emotions.firstOrNull().orEmpty()
                                    )
                                )
                            },
                            onEmotionChange = { onEdit(state.draft.copy(emotion = it)) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    SettingsSchema.fields
                        .filter { it.section == section }
                        .filterNot { section == SettingsSection.VOICE && it.key in setOf("character", "emotion") }
                        .forEach { field ->
                            SchemaFieldEditor(
                                field = field,
                                config = state.draft,
                                issue = state.validationIssues[field.key],
                                onEdit = onEdit
                            )
                        }
                    if (section == SettingsSection.DEBUG) {
                        DiagnosticsPanel(
                            version = state.persistedVersion,
                            message = state.message,
                            connectionSucceeded = state.connectionSucceeded
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: SettingsUiState,
    onRefreshCatalog: () -> Unit,
    onTestConnection: () -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onSave: () -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onRefreshCatalog, modifier = Modifier.weight(1f)) {
                Text("刷新音色")
            }
            OutlinedButton(onClick = onTestConnection, modifier = Modifier.weight(1f)) {
                Text("测试连接")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = if (state.isPreviewing) onStopPreview else onPreview,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isPreviewing) "停止" else "试听")
            }
            Button(
                onClick = onSave,
                enabled = !state.isBusy && state.hasUnsavedChanges,
                modifier = Modifier.weight(1f)
            ) {
                Text("保存配置")
            }
            if (state.isBusy) CircularProgressIndicator()
        }
        OutlinedButton(onClick = onResetDefaults, modifier = Modifier.fillMaxWidth()) {
            Text("恢复默认草稿")
        }
    }
}

@Composable
private fun SchemaFieldEditor(
    field: SettingsField,
    config: TtsConfig,
    issue: String?,
    onEdit: (TtsConfig) -> Unit
) {
    val value = fieldValue(config, field.key)
    when (field.type) {
        SettingsFieldType.BOOLEAN -> BooleanSetting(
            label = field.label,
            description = field.description,
            checked = value.toBoolean(),
            onCheckedChange = { applyField(config, field.key, it.toString(), onEdit) }
        )
        SettingsFieldType.CHOICE -> ChoicePicker(
            label = field.label,
            value = value,
            choices = field.choices,
            onValueChange = { applyField(config, field.key, it, onEdit) },
            modifier = Modifier.padding(vertical = 6.dp)
        )
        SettingsFieldType.TEXT,
        SettingsFieldType.INTEGER,
        SettingsFieldType.DECIMAL -> OutlinedTextField(
            value = value,
            onValueChange = { applyField(config, field.key, it, onEdit) },
            label = { Text(field.label) },
            supportingText = { Text(issue ?: field.description) },
            isError = issue != null,
            singleLine = field.key != "testText",
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.type) {
                    SettingsFieldType.INTEGER -> KeyboardType.Number
                    SettingsFieldType.DECIMAL -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
    }
}

private fun applyField(
    config: TtsConfig,
    key: String,
    rawValue: String,
    onEdit: (TtsConfig) -> Unit
) {
    val result = SettingsSchema.edit(config, key, rawValue)
    if (result is SchemaEditResult.Success) onEdit(result.config)
}

private fun fieldValue(config: TtsConfig, key: String): String = when (key) {
    "enabled" -> config.enabled.toString()
    "baseUrl" -> config.baseUrl
    "character" -> config.character
    "emotion" -> config.emotion
    "useManualVoice" -> config.useManualVoice.toString()
    "manualCharacter" -> config.manualCharacter
    "manualEmotion" -> config.manualEmotion
    "textLanguage" -> config.textLanguage.name
    "audioFormat" -> config.audioFormat.name
    "topK" -> config.topK.toString()
    "topP" -> config.topP.toString()
    "temperature" -> config.temperature.toString()
    "batchSize" -> config.batchSize.toString()
    "speed" -> config.speed.toString()
    "saveTemp" -> config.saveTemp.toString()
    "stream" -> config.stream.toString()
    "connectTimeoutMs" -> config.connectTimeoutMs.toString()
    "readTimeoutMs" -> config.readTimeoutMs.toString()
    "fallbackToOriginal" -> config.fallbackToOriginal.toString()
    "strictMode" -> config.strictMode.toString()
    "forceModulePlayer" -> config.forceModulePlayer.toString()
    "logLevel" -> config.logLevel.name
    "testText" -> config.testText
    else -> ""
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            state = SettingsUiState(0, TtsConfig(), TtsConfig()),
            onEdit = {},
            onSave = {},
            onRefreshCatalog = {},
            onTestConnection = {},
            onPreview = {},
            onStopPreview = {},
            onResetDefaults = {}
        )
    }
}
