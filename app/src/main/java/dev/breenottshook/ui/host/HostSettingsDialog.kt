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
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.content.res.Configuration
import android.widget.EditText
import android.widget.Spinner
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
import dev.breenottshook.ui.PreviewListener
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
    companion object {
        internal fun previewActionLabel(isPreviewing: Boolean): String =
            if (isPreviewing) "停止试听" else "试听"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = GptSovitsClient(OkHttpClient())
    private val catalogCache = CharacterCache(loader = client::fetchCharacters)
    private val connectionTester = ApiConnectionTester(client)
    private val previewController = SessionPreviewController(context, scope, client)
    private val snapshot = repository.read()
    private var bindings = HostFieldFactory.createAll(context, snapshot.value)
    private var catalog: CharacterCatalog? = null
    private val hostTitleStyle by lazy { findHostTextView(android.R.id.title) }
    private val hostSummaryStyle by lazy { findHostTextView(android.R.id.summary) }

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
                save { dialog.dismiss() }
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

    fun createPageContent(onClose: () -> Unit): ScrollView {
        val pageBackground = resolvePageBackground()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
            setBackgroundColor(pageBackground)
        }
        content.addView(TextView(context).apply {
            text = "与 BreenoTTSHook 模块 APP 共用配置版本 ${snapshot.version}"
            setTextColor(resolveColor(android.R.attr.textColorSecondary, 0xff777777.toInt()))
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })
        if (snapshot.value.baseUrl.startsWith("http://", ignoreCase = true)) {
            content.addView(TextView(context).apply {
                text = "警告：HTTP 连接未加密"
                setTextColor(0xffff4444.toInt())
            })
        }
        content.addView(card(actionButtons()), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        SettingsSection.entries.forEach { section ->
            val sectionBody = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(TextView(context).apply {
                text = section.title
                // JADX: COUIPreferenceCategory's default style is 12sp, uses the
                // secondary neutral color, and does not force a medium typeface.
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(resolveNamedColor("couiColorSecondNeutral",
                    resolveColor(android.R.attr.textColorSecondary, 0xff777777.toInt())))
                typeface = Typeface.DEFAULT
                includeFontPadding = true
                minHeight = resolveDimension("coui_preference_category_text_height", dp(28))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = resolveDimension(
            "support_preference_category_layout_title_margin_start_small", dp(16)
                    )
                    marginEnd = resolveDimension(
                        "support_preference_category_layout_title_margin_end_large", dp(16)
                    )
                    topMargin = resolveDimension("coui_preference_category_margintop_large", dp(8))
                    bottomMargin = resolveDimension(
                        "support_preference_category_layout_title_margin_end_new", dp(4)
                    )
                }
                setPadding(0, 0, 0, 0)
            })
            bindings.filter { it.field.section == section }.forEachIndexed { index, binding ->
                sectionBody.addView(nativeFieldRow(binding))
                if (index < bindings.count { it.field.section == section } - 1) {
                    sectionBody.addView(divider())
                }
            }
            content.addView(card(sectionBody), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
        content.addView(nativeButton("保存").apply {
            text = "保存"
            setOnClickListener { save(onClose) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(4); bottomMargin = dp(8)
        })
        content.addView(nativeButton("恢复默认").apply {
            text = "恢复默认"
            setOnClickListener {
                applyValuesToEditors(TtsConfig())
                toast("已恢复默认草稿，保存后生效")
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        return ScrollView(context).apply {
            setBackgroundColor(pageBackground)
            addView(content)
        }
    }

    fun dispose() = scope.cancel()

    internal fun resolvePageBackground(): Int = HostPageVisuals.backgroundColor(
        if (isNightMode()) 0xff000000.toInt() else resolveLightPageBackground(),
        isNightMode()
    )

    internal fun resolvePrimaryTextColor(): Int =
        resolveColor(android.R.attr.textColorPrimary, 0xff222222.toInt())

    private fun actionButtons() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        fun row(vararg buttons: Button) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEachIndexed { index, button ->
                addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (index == 0) marginEnd = dp(4)
                })
            }
        }
        addView(row(
            nativeButton("刷新音色").apply { setOnClickListener { refreshCatalog() } },
            nativeButton("测试连接").apply { setOnClickListener { testConnection() } }
        ))
        val previewButton = nativeButton(previewActionLabel(false))
        var isPreviewing = false
        previewButton.setOnClickListener {
            if (isPreviewing) {
                scope.launch { previewController.stop() }
                isPreviewing = false
                previewButton.text = previewActionLabel(false)
            } else {
                isPreviewing = true
                previewButton.text = previewActionLabel(true)
                preview()
            }
        }
        addView(row(previewButton), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
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
            val result = previewController.preview(
                config.testText,
                config,
                object : PreviewListener {
                    override fun onStarted() = Unit
                    override fun onCompleted() = Unit
                    override fun onError(error: Throwable) = toast("试听失败：${error.message}")
                    override fun onCancelled(reason: String) = Unit
                }
            )
            if (result.isFailure) toast("试听失败：${result.exceptionOrNull()?.message}")
        }
    }

    private fun save(onSaved: () -> Unit) {
        val draft = currentDraft() ?: return toast("请先修正输入格式")
        when (val validation = ConfigValidator.validate(draft)) {
            is ValidationResult.Invalid -> toast(validation.issues.joinToString { it.message })
            is ValidationResult.Valid -> when (
                val result = repository.update(snapshot.version, validation.value)
            ) {
                is UpdateResult.Success -> {
                    toast("配置已保存，版本 ${result.snapshot.version}")
                    onSaved()
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

    private fun nativeFieldRow(binding: HostFieldBinding): ViewGroup {
        if (binding.editor is EditText || binding.editor is AutoCompleteTextView) {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                addView(TextView(context).apply {
                    text = binding.field.label
                    applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
                    setPadding(0, dp(12), 0, dp(2))
                }, LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
                binding.editor.apply {
                    if (this is EditText) {
                        hint = if (binding.field.key == "testText") binding.field.description else null
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                        setPadding(0, 0, 0, dp(8))
                        minHeight = dp(48)
                    }
                    if (this is AutoCompleteTextView) hint = binding.field.description
                }
                addView(binding.editor, LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
            }
        }
        if (binding.editor is Spinner) {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = dp(72)
                setPadding(dp(16), dp(4), dp(8), dp(4))
                addView(TextView(context).apply {
                    text = binding.field.label
                    applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                binding.editor.apply {
                    minimumWidth = dp(150)
                    background = null
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                }
                addView(binding.editor, LinearLayout.LayoutParams(dp(180), dp(64)))
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        if (binding.editor is android.widget.CompoundButton) {
            val switch = binding.editor as android.widget.CompoundButton
            switch.isEnabled = true
            // COUISwitch resets clickable/focusable during its own layout pass.
            // Make the whole preference row the stable touch target instead.
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { switch.toggle() }
            switch.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    switch.toggle()
                }
                true
            }
            // COUISwitch does not render the Preference title/summary itself.
            // Keep those labels as the left column, like the host row layout.
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            labels.addView(TextView(context).apply {
                text = binding.field.label
                applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
            })
            labels.addView(TextView(context).apply {
                text = binding.field.description
                applyHostTextStyle(this, hostSummaryStyle, 12f, android.R.attr.textColorSecondary)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            switch.text = ""
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(switch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return row
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        labels.addView(TextView(context).apply {
            text = binding.field.label
            applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
        })
        labels.addView(TextView(context).apply {
            text = binding.field.description
            applyHostTextStyle(this, hostSummaryStyle, 12f, android.R.attr.textColorSecondary)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        binding.editor.setPadding(dp(8), 0, 0, 0)
        row.addView(binding.editor, LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun adapter(values: List<String>) = ArrayAdapter(
        context,
        android.R.layout.simple_dropdown_item_1line,
        values
    )

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun divider() = View(context).apply {
        setBackgroundColor(resolveNamedColor("couiColorDivider", 0x22000000))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        }
    }

    private fun card(content: View): ViewGroup {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(fallbackCardColor())
                cornerRadius = dp(16).toFloat()
            }
            setPadding(0, 0, 0, 0)
        }
        card.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun fallbackCardColor(): Int {
        return if (isNightMode()) 0xff1c1c1c.toInt() else 0xffffffff.toInt()
    }

    private fun nativeButton(label: String): Button {
        val button = runCatching {
            val type = Class.forName("com.coui.appcompat.button.COUIButton", false, context.classLoader)
            type.getConstructor(Context::class.java).newInstance(context) as Button
        }.getOrElse { Button(context) }
        button.text = label
        button.minHeight = dp(44)
        button.setAllCaps(false)
        return button
    }

    private fun resolveNamedColor(name: String, fallback: Int): Int {
        val attr = context.resources.getIdentifier(name, "attr", context.packageName)
        return if (attr != 0) resolveColor(attr, fallback) else fallback
    }

    private fun resolveResourceColor(name: String, fallback: Int?): Int? {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        return if (id != 0) context.getColor(id) else fallback
    }

    private fun resolveDimension(name: String, fallback: Int): Int {
        val id = context.resources.getIdentifier(name, "dimen", context.packageName)
        return if (id != 0) context.resources.getDimensionPixelSize(id) else fallback
    }

    private fun findHostTextView(id: Int): TextView? {
        val activity = context as? android.app.Activity ?: return null
        val listId = activity.resources.getIdentifier("list", "id", activity.packageName)
        val root = activity.findViewById<View>(listId) ?: return null
        return findViewById(root, id) as? TextView
    }

    private fun findViewById(root: View, id: Int): View? {
        if (root.id == id) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findViewById(root.getChildAt(index), id)?.let { return it }
            }
        }
        return null
    }

    private fun applyHostTextStyle(
        target: TextView,
        source: TextView?,
        fallbackSizeSp: Float,
        fallbackColorAttr: Int
    ) {
        if (source != null) {
            target.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, source.textSize)
            target.setTextColor(source.textColors)
            target.typeface = source.typeface
            target.includeFontPadding = source.includeFontPadding
            target.letterSpacing = source.letterSpacing
            target.textScaleX = source.textScaleX
        } else {
            target.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fallbackSizeSp)
            target.setTextColor(resolveColor(fallbackColorAttr, 0xff777777.toInt()))
        }
    }

    private fun isNightMode(): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun resolveLightPageBackground(): Int {
        val resource = resolveResourceColor("coui_color_background_with_card", null)
        return resource ?: 0xfff7f7f7.toInt()
    }

    private fun resolveColor(attribute: Int): Int? {
        val typed = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attribute, typed, true)) {
            if (typed.resourceId != 0) context.getColor(typed.resourceId) else typed.data
        } else null
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int =
        resolveColor(attribute) ?: fallback
}
