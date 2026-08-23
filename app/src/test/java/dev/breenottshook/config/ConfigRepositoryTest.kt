package dev.breenottshook.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {

    @Test
    fun `new config defaults diagnostic logging to errors only`() {
        assertEquals(LogLevel.ERROR, TtsConfig().logLevel)
    }

    @Test
    fun `new store returns version zero defaults`() {
        val store = AtomicConfigStore(InMemoryConfigPersistence())

        assertEquals(ConfigSnapshot(0, TtsConfig()), store.read())
    }

    @Test
    fun `successful update normalizes config and increments version`() {
        val store = AtomicConfigStore(InMemoryConfigPersistence())

        val result = store.update(0, TtsConfig(baseUrl = "https://tts.example.test"))

        assertEquals(
            UpdateResult.Success(ConfigSnapshot(1, TtsConfig(baseUrl = "https://tts.example.test/"))),
            result
        )
        assertEquals(1, store.read().version)
    }

    @Test
    fun `stale expected version cannot overwrite newer config`() {
        val store = AtomicConfigStore(InMemoryConfigPersistence())
        store.update(0, TtsConfig(character = "花火"))

        val result = store.update(0, TtsConfig(character = "胡桃"))

        assertEquals(UpdateResult.VersionConflict(currentVersion = 1), result)
        assertEquals("花火", store.read().value.character)
    }

    @Test
    fun `invalid update leaves last valid snapshot unchanged`() {
        val store = AtomicConfigStore(InMemoryConfigPersistence())
        store.update(0, TtsConfig(speed = 1.2))

        val result = store.update(1, TtsConfig(speed = 0.0))

        assertTrue(result is UpdateResult.Invalid)
        assertEquals(1.2, store.read().value.speed, 0.0)
        assertEquals(1, store.read().version)
    }

    @Test
    fun `hook cache retains last valid snapshot after IPC failure`() {
        var shouldFail = false
        val source = ConfigSnapshotSource {
            if (shouldFail) error("provider unavailable")
            ConfigSnapshot(4, TtsConfig(character = "花火"))
        }
        val cache = HookConfigCache(source)
        cache.refresh()
        shouldFail = true

        val result = cache.refresh()

        assertTrue(result.isFailure)
        assertEquals(4, cache.current().version)
        assertEquals("花火", cache.current().value.character)
    }

    @Test
    fun `caller authorization accepts only module and Breeno packages`() {
        assertTrue(ConfigCallerAuthorizer.isAllowed(setOf(ConfigContract.MODULE_PACKAGE)))
        assertTrue(ConfigCallerAuthorizer.isAllowed(setOf(ConfigContract.BREENO_PACKAGE)))
        assertTrue(!ConfigCallerAuthorizer.isAllowed(setOf("com.example.attacker")))
        assertTrue(!ConfigCallerAuthorizer.isAllowed(emptySet()))
    }

    private class InMemoryConfigPersistence : ConfigPersistence {
        private var payload: PersistedConfig? = null
        override fun read(): PersistedConfig? = payload
        override fun write(value: PersistedConfig): Boolean {
            payload = value
            return true
        }
    }
}
