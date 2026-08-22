package dev.breenottshook.ui.host

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        val controller = HostSettingsDialog(activity)
        val page = createPageShell(activity, controller, toolbarTitle(activity))
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
        val state = PageState(root, page, list, appBar, controller, callback, invokedCallback)
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
                state.controller.dispose()
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
        controller: HostSettingsDialog,
        hostTitle: TextView?
    ): ViewGroup {
        val background = controller.resolvePageBackground()
        val toolbarId = activity.resources.getIdentifier("toolbar", "id", activity.packageName)
        val hostToolbar = activity.findViewById<View>(toolbarId)
        val pageToolbar = createNativeToolbar(activity, hostToolbar, hostTitle, controller) {
            close(activity)
        }
        val toolbarHeight = (hostToolbar?.measuredHeight ?: 0).takeIf { it > 0 } ?: dp(activity, 56)
        val statusBarHeight = statusBarHeight(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            addView(View(activity), LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight)
            addView(pageToolbar, LinearLayout.LayoutParams.MATCH_PARENT, toolbarHeight)
            addView(controller.createPageContent { close(activity) }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
    }

    private fun createNativeToolbar(
        activity: Activity,
        hostToolbar: View?,
        hostTitle: TextView?,
        controller: HostSettingsDialog,
        onBack: () -> Unit
    ): View {
        val host = hostToolbar as? Toolbar
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            val backIcon = host?.navigationIcon ?: findNavigationDrawable(hostToolbar, activity)
            if (backIcon != null) {
                addView(ImageButton(activity).apply {
                    setImageDrawable(backIcon)
                    setBackgroundColor(Color.TRANSPARENT)
                    contentDescription = "返回"
                    setOnClickListener { onBack() }
                }, LinearLayout.LayoutParams(dp(activity, 56), LinearLayout.LayoutParams.MATCH_PARENT))
            } else {
                addView(TextView(activity).apply {
                    text = "‹"
                    textSize = 48f
                    gravity = Gravity.CENTER
                    setTextColor(controller.resolvePrimaryTextColor())
                    contentDescription = "返回"
                    setOnClickListener { onBack() }
                }, LinearLayout.LayoutParams(dp(activity, 56), LinearLayout.LayoutParams.MATCH_PARENT))
            }
            addView(TextView(activity).apply {
                    text = "第三方音色"
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    typeface = hostTitle?.typeface ?: android.graphics.Typeface.DEFAULT
                    setTextColor(android.content.res.ColorStateList.valueOf(controller.resolvePrimaryTextColor()))
                    val px = hostTitle?.textSize ?: (20f * activity.resources.displayMetrics.scaledDensity)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
                    visibility = View.VISIBLE
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

    private fun statusBarHeight(context: android.content.Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }

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
        val controller: HostSettingsDialog,
        val callback: OnBackPressedCallback?,
        val invokedCallback: OnBackInvokedCallback?,
        var closing: Boolean = false
    )

    @Volatile
    private var restorePending: Boolean = false
}
