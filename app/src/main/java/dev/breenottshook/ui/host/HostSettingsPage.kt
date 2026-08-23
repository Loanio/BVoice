package dev.breenottshook.ui.host

import android.app.Activity
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.WeakHashMap
import kotlin.math.max

object HostSettingsPage {
    private const val PAGE_TAG = "dev.breenottshook.settings.page"
    private val pages = WeakHashMap<Activity, PageState>()

    fun open(activity: Activity) {
        if (pages.containsKey(activity)) return
        val rootId = activity.resources.getIdentifier("root_view", "id", activity.packageName)
        val root = activity.findViewById<ViewGroup>(rootId) ?: return
        val listId = activity.resources.getIdentifier("list", "id", activity.packageName)
        val list = activity.findViewById<View>(listId)
        val appBarId = activity.resources.getIdentifier("appBarLayout", "id", activity.packageName)
        val appBar = activity.findViewById<View>(appBarId)
        val content = HostSettingsContent(activity)
        val page = createPageShell(activity, content, toolbarTitle(activity))
            .apply { tag = PAGE_TAG }
        val width = root.resources.displayMetrics.widthPixels.toFloat()
        page.translationX = width
        root.addView(
            page,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        page.elevation = dp(activity, 24).toFloat()
        page.bringToFront()
        val title = toolbarTitle(activity)
        installBackHandler(activity)
        val callback = (activity as? ComponentActivity)?.onBackPressedDispatcher?.let { dispatcher ->
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close(activity)
            }.also { dispatcher.addCallback(activity, it) }
        }
        val invokedCallback = if (android.os.Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback { close(activity) }.also {
                activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    it
                )
            }
        } else null
        val state = PageState(root, page, list, appBar, content, callback, invokedCallback)
        pages[activity] = state
        restorePending = true
        list?.animate()?.translationX(-width * 0.18f)?.setDuration(320L)?.start()
        appBar?.animate()?.translationX(-width * 0.18f)?.setDuration(320L)?.start()
        page.animate().translationX(0f)
            .setInterpolator(DecelerateInterpolator(1.5f)).setDuration(320L).start()
    }

    fun close(activity: Activity) {
        val state = pages[activity] ?: return
        if (state.closing) return
        state.closing = true
        restorePending = false
        val width = state.page.resources.displayMetrics.widthPixels.toFloat()
        state.page.animate().translationX(width)
            .setInterpolator(DecelerateInterpolator(1.5f)).setDuration(320L).withEndAction {
                pages.remove(activity)
                state.container.removeView(state.page)
                state.list?.translationX = 0f
                state.appBar?.translationX = 0f
                state.content.dispose()
                state.callback?.isEnabled = false
                state.callback?.remove()
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    state.invokedCallback?.let {
                        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                    }
                }
                installDefaultBackHandler(activity)
            }.start()
        state.list?.animate()?.translationX(0f)?.setDuration(320L)?.start()
        state.appBar?.animate()?.translationX(0f)?.setDuration(320L)?.start()
    }

    /** Recreate the injected page after the host Activity is recreated for a theme change. */
    fun restoreIfNeeded(activity: Activity) {
        if (!restorePending || pages.containsKey(activity)) return
        activity.window?.decorView?.postDelayed({
            if (restorePending && !pages.containsKey(activity)) open(activity)
        }, 450L)
    }

    private fun installBackHandler(activity: Activity) {
        val id = activity.resources.getIdentifier("coui_toolbar_back_view", "id", activity.packageName)
        activity.findViewById<View>(id)?.setOnClickListener { close(activity) }
    }

    private fun installDefaultBackHandler(activity: Activity) {
        val id = activity.resources.getIdentifier("coui_toolbar_back_view", "id", activity.packageName)
        activity.findViewById<View>(id)?.setOnClickListener { activity.onBackPressed() }
    }

    private fun toolbarTitle(activity: Activity): TextView? {
        val id = activity.resources.getIdentifier("toolbar", "id", activity.packageName)
        return findTextView(activity.findViewById(id))
    }

    private fun createPageShell(
        activity: Activity,
        content: HostSettingsContent,
        hostTitle: TextView?
    ): ViewGroup {
        val background = content.resolvePageBackground()
        val toolbarId = activity.resources.getIdentifier("toolbar", "id", activity.packageName)
        val hostToolbar = activity.findViewById<View>(toolbarId)
        val pageToolbar = createNativeToolbar(activity, hostToolbar, hostTitle, content) {
            close(activity)
        }
        val toolbarHeight = max(hostToolbar?.measuredHeight ?: 0, dp(activity, 56))
        val statusBarSpacer = View(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            addView(statusBarSpacer, LinearLayout.LayoutParams.MATCH_PARENT, 0)
            addView(pageToolbar, LinearLayout.LayoutParams.MATCH_PARENT, toolbarHeight)
            addView(content.createPageContent { close(activity) }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            setOnApplyWindowInsetsListener { _, insets ->
                val height = insets.getInsets(WindowInsets.Type.statusBars()).top
                if (statusBarSpacer.layoutParams.height != height) {
                    statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply {
                        this.height = height
                    }
                }
                insets
            }
            // The host settings view may already have dispatched insets before
            // this overlay is attached. Read the current inset as well so the
            // toolbar starts below the status bar instead of being covered by it.
            post {
                val height = activity.window?.decorView?.rootWindowInsets
                    ?.getInsets(WindowInsets.Type.statusBars())?.top
                    ?: activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                        .takeIf { it != 0 }
                        ?.let(activity.resources::getDimensionPixelSize)
                    ?: 0
                if (statusBarSpacer.layoutParams.height != height) {
                    statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply {
                        this.height = height
                    }
                }
            }
        }
    }

    private fun createNativeToolbar(
        activity: Activity,
        hostToolbar: View?,
        hostTitle: TextView?,
        content: HostSettingsContent,
        onBack: () -> Unit
    ): View {
        val host = hostToolbar as? Toolbar
        val toolbarTextColor = if (
            activity.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) Color.WHITE else content.resolvePrimaryTextColor()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(content.resolvePageBackground())
            minimumHeight = dp(activity, 56)
            elevation = dp(activity, 8).toFloat()
            val backIcon = host?.navigationIcon ?: findNavigationDrawable(hostToolbar, activity)
            if (backIcon != null) {
                addView(ImageButton(activity).apply {
                    setImageDrawable(backIcon.mutate())
                    setColorFilter(toolbarTextColor, PorterDuff.Mode.SRC_IN)
                    setBackgroundColor(Color.TRANSPARENT)
                    contentDescription = "返回"
                    visibility = View.VISIBLE
                    setOnClickListener { onBack() }
                }, LinearLayout.LayoutParams(dp(activity, 56), LinearLayout.LayoutParams.MATCH_PARENT))
            } else {
                addView(TextView(activity).apply {
                    text = "‹"
                    textSize = 48f
                    gravity = Gravity.CENTER
                    setTextColor(toolbarTextColor)
                    contentDescription = "返回"
                    visibility = View.VISIBLE
                    setOnClickListener { onBack() }
                }, LinearLayout.LayoutParams(dp(activity, 56), LinearLayout.LayoutParams.MATCH_PARENT))
            }
            addView(TextView(activity).apply {
                    text = HostStrings.title(activity.resources.configuration.locales[0])
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(dp(activity, 4), 0, dp(activity, 16), 0)
                    typeface = hostTitle?.typeface ?: android.graphics.Typeface.DEFAULT
                    setTextColor(android.content.res.ColorStateList.valueOf(toolbarTextColor))
                    val px = hostTitle?.textSize ?: (20f * activity.resources.displayMetrics.scaledDensity)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
                    visibility = View.VISIBLE
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun findNavigationDrawable(toolbar: View?, activity: Activity): android.graphics.drawable.Drawable? {
        val backId = activity.resources.getIdentifier("coui_toolbar_back_view", "id", activity.packageName)
        val candidate = activity.findViewById<View>(backId)
        if (candidate is ImageView) return candidate.drawable
        if (candidate?.background != null) return candidate.background
        return findImageDrawable(toolbar)
    }

    private fun findImageDrawable(view: View?): android.graphics.drawable.Drawable? {
        if (view is ImageView && view.drawable != null) return view.drawable
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findImageDrawable(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun findTextView(view: View?): TextView? = when (view) {
        is TextView -> view
        is ViewGroup -> (0 until view.childCount)
            .asSequence()
            .map { findTextView(view.getChildAt(it)) }
            .firstOrNull { it != null }
        else -> null
    }

    private data class PageState(
        val container: ViewGroup,
        val page: View,
        val list: View?,
        val appBar: View?,
        val content: HostSettingsContent,
        val callback: OnBackPressedCallback?,
        val invokedCallback: OnBackInvokedCallback?,
        var closing: Boolean = false
    )

    @Volatile
    private var restorePending: Boolean = false
}
