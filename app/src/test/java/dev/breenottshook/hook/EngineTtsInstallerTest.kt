package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTtsInstallerTest {
    private val descriptor = EngineTtsDescriptor(
        className = Fixture::class.java.name,
        speak = MethodDescriptor("m39754C0", listOf(String::class.java.name, Any::class.java.name, Any::class.java.name, Any::class.java.name), "void"),
        streamStart = MethodDescriptor("m39779P0", listOf(Any::class.java.name, Any::class.java.name), "void"),
        streamChunk = MethodDescriptor("m39777O0", listOf(String::class.java.name), "void"),
        streamEnd = MethodDescriptor("m39768J0", emptyList(), "void")
    )

    @Test
    fun `resolver returns every verified engine method exactly once`() {
        val result = EngineTtsInstaller.resolve(Fixture::class.java, descriptor)
        assertTrue(result is EngineInstallResult.Ready)
        val methods = (result as EngineInstallResult.Ready).methods
        assertEquals("m39754C0", methods.speak.name)
        assertEquals("m39779P0", methods.streamStart.name)
        assertEquals("m39777O0", methods.streamChunk.name)
        assertEquals("m39768J0", methods.streamEnd.name)
    }

    @Test
    fun `resolver rejects missing or overloaded signatures`() {
        assertTrue(EngineTtsInstaller.resolve(Missing::class.java, descriptor) is EngineInstallResult.Disabled)
        assertTrue(EngineTtsInstaller.resolve(WrongSignature::class.java, descriptor) is EngineInstallResult.Disabled)
    }

    class Fixture {
        fun m39754C0(text: String, listener: Any, bundle: Any, callback: Any) {}
        fun m39779P0(listener: Any, bundle: Any) {}
        fun m39777O0(text: String) {}
        fun m39768J0() {}
    }

    class Missing {
        fun m39754C0(text: String, listener: Any, bundle: Any, callback: Any) {}
    }

    class WrongSignature {
        fun m39754C0(text: String, listener: Any, bundle: Any, callback: Any, extra: Any) {}
        fun m39779P0(listener: Any, bundle: Any) {}
        fun m39777O0(text: String) {}
        fun m39768J0() {}
    }
}
