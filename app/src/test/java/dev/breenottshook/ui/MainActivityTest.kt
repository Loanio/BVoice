package dev.breenottshook.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @Test
    fun `launcher activity creates the configuration app without finishing`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNotNull(activity)
        assertFalse(activity.isFinishing)
    }
}
