package dev.breenottshook.ui.host

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.CompoundButton
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.ui.SettingsField
import dev.breenottshook.ui.SettingsFieldType
import dev.breenottshook.ui.SettingsSchema

data class HostFieldBinding(
    val field: SettingsField,
    val editor: View,
    val readRawValue: () -> String
)

object HostFieldFactory {
    val supportedKeys: Set<String>
        get() = SettingsSchema.fields.mapTo(linkedSetOf()) { it.key }

    fun switchContentDescription(label: String, checked: Boolean): String =
        "$label，${if (checked) "已开启" else "已关闭"}，双击切换"

    fun createAll(context: Context, config: TtsConfig): List<HostFieldBinding> =
        SettingsSchema.fields.map { field -> create(context, config, field) }

    private fun create(
        context: Context,
        config: TtsConfig,
        field: SettingsField
    ): HostFieldBinding {
        val currentValue = read(config, field.key)
        if (field.key in setOf("character", "emotion")) {
            val editor = Spinner(context).apply {
                contentDescription = field.description
                adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    emptyList<String>()
                )
            }
            return HostFieldBinding(field, editor) {
                editor.selectedItem?.toString().orEmpty()
            }
        }
        return when (field.type) {
            SettingsFieldType.BOOLEAN -> {
                val switch = createNativeSwitch(context) ?: Switch(context)
                (switch as? android.widget.TextView)?.text = field.label
                (switch as? CompoundButton)?.apply {
                    isChecked = currentValue.toBoolean()
                    contentDescription = switchContentDescription(field.label, isChecked)
                    setOnCheckedChangeListener { _, checked ->
                        contentDescription = switchContentDescription(field.label, checked)
                    }
                }
                HostFieldBinding(field, switch) { switch.isChecked.toString() }
            }
            SettingsFieldType.CHOICE -> {
                val spinner = Spinner(context).apply {
                    contentDescription = field.label
                    adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        field.choices
                    )
                    setSelection(field.choices.indexOf(currentValue).coerceAtLeast(0))
                }
                HostFieldBinding(field, spinner) {
                    spinner.selectedItem?.toString().orEmpty()
                }
            }
            SettingsFieldType.TEXT,
            SettingsFieldType.INTEGER,
            SettingsFieldType.DECIMAL -> {
                val editText = EditText(context).apply {
                    hint = field.label
                    contentDescription = field.description
                    setText(currentValue)
                    inputType = when (field.type) {
                        SettingsFieldType.INTEGER -> InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_FLAG_SIGNED
                        SettingsFieldType.DECIMAL -> InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                }
                HostFieldBinding(field, editText) { editText.text.toString() }
            }
        }
    }

    private fun createNativeSwitch(context: Context): CompoundButton? = runCatching {
        val type = Class.forName("com.coui.appcompat.couiswitch.COUISwitch", false, context.classLoader)
        val constructor = type.constructors.firstOrNull { c ->
            c.parameterTypes.firstOrNull() == Context::class.java &&
                c.parameterTypes.drop(1).all { it == android.util.AttributeSet::class.java || it == Int::class.javaPrimitiveType }
        } ?: return@runCatching null
        val args = constructor.parameterTypes.drop(1).map {
            when (it) {
                android.util.AttributeSet::class.java -> null
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }.toTypedArray()
        constructor.newInstance(*arrayOf(context, *args)) as? CompoundButton
    }.getOrNull()

    private fun read(config: TtsConfig, key: String): String = when (key) {
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
        "maxConcurrentSynthesis" -> config.maxConcurrentSynthesis.toString()
        "playbackIntervalMs" -> config.playbackIntervalMs.toString()
        "connectTimeoutMs" -> config.connectTimeoutMs.toString()
        "readTimeoutMs" -> config.readTimeoutMs.toString()
        "fallbackToOriginal" -> config.fallbackToOriginal.toString()
        "strictMode" -> config.strictMode.toString()
        "forceModulePlayer" -> config.forceModulePlayer.toString()
        "logLevel" -> config.logLevel.name
        "testText" -> config.testText
        else -> ""
    }
}
