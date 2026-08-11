package dev.breenottshook.api

import java.io.IOException

sealed interface ApiError {
    data class Http(val statusCode: Int, val message: String) : ApiError
    data class Network(val message: String) : ApiError
    data class Protocol(val message: String) : ApiError
    data class ResponseTooLarge(val maxBytes: Long) : ApiError
}

class ApiException(
    val error: ApiError,
    cause: Throwable? = null
) : IOException(error.toString(), cause)

data class SynthesisResult(
    val contentType: String?,
    val byteCount: Long
)
