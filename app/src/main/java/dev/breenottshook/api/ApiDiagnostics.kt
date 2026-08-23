package dev.breenottshook.api

import android.util.Log

sealed interface ApiDiagnosticEvent {
    data class RequestStarted(
        val endpoint: String,
        val stream: Boolean,
        val chars: Int,
        val connectTimeoutMs: Long,
        val readTimeoutMs: Long,
        val character: String = "",
        val emotion: String = "",
        val attempt: Int = 1
    ) : ApiDiagnosticEvent
    data class ResponseReceived(val endpoint: String, val statusCode: Int, val contentType: String?, val attempt: Int = 1) : ApiDiagnosticEvent
    data class HttpFailure(
        val endpoint: String,
        val statusCode: Int,
        val body: String = "",
        val attempt: Int = 1
    ) : ApiDiagnosticEvent
    data class NetworkFailure(val endpoint: String, val exceptionType: String) : ApiDiagnosticEvent
}

object AndroidApiDiagnostics : (ApiDiagnosticEvent) -> Unit {
    override fun invoke(event: ApiDiagnosticEvent) {
        when (event) {
            is ApiDiagnosticEvent.RequestStarted -> Log.i(
                TAG,
                "api_request endpoint=${event.endpoint};attempt=${event.attempt};stream=${event.stream};chars=${event.chars};character=${event.character};emotion=${event.emotion};connectMs=${event.connectTimeoutMs};readMs=${event.readTimeoutMs}"
            ).also {
                DiagnosticLogStore.append(
                    "INFO",
                    "api_request endpoint=${event.endpoint};attempt=${event.attempt};stream=${event.stream};chars=${event.chars};character=${event.character};emotion=${event.emotion};connectMs=${event.connectTimeoutMs};readMs=${event.readTimeoutMs}"
                )
            }
            is ApiDiagnosticEvent.ResponseReceived -> Log.i(
                TAG,
                "api_response endpoint=${event.endpoint};attempt=${event.attempt};status=${event.statusCode};contentType=${event.contentType.orEmpty()}"
            ).also {
                DiagnosticLogStore.append("INFO", "api_response endpoint=${event.endpoint};attempt=${event.attempt};status=${event.statusCode};contentType=${event.contentType.orEmpty()}")
            }
            is ApiDiagnosticEvent.HttpFailure -> Log.w(
                TAG,
                "api_http_failed endpoint=${event.endpoint};attempt=${event.attempt};status=${event.statusCode};body=${event.body.take(512)}"
            ).also {
                DiagnosticLogStore.append("WARN", "api_http_failed endpoint=${event.endpoint};attempt=${event.attempt};status=${event.statusCode};body=${event.body.take(512)}")
            }
            is ApiDiagnosticEvent.NetworkFailure -> Log.w(
                TAG,
                "api_network_failed endpoint=${event.endpoint};type=${event.exceptionType}"
            ).also { DiagnosticLogStore.append("WARN", "api_network_failed endpoint=${event.endpoint};type=${event.exceptionType}") }
        }
    }

    private const val TAG = "BreenoTTSHook"
}
