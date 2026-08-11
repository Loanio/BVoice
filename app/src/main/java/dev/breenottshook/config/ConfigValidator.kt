package dev.breenottshook.config

import java.net.URI

data class ConfigIssue(
    val field: String,
    val message: String
)

sealed interface ValidationResult {
    data class Valid(val value: TtsConfig) : ValidationResult
    data class Invalid(val issues: List<ConfigIssue>) : ValidationResult
}

object ConfigValidator {
    private const val MIN_TIMEOUT_MS = 1_000L
    private const val MAX_TIMEOUT_MS = 120_000L

    fun validate(config: TtsConfig): ValidationResult {
        val issues = buildList {
            if (!isHttpUrl(config.baseUrl)) add(ConfigIssue("baseUrl", "仅支持 HTTP 或 HTTPS 地址"))
            if (!config.speed.isFinite() || config.speed <= 0.0) {
                add(ConfigIssue("speed", "语速必须大于 0"))
            }
            if (config.batchSize < 1) add(ConfigIssue("batchSize", "批大小至少为 1"))
            if (!config.topP.isFinite() || config.topP !in 0.0..1.0) {
                add(ConfigIssue("topP", "top_p 必须位于 0 到 1"))
            }
            if (!config.temperature.isFinite() || config.temperature <= 0.0) {
                add(ConfigIssue("temperature", "temperature 必须大于 0"))
            }
            if (config.connectTimeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
                add(ConfigIssue("connectTimeoutMs", "连接超时必须位于 1 到 120 秒"))
            }
            if (config.readTimeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
                add(ConfigIssue("readTimeoutMs", "读取超时必须位于 1 到 120 秒"))
            }
        }
        if (issues.isNotEmpty()) return ValidationResult.Invalid(issues)
        return ValidationResult.Valid(config.copy(baseUrl = normalizeBaseUrl(config.baseUrl)))
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/') + "/"
}
