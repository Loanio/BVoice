package dev.breenottshook.hook

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostPreferenceRowStylerTest {
    @Test
    fun missingHostAssignmentMovesSummaryIntoTheThreeChildAssignmentSlot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val row = LinearLayout(context)
        val message = LinearLayout(context)
        val title = TextView(context)
        val summary = TextView(context).apply { id = android.R.id.summary }
        message.addView(title)
        message.addView(summary)
        row.addView(message)

        val styled = HostPreferenceRowStyler.styleRow(
            context = context,
            message = message,
            row = row,
            summary = summary,
            summaryText = "花火",
            assignmentId = 42
        )

        assertTrue(styled)
        assertEquals(3, row.childCount)
        assertSame(summary, row.getChildAt(2))
        assertEquals("花火", summary.text.toString())
        assertEquals(42, summary.id)
        assertEquals(View.VISIBLE, summary.visibility)
    }
}
