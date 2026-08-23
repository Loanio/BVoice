package dev.breenottshook.ui.host

import dev.breenottshook.ui.SettingsSection
import java.util.Locale

internal object HostStrings {
    fun isEnglish(locale: Locale): Boolean = locale.language.equals("en", ignoreCase = true)

    fun title(locale: Locale): String = if (isEnglish(locale)) "Third-party voice" else "第三方音色"

    fun sectionTitle(section: SettingsSection, locale: Locale): String {
        if (!isEnglish(locale)) return section.title
        return when (section) {
            SettingsSection.BASIC -> "Basic"
            SettingsSection.VOICE -> "Voice"
            SettingsSection.ADVANCED -> "Advanced"
            SettingsSection.DEBUG -> "Diagnostics"
        }
    }

    fun fieldLabel(key: String, locale: Locale): String {
        if (!isEnglish(locale)) return chineseLabels[key] ?: key
        return englishLabels[key] ?: key
    }

    fun fieldDescription(key: String, locale: Locale): String {
        if (!isEnglish(locale)) return chineseDescriptions[key] ?: ""
        return englishDescriptions[key] ?: ""
    }

    fun previewLabel(locale: Locale): String = if (isEnglish(locale)) "Try current voice" else "试听当前音色"
    fun stopPreviewLabel(locale: Locale): String = if (isEnglish(locale)) "Stop preview" else "停止试听"
    fun previewSummary(locale: Locale): String = if (isEnglish(locale)) {
        "Preview with the current saved role and emotion"
    } else {
        "试听当前选中的音色"
    }

    fun defaultLabel(locale: Locale): String = if (isEnglish(locale)) "Restore defaults" else "恢复默认"
    fun defaultSummary(locale: Locale): String = if (isEnglish(locale)) {
        "Restore the defaults and save automatically"
    } else {
        "恢复默认设置"
    }

    fun copyLogsLabel(locale: Locale): String = if (isEnglish(locale)) "Copy diagnostic logs" else "复制诊断日志"
    fun copyLogsSummary(locale: Locale): String = if (isEnglish(locale)) {
        "Copy recent runtime logs"
    } else {
        "复制最近的运行记录"
    }
    fun copyLogsDone(locale: Locale): String = if (isEnglish(locale)) "Diagnostic logs copied" else "诊断日志已复制"

    fun previewTextLabel(locale: Locale): String = if (isEnglish(locale)) "Preview text" else "试听文本"

    private val chineseLabels = mapOf(
        "enabled" to "启用第三方 TTS", "baseUrl" to "API 地址", "character" to "角色", "emotion" to "情感",
        "useManualVoice" to "使用手动音色", "manualCharacter" to "手动角色", "manualEmotion" to "手动情感",
        "textLanguage" to "文本语言", "audioFormat" to "音频格式", "topK" to "top_k", "topP" to "top_p",
        "temperature" to "temperature", "batchSize" to "batch_size", "speed" to "语速", "saveTemp" to "保存临时音频",
        "stream" to "流式响应", "connectTimeoutMs" to "连接超时（毫秒）", "readTimeoutMs" to "读取超时（毫秒）",
        "fallbackToOriginal" to "失败时使用原 TTS", "strictMode" to "严格调试模式",
        "forceModulePlayer" to "强制模块播放器", "logLevel" to "日志级别", "testText" to "试听文本"
    )

    private val englishLabels = mapOf(
        "enabled" to "Use third-party TTS", "baseUrl" to "API address", "character" to "Role", "emotion" to "Emotion",
        "useManualVoice" to "Use manual voice", "manualCharacter" to "Manual role", "manualEmotion" to "Manual emotion",
        "textLanguage" to "Text language", "audioFormat" to "Audio format", "topK" to "top_k", "topP" to "top_p",
        "temperature" to "temperature", "batchSize" to "batch_size", "speed" to "Speed", "saveTemp" to "Save temporary audio",
        "stream" to "Stream response", "connectTimeoutMs" to "Connection timeout (ms)", "readTimeoutMs" to "Read timeout (ms)",
        "fallbackToOriginal" to "Use original TTS on failure", "strictMode" to "Strict diagnostics",
        "forceModulePlayer" to "Force module player", "logLevel" to "Log level", "testText" to "Preview text"
    )

    private val chineseDescriptions = mapOf(
        "enabled" to "关闭后使用小布原音色", "baseUrl" to "GPT-SoVITS 服务地址",
        "character" to "从服务返回的列表中选择", "emotion" to "随角色更新", "useManualVoice" to "使用自定义角色和情感",
        "manualCharacter" to "列表中没有时可填写", "manualEmotion" to "列表中没有时可填写",
        "textLanguage" to "发送文本的语言", "audioFormat" to "播放格式",
        "speed" to "1.0 为原速", "saveTemp" to "由服务端处理", "stream" to "边生成边播放",
        "connectTimeoutMs" to "允许范围 1000–120000", "readTimeoutMs" to "允许范围 1000–120000",
        "fallbackToOriginal" to "第三方失败时使用原音色", "strictMode" to "失败时显示详细错误",
        "forceModulePlayer" to "播放器兼容性调试", "logLevel" to "控制日志详细程度", "testText" to "用于连接测试和试听"
    )

    private val englishDescriptions = mapOf(
        "enabled" to "Turn off to use Breeno's voice", "baseUrl" to "GPT-SoVITS service URL",
        "character" to "Choose from the service list", "emotion" to "Updated for the selected role",
        "useManualVoice" to "Use a custom role and emotion", "manualCharacter" to "Use when it is not in the list",
        "manualEmotion" to "Use when it is not in the list", "textLanguage" to "Language of the text",
        "audioFormat" to "Playback format", "speed" to "1.0 is normal speed",
        "saveTemp" to "Handled by the service", "stream" to "Play while generating",
        "connectTimeoutMs" to "Allowed range: 1000–120000", "readTimeoutMs" to "Allowed range: 1000–120000",
        "fallbackToOriginal" to "Use Breeno's voice if this fails", "strictMode" to "Show detailed errors",
        "forceModulePlayer" to "Player compatibility", "logLevel" to "Log detail level",
        "testText" to "Used for connection checks and preview"
    )
}
