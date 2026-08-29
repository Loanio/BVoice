package dev.breenottshook.ui.host

import dev.breenottshook.config.ConfigValidator
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.ValidationResult
import dev.breenottshook.ui.SchemaEditResult
import dev.breenottshook.ui.SettingsSchema

data class HostInputValue(
    val key: String,
    val rawValue: String
)

sealed interface HostValidationResult {
    data class Valid(val config: TtsConfig) : HostValidationResult
    data class Invalid(val field: String, val message: String) : HostValidationResult
}

object HostInputValidation {
    fun validate(base: TtsConfig, values: List<HostInputValue>): HostValidationResult {
        var config = base
        values.forEach { value ->
            if (value.key in CATALOG_SELECTION_KEYS && value.rawValue.isBlank()) return@forEach
            when (val result = SettingsSchema.edit(config, value.key, value.rawValue)) {
                is SchemaEditResult.Success -> config = result.config
                is SchemaEditResult.Invalid -> return HostValidationResult.Invalid(
                    result.field,
                    result.message
                )
            }
        }
        return when (val result = ConfigValidator.validate(config)) {
            is ValidationResult.Valid -> HostValidationResult.Valid(result.value)
            is ValidationResult.Invalid -> result.issues.first().let { issue ->
                HostValidationResult.Invalid(issue.field, issue.message)
            }
        }
    }

    private val CATALOG_SELECTION_KEYS = setOf("character", "emotion")
}
