package dev.breenottshook.ui.host

import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.text.InputType
import androidx.test.core.app.ApplicationProvider
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.ui.SettingsSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HostFieldFactoryTest {
    @Test
    fun `factory creates one editable native binding for every shared schema field`() {
        val bindings = HostFieldFactory.createAll(
            ApplicationProvider.getApplicationContext(),
            TtsConfig()
        )

        assertEquals(SettingsSchema.fields.map { it.key }, bindings.map { it.field.key })
        assertTrue(bindings.single { it.field.key == "enabled" }.editor is Switch)
        assertTrue(bindings.single { it.field.key == "baseUrl" }.editor is EditText)
        assertTrue(bindings.single { it.field.key == "textLanguage" }.editor is Spinner)
    }

    @Test
    fun `factory exposes numeric queue settings with configured values`() {
        val bindings = HostFieldFactory.createAll(
            ApplicationProvider.getApplicationContext(),
            TtsConfig(maxConcurrentSynthesis = 12, playbackIntervalMs = 450)
        )

        val concurrency = bindings.single { it.field.key == "maxConcurrentSynthesis" }
        val interval = bindings.single { it.field.key == "playbackIntervalMs" }

        assertTrue(concurrency.editor is EditText)
        assertTrue(interval.editor is EditText)
        assertEquals("12", concurrency.readRawValue())
        assertEquals("450", interval.readRawValue())
        assertTrue((concurrency.editor as EditText).inputType and InputType.TYPE_CLASS_NUMBER != 0)
        assertTrue((interval.editor as EditText).inputType and InputType.TYPE_CLASS_NUMBER != 0)
    }
}
