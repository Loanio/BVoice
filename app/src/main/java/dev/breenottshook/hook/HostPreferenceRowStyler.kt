package dev.breenottshook.hook

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

internal object HostPreferenceRowStyler {
    private const val ASSIGNMENT_TAG = "dev.breenottshook.settings.entry.assignment"

    fun styleBoundItem(item: View, summaryText: String): Boolean {
        val context = item.context
        val assignmentId = context.resources.getIdentifier("assignment", "id", context.packageName)
        val assignment = findViewById(item, assignmentId) as? TextView
        val summary = findViewById(item, android.R.id.summary) as? TextView
        if (assignment != null) {
            assignment.text = summaryText
            assignment.visibility = View.VISIBLE
            summary?.visibility = View.GONE
            return true
        }

        val messageId = context.resources.getIdentifier("messageLayout", "id", context.packageName)
        val rowId = context.resources.getIdentifier("customLinearLayoutForList", "id", context.packageName)
        val message = findViewById(item, messageId) as? ViewGroup ?: return false
        val row = findViewById(item, rowId) as? LinearLayout ?: return false
        val reusableSummary = summary ?: return false
        return styleRow(context, message, row, reusableSummary, summaryText, assignmentId)
    }

    fun styleActivity(activity: Activity, titleText: String, summaryText: String): Boolean {
        val rootId = activity.resources.getIdentifier("list", "id", activity.packageName)
        val root = activity.findViewById<View>(rootId) ?: return false
        val title = findTextView(root) { it.text?.toString() == titleText } ?: return false
        val message = title.parent as? ViewGroup ?: return false
        val row = message.parent as? LinearLayout ?: return false
        val existing = row.findViewWithTag<View>(ASSIGNMENT_TAG) as? TextView
        if (existing != null) {
            existing.text = summaryText
            configureAssignment(activity, existing, existing.id)
            normalizeChildren(row, existing, activity)
            existing.visibility = View.VISIBLE
            row.requestLayout()
            return true
        }
        val summary = findViewById(message, android.R.id.summary) as? TextView ?: return false
        val assignmentId = activity.resources.getIdentifier("assignment", "id", activity.packageName)
        return styleRow(activity, message, row, summary, summaryText, assignmentId)
    }

    internal fun styleRow(
        context: Context,
        message: ViewGroup,
        row: LinearLayout,
        summary: TextView,
        summaryText: String,
        assignmentId: Int
    ): Boolean {
        if (message.parent !== row) return false
        summary.visibility = View.INVISIBLE
        summary.tag = ASSIGNMENT_TAG
        summary.text = summaryText
        configureAssignment(context, summary, assignmentId)
        (summary.parent as? ViewGroup)?.removeView(summary)
        row.addView(summary)
        normalizeChildren(row, summary, context)
        summary.visibility = View.VISIBLE
        row.requestLayout()
        return true
    }

    private fun configureAssignment(context: Context, assignment: TextView, assignmentId: Int) {
        val assignmentStyle = context.resources.getIdentifier(
            "TextAppearance_COUI_List_Assignment_End",
            "style",
            context.packageName
        )
        if (assignmentStyle != 0) assignment.setTextAppearance(assignmentStyle)
        assignment.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        assignment.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        assignment.maxLines = 2
        assignment.ellipsize = TextUtils.TruncateAt.END
        assignment.id = assignmentId
        assignment.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun normalizeChildren(row: LinearLayout, assignment: TextView, context: Context) {
        row.orientation = LinearLayout.HORIZONTAL
        (assignment.parent as? ViewGroup)?.removeView(assignment)
        while (row.childCount > 2) {
            row.removeViewAt(1)
        }
        while (row.childCount < 2) {
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }
        row.addView(assignment, 2)
    }

    private fun findTextView(root: View, predicate: (TextView) -> Boolean): TextView? {
        if (root is TextView && predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findTextView(root.getChildAt(index), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun findViewById(root: View, id: Int): View? {
        if (id == 0) return null
        if (root.id == id) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findViewById(root.getChildAt(index), id)?.let { return it }
            }
        }
        return null
    }
}
