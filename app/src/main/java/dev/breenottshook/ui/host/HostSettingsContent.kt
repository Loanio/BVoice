package dev.breenottshook.ui.host

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.breenottshook.api.AndroidApiDiagnostics
import dev.breenottshook.api.CharacterCache
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.config.ConfigRepository
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.ui.ApiCatalogGateway
import dev.breenottshook.ui.ApiConnectionTester
import dev.breenottshook.ui.ContentProviderSettingsRepository
import dev.breenottshook.ui.SessionPreviewController
import dev.breenottshook.ui.SettingsOperationController
import dev.breenottshook.ui.SettingsSchema
import dev.breenottshook.ui.SettingsSection
import dev.breenottshook.ui.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private class SingleContentAdapter(private val content: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        object : RecyclerView.ViewHolder(FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }) {}

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

    override fun getItemCount(): Int = 1
}

class HostSettingsContent(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = GptSovitsClient(OkHttpClient(), diagnostics = AndroidApiDiagnostics)
    private val controller = SettingsOperationController(
        repository = ContentProviderSettingsRepository(ConfigRepository(context.contentResolver)),
        catalogGateway = ApiCatalogGateway(CharacterCache(loader = client::fetchCharacters)),
        connectionTester = ApiConnectionTester(client),
        previewController = SessionPreviewController(context, scope, client),
        operationScope = scope
    )
    private var uiState = controller.state.value
    private var bindings = HostFieldFactory.createAll(context, uiState.draft)
    private var automaticCheckStarted = false
    private var interactionListenersInstalled = false
    private var applyingEditorValues = false
    private var previewCard: ViewGroup? = null
    private var lastMessage: String? = null
    private val fieldRows = mutableMapOf<String, View>()
    private val fieldSeparators = mutableMapOf<String, View>()
    private val hostTitleStyle by lazy { findHostTextView(android.R.id.title) }
    private val hostSummaryStyle by lazy { findHostTextView(android.R.id.summary) }
    private val locale get() = context.resources.configuration.locales[0]
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        scope.launch {
            controller.state.collectLatest { renderState(it) }
        }
    }

    fun createPageContent(onClose: () -> Unit): View {
        val pageBackground = resolvePageBackground()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
            setBackgroundColor(pageBackground)
        }
        content.addView(actionButtons(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        installAddressBlurListener()
        installInteractionListeners()

        // The preview text belongs to the preview action and should remain
        // immediately below it instead of being buried in Basic settings.
        binding("testText")?.let { testTextBinding ->
            fieldRows["testText"] = nativeFieldRow(testTextBinding)
            content.addView(card(fieldRows.getValue("testText")), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }

        SettingsSection.entries.forEach { section ->
            val sectionBody = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(TextView(context).apply {
                text = HostStrings.sectionTitle(section, locale)
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
            val sectionBindings = bindings.filter { it.field.section == section && it.field.key != "testText" }
            sectionBindings.forEachIndexed { index, binding ->
                nativeFieldRow(binding).also { row ->
                    fieldRows[binding.field.key] = row
                    sectionBody.addView(row)
                }
                if (index < sectionBindings.lastIndex) {
                    divider().also { separator ->
                        fieldSeparators[binding.field.key] = separator
                        sectionBody.addView(separator)
                    }
                }
            }
            content.addView(card(sectionBody), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
        content.addView(actionCard(HostStrings.defaultLabel(locale), HostStrings.defaultSummary(locale)) {
                applyValuesToEditors(TtsConfig())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)).apply {
            bottomMargin = dp(8)
        })
        content.addView(actionCard(HostStrings.copyLogsLabel(locale), HostStrings.copyLogsSummary(locale)) {
            copyLogsToClipboard()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)).apply {
            bottomMargin = dp(8)
        })
        refreshVoiceModeVisibility()
        return createNativeSettingsRecyclerView().apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(pageBackground)
            layoutManager = LinearLayoutManager(context)
            adapter = SingleContentAdapter(content)
            postDelayed({ startAutomaticServiceCheck() }, HostSettingsInteractionPolicy.initialCheckDelayMillis)
            post { renderState(uiState) }
        }
    }

    private fun createNativeSettingsRecyclerView(): RecyclerView {
        val recyclerView = run {
            val className = "androidx.recyclerview.widget.COUIRecyclerView"
            val loaders = listOfNotNull(
                context.classLoader,
                RecyclerView::class.java.classLoader,
                Thread.currentThread().contextClassLoader
            ).distinct()
            loaders.asSequence()
                .mapNotNull { loader -> runCatching { Class.forName(className, true, loader) }.getOrNull() }
                .mapNotNull { type ->
                    runCatching {
                        type.getConstructor(Context::class.java).newInstance(context) as? RecyclerView
                    }.getOrNull()
                }
                .firstOrNull()
                ?: RecyclerView(context)
        }

        // COUIRecyclerView supplies the original spring/fling implementation. Disable
        // only the framework EdgeEffect so Android's stretch animation does not replace it.
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        runCatching {
            recyclerView.javaClass.getMethod("setIsUseNativeOverScroll", Boolean::class.javaPrimitiveType)
                .invoke(recyclerView, false)
        }
        runCatching {
            recyclerView.javaClass.getMethod("setOverScrollEnable", Boolean::class.javaPrimitiveType)
                .invoke(recyclerView, true)
        }
        runCatching {
            recyclerView.javaClass.getMethod("setOverScrollingFixed", Boolean::class.javaPrimitiveType)
                .invoke(recyclerView, true)
        }
        return recyclerView
    }

    fun dispose() {
        controller.close()
        scope.cancel()
    }

    internal fun resolvePageBackground(): Int = HostPageVisuals.backgroundColor(
        if (isNightMode()) 0xff000000.toInt() else resolveLightPageBackground(),
        isNightMode()
    )

    internal fun resolvePrimaryTextColor(): Int =
        resolveColor(android.R.attr.textColorPrimary, 0xff222222.toInt())

    private fun actionButtons() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
        previewCard = actionCard(
            HostStrings.previewLabel(locale),
            HostStrings.previewSummary(locale)
        ) { }
        previewCard?.setOnClickListener {
            if (controller.state.value.isPreviewing) {
                controller.stopPreview()
            } else {
                val draft = currentDraft()?.let(::normalizeVoiceForPreview)
                if (draft != null) {
                    // Pass the live Spinner values directly. Auto-save may still
                    // be persisting the previous role when the user taps quickly.
                    controller.edit { draft }
                    controller.preview(draft)
                }
            }
        }
        addView(previewCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
    }

    private fun actionCard(title: String, summary: String, onClick: () -> Unit): ViewGroup =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(fallbackCardColor())
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = title
                applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
            })
            addView(TextView(context).apply {
                text = summary
                applyHostTextStyle(this, hostSummaryStyle, 12f, android.R.attr.textColorSecondary)
            })
        }

    private fun updateActionCard(card: ViewGroup, title: String, summary: String) {
        (card.getChildAt(0) as? TextView)?.text = title
        (card.getChildAt(1) as? TextView)?.text = summary
    }

    private fun installAddressBlurListener() {
        (binding("baseUrl")?.editor as? EditText)?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && syncDraftToController()) controller.testConnectionAndRefresh()
        }
    }

    private fun startAutomaticServiceCheck() {
        if (automaticCheckStarted) return
        automaticCheckStarted = true
        if (syncDraftToController()) controller.initialize()
    }

    private fun bindCatalogDropdown(
        characterKey: String,
        emotionKey: String,
        next: CharacterCatalog
    ) {
        val characterEditor = binding(characterKey)?.editor as? Spinner ?: return
        val emotionEditor = binding(emotionKey)?.editor as? Spinner ?: return
        val selection = HostSettingsInteractionPolicy.selectInitialCatalogVoice(next, uiState.draft)
        characterEditor.setAdapter(adapter(next.characters.keys.sorted()))
        characterEditor.setSelection(next.characters.keys.sorted().indexOf(selection.character).coerceAtLeast(0))
        emotionEditor.setAdapter(adapter(next.characters[selection.character].orEmpty()))
        emotionEditor.setSelection(next.characters[selection.character].orEmpty().indexOf(selection.emotion).coerceAtLeast(0))
        characterEditor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val selected = HostSettingsInteractionPolicy.selectCatalogVoice(
                next,
                characterEditor.selectedItem?.toString().orEmpty(),
                ""
            )
            applyingEditorValues = true
            emotionEditor.setAdapter(adapter(next.characters[selected.character].orEmpty()))
            emotionEditor.setSelection(
                next.characters[selected.character].orEmpty().indexOf(selected.emotion).coerceAtLeast(0)
            )
            applyingEditorValues = false
            scheduleAutomaticSave()
            }
        }
        emotionEditor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                scheduleAutomaticSave()
        }
    }

    private fun currentDraft(): TtsConfig? {
        clearFieldErrors()
        return when (val result = HostInputValidation.validate(
            uiState.draft,
            bindings.map { HostInputValue(it.field.key, it.readRawValue()) }
        )) {
            is HostValidationResult.Valid -> result.config
            is HostValidationResult.Invalid -> {
                showFieldError(result.field, result.message)
                null
            }
        }
    }

    private fun normalizeVoiceForPreview(config: TtsConfig): TtsConfig {
        if (config.useManualVoice) return config
        val catalog = controller.state.value.catalog ?: return config
        val selected = HostSettingsInteractionPolicy.selectCatalogVoice(
            catalog,
            config.character,
            config.emotion
        )
        return config.copy(character = selected.character, emotion = selected.emotion)
    }

    private fun syncDraftToController(): Boolean {
        val draft = currentDraft() ?: return false
        controller.edit { draft }
        return true
    }

    private fun clearFieldErrors() {
        bindings.forEach { binding ->
            (binding.editor as? EditText)?.error = null
        }
    }

    private fun showFieldError(key: String, message: String) {
        val editor = binding(key)?.editor as? EditText ?: return
        editor.error = message
        if (!editor.hasFocus()) editor.requestFocus()
    }

    private fun renderState(next: SettingsUiState) = onMain {
        val previous = uiState
        uiState = next
        val presentation = HostSettingsPresentation.from(next)
        previewCard?.let { card ->
            card.isEnabled = presentation.previewEnabled
            card.alpha = if (presentation.previewEnabled) 1f else 0.5f
            updateActionCard(card, presentation.previewLabel, HostStrings.previewSummary(locale))
        }
        bindings.forEach { it.editor.isEnabled = presentation.controlsEnabled }

        if (next.catalog != null && next.catalog != previous.catalog) {
            applyingEditorValues = true
            bindCatalogDropdown("character", "emotion", next.catalog)
            applyingEditorValues = false
        }
        next.message
            ?.takeIf {
                it != lastMessage &&
                    !it.startsWith("正在") &&
                    !it.startsWith("试听失败") &&
                    !it.startsWith("已自动保存")
            }
            ?.let(::toast)
        lastMessage = next.message
    }

    private fun applyValuesToEditors(config: TtsConfig) {
        applyingEditorValues = true
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
        applyingEditorValues = false
        refreshVoiceModeVisibility()
        scheduleAutomaticSave()
    }

    private fun binding(key: String) = bindings.firstOrNull { it.field.key == key }

    private fun isFieldVisible(binding: HostFieldBinding): Boolean =
        binding.field.key !in setOf("manualCharacter", "manualEmotion") ||
            ((this.binding("useManualVoice")?.editor as? CompoundButton)?.isChecked == true)

    private fun installInteractionListeners() {
        if (interactionListenersInstalled) return
        interactionListenersInstalled = true
        bindings.forEach { binding ->
            when (val editor = binding.editor) {
                is CompoundButton -> editor.setOnCheckedChangeListener { _, _ ->
                    editor.contentDescription = HostFieldFactory.switchContentDescription(
                        HostStrings.fieldLabel(binding.field.key, locale),
                        editor.isChecked
                    )
                    if (binding.field.key == "useManualVoice") {
                        seedManualVoiceEditorsIfNeeded()
                        refreshVoiceModeVisibility()
                    }
                    scheduleAutomaticSave()
                }
                is Spinner -> editor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                        scheduleAutomaticSave()
                }
                is EditText -> editor.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = scheduleAutomaticSave()
                })
            }
        }
    }

    private fun refreshVoiceModeVisibility() {
        val manual = (binding("useManualVoice")?.editor as? CompoundButton)?.isChecked == true
        if (manual) seedManualVoiceEditorsIfNeeded()
        fieldRows["character"]?.visibility = if (manual) View.GONE else View.VISIBLE
        fieldRows["emotion"]?.visibility = if (manual) View.GONE else View.VISIBLE
        fieldRows["manualCharacter"]?.visibility = if (manual) View.VISIBLE else View.GONE
        fieldRows["manualEmotion"]?.visibility = if (manual) View.VISIBLE else View.GONE
        listOf("character", "manualCharacter", "manualEmotion").forEach { key ->
            fieldSeparators[key]?.visibility = if (manual && key == "character") View.GONE
                else if (!manual && key.startsWith("manual")) View.GONE else View.VISIBLE
        }
    }

    private fun seedManualVoiceEditorsIfNeeded() {
        val manualEnabled = (binding("useManualVoice")?.editor as? CompoundButton)?.isChecked == true
        if (!manualEnabled) return
        val manualCharacter = binding("manualCharacter")?.editor as? EditText ?: return
        val manualEmotion = binding("manualEmotion")?.editor as? EditText ?: return
        val character = (binding("character")?.editor as? Spinner)?.selectedItem?.toString().orEmpty()
        val emotion = (binding("emotion")?.editor as? Spinner)?.selectedItem?.toString().orEmpty()
        val seeded = HostSettingsInteractionPolicy.seedManualVoice(
            manualCharacter.text.toString(),
            manualEmotion.text.toString(),
            character,
            emotion
        )
        applyingEditorValues = true
        manualCharacter.setText(seeded.character)
        manualEmotion.setText(seeded.emotion)
        applyingEditorValues = false
        scheduleAutomaticSave()
    }

    private fun scheduleAutomaticSave() {
        if (applyingEditorValues) return
        syncDraftToController()
    }

    private fun nativeFieldRow(binding: HostFieldBinding): ViewGroup {
        if (binding.editor is EditText) {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                addView(TextView(context).apply {
                    text = HostStrings.fieldLabel(binding.field.key, locale)
                    applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
                    setPadding(0, dp(12), 0, dp(2))
                }, LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
                binding.editor.apply {
                    hint = if (binding.field.key == "testText") HostStrings.fieldDescription(binding.field.key, locale) else null
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(0, 0, 0, dp(8))
                    minHeight = dp(48)
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
                    text = HostStrings.fieldLabel(binding.field.key, locale)
                    applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                binding.editor.apply {
                    minimumWidth = dp(150)
                    background = null
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                    setPopupBackgroundDrawable(ColorDrawable(fallbackCardColor()))
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
            switch.isClickable = false
            switch.isFocusable = false
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = switch.contentDescription
            row.setOnClickListener {
                switch.toggle()
                row.contentDescription = switch.contentDescription
            }
            // COUISwitch does not render the Preference title/summary itself.
            // Keep those labels as the left column, like the host row layout.
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            labels.addView(TextView(context).apply {
                    text = HostStrings.fieldLabel(binding.field.key, locale)
                applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
            })
            labels.addView(TextView(context).apply {
                text = HostStrings.fieldDescription(binding.field.key, locale)
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
            text = HostStrings.fieldLabel(binding.field.key, locale)
            applyHostTextStyle(this, hostTitleStyle, 16f, android.R.attr.textColorPrimary)
        })
        labels.addView(TextView(context).apply {
            text = HostStrings.fieldDescription(binding.field.key, locale)
            applyHostTextStyle(this, hostSummaryStyle, 12f, android.R.attr.textColorSecondary)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        binding.editor.setPadding(dp(8), 0, 0, 0)
        row.addView(binding.editor, LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun adapter(values: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(
        context, 0, values
    ) {
        private fun row(dropdown: Boolean) = TextView(context).apply {
            setTextColor(resolvePrimaryTextColor())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(12), 0, dp(12), 0)
            gravity = if (dropdown) android.view.Gravity.CENTER_VERTICAL
            else android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            minimumHeight = dp(48)
            if (dropdown) setBackgroundColor(fallbackCardColor())
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (convertView as? TextView ?: row(false)).apply { text = getItem(position).orEmpty() }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            (convertView as? TextView ?: row(true)).apply { text = getItem(position).orEmpty() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun toast(message: String) = onMain {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun copyLogsToClipboard() = onMain {
        val text = dev.breenottshook.api.DiagnosticLogStore.exportText()
            .ifBlank { localized("暂无诊断日志", "No diagnostic logs yet") }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            toast(localized("无法访问剪贴板", "Clipboard is unavailable"))
            return@onMain
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("BreenoTTSHook diagnostics", text))
        toast(HostStrings.copyLogsDone(locale))
    }
    private fun localized(chinese: String, english: String): String =
        if (HostStrings.isEnglish(locale)) english else chinese
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun divider() = View(context).apply {
        setBackgroundColor(resolveNamedColor(
            "couiColorDivider",
            if (isNightMode()) 0x33ffffff else 0x22000000
        ))
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
