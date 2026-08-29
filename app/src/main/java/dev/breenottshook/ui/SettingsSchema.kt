package dev.breenottshook.ui

import dev.breenottshook.config.LogLevel
import dev.breenottshook.config.SynthesisAudioFormat
import dev.breenottshook.config.TextLanguage
import dev.breenottshook.config.TtsConfig

enum class SettingsSection(val title: String) {
    BASIC("基础"),
    VOICE("音色"),
    ADVANCED("高级设置"),
    DEBUG("调试")
}

enum class SettingsFieldType {
    BOOLEAN,
    TEXT,
    INTEGER,
    DECIMAL,
    CHOICE
}

data class SettingsField(
    val key: String,
    val label: String,
    val description: String,
    val section: SettingsSection,
    val type: SettingsFieldType,
    val choices: List<String> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val hostEditorSupported: Boolean = true
)

sealed interface SchemaEditResult {
    data class Success(val config: TtsConfig) : SchemaEditResult
    data class Invalid(val field: String, val message: String) : SchemaEditResult
}

object SettingsSchema {
    val fields: List<SettingsField> = listOf(
        boolean("enabled", "启用第三方 TTS", "关闭时不替换小布播报", SettingsSection.BASIC),
        text("baseUrl", "API 地址", "GPT-SoVITS 服务地址", SettingsSection.BASIC),
        text("character", "角色", "从服务器角色列表选择", SettingsSection.VOICE),
        text("emotion", "情感", "随角色动态加载", SettingsSection.VOICE),
        boolean("useManualVoice", "使用手动音色", "优先使用下面的手动角色和情感", SettingsSection.ADVANCED),
        text("manualCharacter", "手动角色", "目录中没有时直接填写服务器角色名", SettingsSection.ADVANCED),
        text("manualEmotion", "手动情感", "目录中没有时直接填写服务器情感名", SettingsSection.ADVANCED),
        choice("textLanguage", "文本语言", "发送给 GPT-SoVITS 的语言模式", SettingsSection.VOICE, enumNames<TextLanguage>()),
        choice("audioFormat", "音频格式", "接入播放器时建议使用 WAV", SettingsSection.ADVANCED, enumNames<SynthesisAudioFormat>()),
        integer("topK", "top_k", "采样候选数量", SettingsSection.ADVANCED, minimum = 1.0),
        decimal("topP", "top_p", "核采样概率", SettingsSection.ADVANCED, minimum = 0.0, maximum = 1.0),
        decimal("temperature", "temperature", "生成随机度", SettingsSection.ADVANCED, minimum = 0.0),
        integer("batchSize", "batch_size", "推理批大小", SettingsSection.ADVANCED, minimum = 1.0),
        decimal("speed", "语速", "1.0 为原速", SettingsSection.VOICE, minimum = 0.0),
        boolean("saveTemp", "保存临时音频", "由服务端决定临时文件保存行为", SettingsSection.ADVANCED),
        boolean("stream", "流式响应", "边生成边解码播放", SettingsSection.ADVANCED),
        integer(
            "maxConcurrentSynthesis",
            "并发请求数量",
            "必须大于 0；数值过大可能增加服务和内存压力",
            SettingsSection.ADVANCED,
            minimum = 1.0
        ),
        integer(
            "playbackIntervalMs",
            "播放间隔（毫秒）",
            "相邻句子之间的静音时长，范围 0–5000",
            SettingsSection.ADVANCED,
            minimum = 0.0,
            maximum = 5_000.0
        ),
        integer("connectTimeoutMs", "连接超时（毫秒）", "允许范围 1000–120000", SettingsSection.ADVANCED, 1_000.0, 120_000.0),
        integer("readTimeoutMs", "读取超时（毫秒）", "允许范围 1000–120000", SettingsSection.ADVANCED, 1_000.0, 120_000.0),
        boolean("fallbackToOriginal", "失败时使用原 TTS", "仅在第三方音频开始播放前允许回退", SettingsSection.BASIC),
        boolean("strictMode", "严格调试模式", "失败时禁止静默回退原 TTS", SettingsSection.DEBUG),
        boolean("forceModulePlayer", "强制模块播放器", "调试原播放器兼容问题", SettingsSection.DEBUG),
        choice("logLevel", "日志级别", "普通日志不会记录完整播报文本", SettingsSection.DEBUG, enumNames<LogLevel>()),
        text("testText", "试听文本", "仅用于连接测试和试听", SettingsSection.DEBUG)
    )

    fun edit(config: TtsConfig, key: String, rawValue: String): SchemaEditResult {
        fun success(value: TtsConfig) = SchemaEditResult.Success(value)
        fun invalid(message: String) = SchemaEditResult.Invalid(key, message)
        fun booleanValue(): Boolean? = when (rawValue.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

        return try {
            when (key) {
                "enabled" -> booleanValue()?.let { success(config.copy(enabled = it)) }
                    ?: invalid("请输入 true 或 false")
                "baseUrl" -> success(config.copy(baseUrl = rawValue))
                "character" -> success(config.copy(character = rawValue))
                "emotion" -> success(config.copy(emotion = rawValue))
                "useManualVoice" -> booleanValue()?.let { enabled ->
                    success(
                        config.copy(
                            useManualVoice = enabled,
                            manualCharacter = if (enabled && config.manualCharacter.isBlank()) {
                                config.character
                            } else {
                                config.manualCharacter
                            },
                            manualEmotion = if (enabled && config.manualEmotion.isBlank()) {
                                config.emotion
                            } else {
                                config.manualEmotion
                            }
                        )
                    )
                }
                    ?: invalid("请输入 true 或 false")
                "manualCharacter" -> success(config.copy(manualCharacter = rawValue))
                "manualEmotion" -> success(config.copy(manualEmotion = rawValue))
                "textLanguage" -> success(config.copy(textLanguage = TextLanguage.valueOf(rawValue)))
                "audioFormat" -> success(config.copy(audioFormat = SynthesisAudioFormat.valueOf(rawValue)))
                "topK" -> rawValue.toIntOrNull()?.let { success(config.copy(topK = it)) }
                    ?: invalid("请输入整数")
                "topP" -> rawValue.toDoubleOrNull()?.let { success(config.copy(topP = it)) }
                    ?: invalid("请输入数字")
                "temperature" -> rawValue.toDoubleOrNull()?.let { success(config.copy(temperature = it)) }
                    ?: invalid("请输入数字")
                "batchSize" -> rawValue.toIntOrNull()?.let { success(config.copy(batchSize = it)) }
                    ?: invalid("请输入整数")
                "speed" -> rawValue.toDoubleOrNull()?.let { success(config.copy(speed = it)) }
                    ?: invalid("请输入数字")
                "saveTemp" -> booleanValue()?.let { success(config.copy(saveTemp = it)) }
                    ?: invalid("请输入 true 或 false")
                "stream" -> booleanValue()?.let { success(config.copy(stream = it)) }
                    ?: invalid("请输入 true 或 false")
                "maxConcurrentSynthesis" -> rawValue.toIntOrNull()
                    ?.let { success(config.copy(maxConcurrentSynthesis = it)) }
                    ?: invalid("请输入整数")
                "playbackIntervalMs" -> rawValue.toLongOrNull()
                    ?.let { success(config.copy(playbackIntervalMs = it)) }
                    ?: invalid("请输入整数")
                "connectTimeoutMs" -> rawValue.toLongOrNull()?.let { success(config.copy(connectTimeoutMs = it)) }
                    ?: invalid("请输入整数")
                "readTimeoutMs" -> rawValue.toLongOrNull()?.let { success(config.copy(readTimeoutMs = it)) }
                    ?: invalid("请输入整数")
                "fallbackToOriginal" -> booleanValue()?.let { success(config.copy(fallbackToOriginal = it)) }
                    ?: invalid("请输入 true 或 false")
                "strictMode" -> booleanValue()?.let { success(config.copy(strictMode = it)) }
                    ?: invalid("请输入 true 或 false")
                "forceModulePlayer" -> booleanValue()?.let { success(config.copy(forceModulePlayer = it)) }
                    ?: invalid("请输入 true 或 false")
                "logLevel" -> success(config.copy(logLevel = LogLevel.valueOf(rawValue)))
                "testText" -> success(config.copy(testText = rawValue))
                else -> SchemaEditResult.Invalid(key, "未知配置项")
            }
        } catch (_: IllegalArgumentException) {
            invalid("选项无效")
        }
    }

    private fun boolean(
        key: String,
        label: String,
        description: String,
        section: SettingsSection
    ) = SettingsField(key, label, description, section, SettingsFieldType.BOOLEAN)

    private fun text(
        key: String,
        label: String,
        description: String,
        section: SettingsSection
    ) = SettingsField(key, label, description, section, SettingsFieldType.TEXT)

    private fun integer(
        key: String,
        label: String,
        description: String,
        section: SettingsSection,
        minimum: Double? = null,
        maximum: Double? = null
    ) = SettingsField(key, label, description, section, SettingsFieldType.INTEGER, minimum = minimum, maximum = maximum)

    private fun decimal(
        key: String,
        label: String,
        description: String,
        section: SettingsSection,
        minimum: Double? = null,
        maximum: Double? = null
    ) = SettingsField(key, label, description, section, SettingsFieldType.DECIMAL, minimum = minimum, maximum = maximum)

    private fun choice(
        key: String,
        label: String,
        description: String,
        section: SettingsSection,
        choices: List<String>
    ) = SettingsField(key, label, description, section, SettingsFieldType.CHOICE, choices = choices)

    private inline fun <reified T : Enum<T>> enumNames(): List<String> =
        enumValues<T>().map { it.name }
}
