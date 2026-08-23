package dev.breenottshook.hook

import org.junit.Assert.assertSame
import org.junit.Test

class NativeHighlightListenerResolverTest {
    @Test
    fun `resolves listener when obfuscated field name changes`() {
        val logs = mutableListOf<String>()
        val resolved = NativeHighlightListenerResolver.resolve(FakeManager::class.java) { logs += it }
        assertSame(FakeManager.listener, resolved)
        assert(logs.single().contains("success=true"))
    }

    private class FakeManager {
        companion object {
            @JvmField val renamedField = FakeListener()
            @JvmField val listener = renamedField
        }
    }

    private class FakeListener {
        @Suppress("UNUSED_PARAMETER")
        fun onNextSliceStart(info: Any) = Unit
    }
}
