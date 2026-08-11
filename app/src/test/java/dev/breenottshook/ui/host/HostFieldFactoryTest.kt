package dev.breenottshook.ui.host

import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
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
}
