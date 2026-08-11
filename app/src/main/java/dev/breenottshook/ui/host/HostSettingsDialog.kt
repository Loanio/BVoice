package dev.breenottshook.ui.host

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.breenottshook.api.CatalogState
import dev.breenottshook.api.CharacterCache
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.config.ConfigRepository
import dev.breenottshook.config.ConfigValidator
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
import dev.breenottshook.config.ValidationResult
import dev.breenottshook.ui.ApiConnectionTester
import dev.breenottshook.ui.ContentProviderSettingsRepository
import dev.breenottshook.ui.SessionPreviewController
import dev.breenottshook.ui.SettingsRepository
import dev.breenottshook.ui.SettingsSchema
import dev.breenottshook.ui.SettingsSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class HostSettingsDialog(
    private val context: Context,
    private val repository: SettingsRepository = ContentProviderSettingsRepository(
        ConfigRepository(context.contentResolver)
    )
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = GptSovitsClient(OkHttpClient())
    private val catalogCache = CharacterCache(loader = client::fetchCharacters)
    private val connectionTester = ApiConnectionTester(client)
    private val previewController = SessionPreviewController(context, scope, client)
    private val snapshot = repository.read()
    private var bindings = HostFieldFactory.createAll(context, snapshot.value)
    private var catalog: CharacterCatalog? = null

    fun show(): AlertDialog {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        content.addView(TextView(context).apply {
            text = "与 BreenoTTSHook 模块 APP 共用配置版本 ${snapshot.version}"
        })
        if (snapshot.value.baseUrl.startsWith("http://", ignoreCase = true)) {
            content.addView(TextView(context).apply {
                text = "警告：HTTP 连接未加密"
                setTextColor(0xffff4444.toInt())
            })
        }
        content.addView(actionButtons())

        SettingsSection.entries.forEach { section ->
            content.addView(TextView(context).apply {
                text = section.title
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            })
            bindings.filter { it.field.section == section }.forEach { binding ->
                if (binding.editor !is android.widget.Switch) {
                    content.addView(TextView(context).apply {
                        text = binding.field.label
                    })
                }
                content.addView(
                    binding.editor,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("第三方音色")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton("保存", null)
            .setNeutralButton("恢复默认", null)
            .setNegativeButton("关闭", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                save(dialog)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                applyValuesToEditors(TtsConfig())
                toast("已恢复默认草稿，保存后生效")
            }
        }
        dialog.setOnDismissListener { scope.cancel() }
        dialog.show()
        return dialog
    }

    private fun actionButtons() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(Button(context).apply {
            text = "刷新音色"
            setOnClickListener { refreshCatalog() }
        })
        addView(Button(context).apply {
            text = "测试连接"
            setOnClickListener { testConnection() }
        })
        addView(Button(context).apply {
            text = "试听"
            setOnClickListener { preview() }
        })
        addView(Button(context).apply {
            text = "停止试听"
            setOnClickListener { scope.launch { previewController.stop() } }
        })
    }

    private fun refreshCatalog() {
        val config = currentDraft() ?: return toast("请先修正输入格式")
        scope.launch {
            when (val result = catalogCache.getOrFetch(config.baseUrl, forceRefresh = true)) {
                is CatalogState.Fresh -> applyCatalog(result.catalog, "音色列表已刷新")
                is CatalogState.Stale -> applyCatalog(result.catalog, "刷新失败，已使用缓存")
                is CatalogState.Failed -> toast("音色列表加载失败：${result.reason}")
            }
        }
    }

    private fun applyCatalog(next: CharacterCatalog, message: String) {
        catalog = next
        val characterEditor = binding("character")?.editor as? AutoCompleteTextView
        val emotionEditor = binding("emotion")?.editor as? AutoCompleteTextView
        characterEditor?.setAdapter(adapter(next.characters.keys.sorted()))
        characterEditor?.setOnItemClickListener { _, _, _, _ ->
            emotionEditor?.setAdapter(adapter(next.characters[characterEditor.text.toString()].orEmpty()))
        }
        emotionEditor?.setAdapter(adapter(next.characters[characterEditor?.text?.toString()].orEmpty()))
        characterEditor?.showDropDown()
        toast(message)
    }

    private fun testConnection() {
        val config = currentDraft() ?: return toast("请先修正输入格式")
        scope.launch {
            val result = connectionTester.test(config)
            toast(if (result.isSuccess) "连接成功" else "连接失败：${result.exceptionOrNull()?.message}")
        }
    }

    private fun preview() {
        val config = currentDraft() ?: return toast("请先修正输入格式")
        scope.launch {
            val result = previewController.preview(config.testText, config)
            if (result.isFailure) toast("试听失败：${result.exceptionOrNull()?.message}")
        }
    }

    private fun save(dialog: AlertDialog) {
        val draft = currentDraft() ?: return toast("请先修正输入格式")
        when (val validation = ConfigValidator.validate(draft)) {
            is ValidationResult.Invalid -> toast(validation.issues.joinToString { it.message })
            is ValidationResult.Valid -> when (
                val result = repository.update(snapshot.version, validation.value)
            ) {
                is UpdateResult.Success -> {
                    toast("配置已保存，版本 ${result.snapshot.version}")
                    dialog.dismiss()
                }
                is UpdateResult.VersionConflict -> toast("配置冲突，请关闭后重新打开")
                is UpdateResult.Invalid -> toast(result.issues.joinToString { it.message })
                UpdateResult.PersistenceFailure -> toast("配置保存失败")
            }
        }
    }

    private fun currentDraft(): TtsConfig? {
        var config = snapshot.value
        bindings.forEach { binding ->
            when (val result = SettingsSchema.edit(config, binding.field.key, binding.readRawValue())) {
                is dev.breenottshook.ui.SchemaEditResult.Success -> config = result.config
                is dev.breenottshook.ui.SchemaEditResult.Invalid -> return null
            }
        }
        return config
    }

    private fun applyValuesToEditors(config: TtsConfig) {
        val defaults = HostFieldFactory.createAll(context, config)
        bindings.zip(defaults).forEach { (target, source) ->
            val raw = source.readRawValue()
            when (val editor = target.editor) {
                is android.widget.Switch -> editor.isChecked = raw.toBoolean()
                is android.widget.EditText -> editor.setText(raw)
                is android.widget.Spinner -> {
                    val position = target.field.choices.indexOf(raw).coerceAtLeast(0)
                    editor.setSelection(position)
                }
            }
        }
    }

    private fun binding(key: String) = bindings.firstOrNull { it.field.key == key }

    private fun adapter(values: List<String>) = ArrayAdapter(
        context,
        android.R.layout.simple_dropdown_item_1line,
        values
    )

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
