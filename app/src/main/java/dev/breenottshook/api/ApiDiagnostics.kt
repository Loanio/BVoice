package dev.breenottshook.api

import android.util.Log

sealed interface ApiDiagnosticEvent {
    data class RequestStarted(
        val endpoint: String,
        val stream: Boolean,
        val chars: Int,
        val connectTimeoutMs: Long,
        val readTimeoutMs: Long
    ) : ApiDiagnosticEvent
    data class ResponseReceived(val endpoint: String, val statusCode: Int, val contentType: String?) : ApiDiagnosticEvent
    data class HttpFailure(val endpoint: String, val statusCode: Int) : ApiDiagnosticEvent
    data class NetworkFailure(val endpoint: String, val exceptionType: String) : ApiDiagnosticEvent
}

object AndroidApiDiagnostics : (ApiDiagnosticEvent) -> Unit {
    override fun invoke(event: ApiDiagnosticEvent) {
        when (event) {
            is ApiDiagnosticEvent.RequestStarted -> Log.i(
                TAG,
                "api_request endpoint=${event.endpoint};stream=${event.stream};chars=${event.chars};connectMs=${event.connectTimeoutMs};readMs=${event.readTimeoutMs}"
            )
            is ApiDiagnosticEvent.ResponseReceived -> Log.i(
                TAG,
                "api_response endpoint=${event.endpoint};status=${event.statusCode};contentType=${event.contentType.orEmpty()}"
            )
            is ApiDiagnosticEvent.HttpFailure -> Log.w(
                TAG,
                "api_http_failed endpoint=${event.endpoint};status=${event.statusCode}"
            )
            is ApiDiagnosticEvent.NetworkFailure -> Log.w(
                TAG,
                "api_network_failed endpoint=${event.endpoint};type=${event.exceptionType}"
            )
        }
    }

    private const val TAG = "BreenoTTSHook"
}
