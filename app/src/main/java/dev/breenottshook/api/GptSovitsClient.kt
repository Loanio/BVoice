package dev.breenottshook.api

import dev.breenottshook.config.TtsConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GptSovitsClient(
    private val baseClient: OkHttpClient,
    private val maxResponseBytes: Long = 64L * 1024 * 1024
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCharacters(baseUrl: String): CharacterCatalog = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolve(baseUrl, "character_list"))
            .get()
            .build()
        execute(request) { response ->
            val body = response.body?.string()
                ?: throw ApiException(ApiError.Protocol("Empty character list response"))
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw ApiException(ApiError.Protocol("Invalid character list JSON"), it) }
            CharacterCatalog(
                root.mapValues { (_, value) ->
                    value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                }
            )
        }
    }

    suspend fun synthesize(
        text: String,
        config: TtsConfig,
        onBytes: suspend (ByteArray) -> Unit
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val bodyJson = buildJsonObject {
            put("text", text)
            put("character", config.effectiveCharacter)
            put("emotion", config.effectiveEmotion)
            put("text_language", config.textLanguage.apiValue)
            put("format", config.audioFormat.apiValue)
            put("top_k", config.topK)
            put("top_p", config.topP)
            put("temperature", config.temperature)
            put("batch_size", config.batchSize)
            put("speed", config.speed)
            put("save_temp", config.saveTemp)
            put("stream", config.stream)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(resolve(config.baseUrl, "tts"))
            .post(bodyJson)
            .build()
        val client = baseClient.newBuilder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val call = client.newCall(request)
        val cancellationHandle = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                throw ApiException(ApiError.Network(error.message ?: "Network failure"), error)
            }
            response.use {
                if (!it.isSuccessful) {
                    throw ApiException(
                        ApiError.Http(it.code, it.body?.string().orEmpty().take(1_024))
                    )
                }
                val source = it.body?.source()
                    ?: throw ApiException(ApiError.Protocol("Empty synthesis response"))
                var total = 0L
                val buffer = okio.Buffer()
                while (true) {
                    coroutineContext.ensureActive()
                    val read = source.read(buffer, NETWORK_CHUNK_SIZE)
                    if (read == -1L) break
                    total += read
                    if (total > maxResponseBytes) {
                        throw ApiException(ApiError.ResponseTooLarge(maxResponseBytes))
                    }
                    onBytes(buffer.readByteArray(read))
                }
                SynthesisResult(it.header("Content-Type"), total)
            }
        } finally {
            cancellationHandle.dispose()
        }
    }

    private suspend fun <T> execute(request: Request, block: suspend (okhttp3.Response) -> T): T {
        val call = baseClient.newCall(request)
        val cancellationHandle = kotlinx.coroutines.currentCoroutineContext().job
            .invokeOnCompletion { cause -> if (cause is CancellationException) call.cancel() }
        return try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                throw ApiException(ApiError.Network(error.message ?: "Network failure"), error)
            }
            response.use {
                if (!it.isSuccessful) {
                    throw ApiException(ApiError.Http(it.code, it.body?.string().orEmpty().take(1_024)))
                }
                block(it)
            }
        } finally {
            cancellationHandle.dispose()
        }
    }

    private fun resolve(baseUrl: String, relative: String) =
        baseUrl.toHttpUrl().resolve(relative)
            ?: throw ApiException(ApiError.Protocol("Invalid endpoint: $relative"))

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val NETWORK_CHUNK_SIZE = 8_192L
    }
}
