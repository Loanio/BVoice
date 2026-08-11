package dev.breenottshook.api

import dev.breenottshook.config.TextLanguage
import dev.breenottshook.config.TtsConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GptSovitsClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GptSovitsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GptSovitsClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `decodes character and emotion catalog`() = runTest {
        server.enqueue(MockResponse().setBody("""{"花火":["default","平静"],"胡桃":["default"]}"""))

        val catalog = client.fetchCharacters(server.url("/").toString())

        assertEquals(listOf("default", "平静"), catalog.characters["花火"])
        assertEquals(listOf("default"), catalog.characters["胡桃"])
        assertEquals("/character_list", server.takeRequest().path)
    }

    @Test
    fun `posts every synthesis field and forwards response bytes`() = runTest {
        val expectedAudio = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write(expectedAudio))
        )
        val received = mutableListOf<Byte>()
        val config = TtsConfig(
            baseUrl = server.url("/").toString(),
            character = "花火",
            emotion = "平静",
            textLanguage = TextLanguage.CHINESE,
            topK = 8,
            topP = 0.75,
            temperature = 0.9,
            batchSize = 2,
            speed = 1.1,
            saveTemp = true,
            stream = true
        )

        val result = client.synthesize("你好", config) { bytes ->
            received += bytes.toList()
        }

        val request = server.takeRequest()
        val json = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("/tts", request.path)
        assertEquals("POST", request.method)
        assertEquals("你好", json.getValue("text").jsonPrimitive.content)
        assertEquals("花火", json.getValue("character").jsonPrimitive.content)
        assertEquals("平静", json.getValue("emotion").jsonPrimitive.content)
        assertEquals("中文", json.getValue("text_language").jsonPrimitive.content)
        assertEquals("8", json.getValue("top_k").jsonPrimitive.content)
        assertEquals("0.75", json.getValue("top_p").jsonPrimitive.content)
        assertEquals("0.9", json.getValue("temperature").jsonPrimitive.content)
        assertEquals("2", json.getValue("batch_size").jsonPrimitive.content)
        assertEquals("1.1", json.getValue("speed").jsonPrimitive.content)
        assertEquals("true", json.getValue("save_temp").jsonPrimitive.content)
        assertEquals("true", json.getValue("stream").jsonPrimitive.content)
        assertArrayEquals(expectedAudio, received.toByteArray())
        assertEquals(5, result.byteCount)
        assertEquals("audio/wav", result.contentType)
    }

    @Test
    fun `maps non success response to typed HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("model unavailable"))

        val failure = runCatching {
            client.synthesize(
                text = "你好",
                config = TtsConfig(baseUrl = server.url("/").toString())
            ) {}
        }.exceptionOrNull()

        assertTrue(failure is ApiException)
        assertEquals(503, ((failure as ApiException).error as ApiError.Http).statusCode)
    }

    @Test
    fun `rejects response exceeding configured byte limit`() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(9) { 7 })))
        val limited = GptSovitsClient(OkHttpClient(), maxResponseBytes = 8)

        val failure = runCatching {
            limited.synthesize(
                text = "你好",
                config = TtsConfig(baseUrl = server.url("/").toString())
            ) {}
        }.exceptionOrNull()

        assertEquals(ApiError.ResponseTooLarge(8), (failure as ApiException).error)
    }

    @Test
    fun `character cache returns stale catalog when refresh fails`() = runTest {
        var now = 1_000L
        var fail = false
        val expected = CharacterCatalog(mapOf("花火" to listOf("default")))
        val cache = CharacterCache(
            ttlMs = 100,
            clock = { now },
            loader = {
                if (fail) error("offline") else expected
            }
        )
        assertEquals(CatalogState.Fresh(expected), cache.getOrFetch("http://tts/", false))
        now = 1_200L
        fail = true

        val stale = cache.getOrFetch("http://tts/", false)

        assertEquals(CatalogState.Stale(expected, "offline"), stale)
    }
}
