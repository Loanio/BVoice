package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredInstallerTest {
    @Test
    fun `installs when deferred context becomes available`() {
        val installed = mutableListOf<String>()
        var deferred: ((String) -> Unit)? = null
        val installer = DeferredInstaller<String> { installed += it }

        installer.start(
            current = null,
            defer = { deferred = it }
        )
        assertEquals(emptyList<String>(), installed)

        deferred?.invoke("application")

        assertEquals(listOf("application"), installed)
    }

    @Test
    fun `installs at most once when lifecycle repeats`() {
        val installed = mutableListOf<String>()
        var deferred: ((String) -> Unit)? = null
        val installer = DeferredInstaller<String> { installed += it }

        installer.start(
            current = null,
            defer = { deferred = it }
        )
        deferred?.invoke("first")
        deferred?.invoke("second")

        assertEquals(listOf("first"), installed)
    }
}
