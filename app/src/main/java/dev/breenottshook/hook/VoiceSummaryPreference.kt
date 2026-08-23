package dev.breenottshook.hook

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

class VoiceSummaryPreference(context: Context) : Preference(context) {
    private var rightSummary: CharSequence = ""

    fun setRightSummary(value: CharSequence) {
        rightSummary = value
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val frame = holder.itemView.findViewById<ViewGroup>(android.R.id.widget_frame) ?: return
        frame.removeAllViews()
        val text = TextView(context).apply {
            this.text = rightSummary
            setTextColor(Color.GRAY)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        frame.addView(text)
    }
}
