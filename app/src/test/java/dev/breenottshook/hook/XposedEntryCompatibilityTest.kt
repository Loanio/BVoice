package dev.breenottshook.hook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.robv.android.xposed.IXposedHookLoadPackage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XposedEntryCompatibilityTest {
    @Test
    fun `declared xposed entry is loadable by legacy module loaders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryClassName = context.assets.open("xposed_init")
            .bufferedReader()
            .use { it.readText().trim() }
        val entryClass = Class.forName(entryClassName)

        assertTrue(
            "$entryClassName must implement IXposedHookLoadPackage",
            IXposedHookLoadPackage::class.java.isAssignableFrom(entryClass)
        )
    }
}
