package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeStopResolverTest {
    @Test
    fun `resolves native mute stop method by exact signature`() {
        assertEquals("q", NativeStopResolver.resolve(FakeManager::class.java)?.name)
        assertEquals("H0", NativeStopResolver.resolveEngineStop(FakeEngine::class.java)?.name)
    }

    private class FakeManager {
        fun q() = Unit
        fun q(reason: String) = Unit
    }

    private class FakeEngine {
        fun H0() = Unit
        fun H0(reason: String) = Unit
    }
}
