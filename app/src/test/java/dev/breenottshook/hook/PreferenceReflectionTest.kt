package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreferenceReflectionTest {
    @Test
    fun clickListenerSetterUsesTheHostMethodsParameterType() {
        val setter = PreferenceReflection.clickListenerSetter(HostPreference::class.java.methods)

        assertNotNull(setter)
        assertEquals(HostClickListener::class.java, setter?.parameterTypes?.single())
    }

    private fun interface HostClickListener {
        fun onPreferenceClick(): Boolean
    }

    @Suppress("UNUSED_PARAMETER")
    private class HostPreference {
        fun setOnPreferenceClickListener(listener: HostClickListener?) = Unit
    }
}
