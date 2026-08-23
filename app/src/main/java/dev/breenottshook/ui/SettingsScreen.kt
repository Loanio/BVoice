package dev.breenottshook.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
    onUpdateCoreSetting: (TtsConfig) -> Unit,
    onTestConnection: () -> Unit,
    onPreview: () -> Unit,
    onAddressBlur: () -> Unit,
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

            CoreSettingsSection(state = state, onUpdateCoreSetting = onUpdateCoreSetting)
            ServiceSettingsSection(state = state, onEdit = onEdit, onAddressBlur = onAddressBlur)
            StatusSection(
                state = state,
                onTestConnection = onTestConnection
            )
            PreviewSection(state = state, onPreview = onPreview, onStopPreview = onStopPreview)
            VoiceSection(state = state, onUpdateCoreSetting = onUpdateCoreSetting)
            AdvancedSettingsSection(state = state, onEdit = onEdit, onResetDefaults = onResetDefaults)
        }
    }
}

@Composable
private fun StatusSection(
    state: SettingsUiState,
    onTestConnection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = state.operation == SettingsOperation.IDLE && !state.isBusy,
                onClick = onTestConnection
            )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("服务状态", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(serviceStatusText(state), style = MaterialTheme.typography.bodyLarge)
            state.message?.takeIf { it != state.serviceStatusMessage }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text("点击卡片测试连接并刷新音色", style = MaterialTheme.typography.bodySmall)
            if (state.isBusy) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PreviewSection(state: SettingsUiState, onPreview: () -> Unit, onStopPreview: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = state.operation == SettingsOperation.IDLE || state.isPreviewing,
                onClick = if (state.isPreviewing) onStopPreview else onPreview
            )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (state.isPreviewing) "正在试听" else "试听当前音色", style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.isPreviewing) "点击停止试听" else "使用当前已自动保存的角色与情感",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CoreSettingsSection(
    state: SettingsUiState,
    onUpdateCoreSetting: (TtsConfig) -> Unit
) {
    val controlsEnabled = state.operation == SettingsOperation.IDLE && !state.isBusy
    SettingsSectionCard(title = "基础设置") {
        BooleanSetting(
            label = "启用第三方 TTS",
            description = "关闭时不替换小布播报",
            checked = state.draft.enabled,
            onCheckedChange = { onUpdateCoreSetting(state.draft.copy(enabled = it)) },
            enabled = controlsEnabled
        )
    }
}

@Composable
private fun ServiceSettingsSection(
    state: SettingsUiState,
    onEdit: (TtsConfig) -> Unit,
    onAddressBlur: () -> Unit
) {
    var wasFocused by remember { mutableStateOf(false) }
    SettingsSectionCard(title = "服务配置") {
        OutlinedTextField(
            value = state.draft.baseUrl,
            onValueChange = { onEdit(state.draft.copy(baseUrl = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) onAddressBlur()
                    wasFocused = focusState.isFocused
                },
            label = { Text("API 地址") },
            isError = state.validationIssues.containsKey("baseUrl"),
            supportingText = {
                Text(state.validationIssues["baseUrl"] ?: "GPT-SoVITS 服务根地址")
            },
            enabled = state.operation == SettingsOperation.IDLE && !state.isBusy
        )
        if (state.draft.baseUrl.trim().startsWith("http://", ignoreCase = true)) {
            Text(
                "HTTP 连接未加密，请勿在不可信网络传输敏感文本。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun VoiceSection(
    state: SettingsUiState,
    onUpdateCoreSetting: (TtsConfig) -> Unit
) {
    val controlsEnabled = state.operation == SettingsOperation.IDLE && !state.isBusy
    SettingsSectionCard(title = "音色") {
        CharacterEmotionPicker(
            character = state.draft.character,
            emotion = state.draft.emotion,
            characters = state.characters,
            emotions = state.emotions,
            enabled = controlsEnabled,
            onCharacterChange = { character ->
                val emotions = state.catalog?.characters?.get(character).orEmpty()
                onUpdateCoreSetting(
                    state.draft.copy(
                        character = character,
                        emotion = state.draft.emotion.takeIf { it in emotions }
                            ?: emotions.firstOrNull().orEmpty()
                    )
                )
            },
            onEmotionChange = { onUpdateCoreSetting(state.draft.copy(emotion = it)) }
        )
    }
}

@Composable
private fun AdvancedSettingsSection(
    state: SettingsUiState,
    onEdit: (TtsConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsSectionCard(title = "高级选项") {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (expanded) "收起高级设置" else "高级设置")
        }
        if (!expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                "手动音色、音频格式、试听文本和兼容性参数集中在这里。",
                style = MaterialTheme.typography.bodyMedium
            )
            return@SettingsSectionCard
        }

        Spacer(Modifier.height(12.dp))
        advancedFields.filter { field ->
            state.draft.useManualVoice || field.key !in manualVoiceFieldKeys
        }.forEach { field ->
            SchemaFieldEditor(
                field = field,
                config = state.draft,
                issue = state.validationIssues[field.key],
                onEdit = onEdit
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                state.isBusy -> "正在自动保存…"
                state.hasUnsavedChanges -> "有未保存更改，稍后会自动保存"
                else -> "高级设置会自动保存"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = onResetDefaults,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认值并自动保存")
        }
        DiagnosticsPanel(
            version = state.persistedVersion,
            message = state.message,
            connectionSucceeded = state.connectionSucceeded,
            modifier = Modifier.padding(top = 8.dp)
        )
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

private fun serviceStatusText(state: SettingsUiState): String = when (state.serviceStatus) {
    ServiceStatus.UNCHECKED -> "尚未检查服务连接"
    ServiceStatus.CHECKING -> "正在检查服务连接"
    ServiceStatus.AVAILABLE -> state.serviceStatusMessage ?: "服务连接可用"
    ServiceStatus.UNAVAILABLE -> state.serviceStatusMessage ?: "服务连接不可用"
}

private val visibleSettingKeys = setOf(
    "enabled",
    "baseUrl",
    "character",
    "emotion"
)

private val advancedFields = SettingsSchema.fields.filterNot { it.key in visibleSettingKeys }

private val manualVoiceFieldKeys = setOf("manualCharacter", "manualEmotion")

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            state = SettingsUiState(0, TtsConfig(), TtsConfig()),
            onEdit = {},
            onUpdateCoreSetting = {},
            onTestConnection = {},
            onPreview = {},
            onAddressBlur = {},
            onStopPreview = {},
            onResetDefaults = {}
        )
    }
}
