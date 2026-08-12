package dev.breenottshook.hook

data class StreamFallback(
    val listener: Any?,
    val bundle: Any?,
    val chunks: List<String>
)

enum class AppendResult {
    Accepted,
    Ignored,
    Overflow
}

sealed interface FinishedStream {
    data object Empty : FinishedStream
    data class Ready(val text: String, val fallback: StreamFallback) : FinishedStream
    data class Overflow(val fallback: StreamFallback) : FinishedStream
}

class StreamUtteranceAccumulator(
    private val maxChars: Int = 100_000
) {
    private var listener: Any? = null
    private var bundle: Any? = null
    private var chunks = mutableListOf<String>()
    private var totalChars = 0
    private var active = false

    @Synchronized
    fun start(newListener: Any?, newBundle: Any?): StreamFallback? {
        val superseded = fallbackOrNull()
        listener = newListener
        bundle = newBundle
        chunks = mutableListOf()
        totalChars = 0
        active = true
        return superseded
    }

    @Synchronized
    fun append(text: String): AppendResult {
        if (!active || text.isBlank()) return AppendResult.Ignored
        chunks += text
        totalChars += text.length
        return if (totalChars > maxChars) AppendResult.Overflow else AppendResult.Accepted
    }

    @Synchronized
    fun finish(): FinishedStream {
        if (!active) return FinishedStream.Empty
        val fallback = fallbackOrNull() ?: return FinishedStream.Empty
        val text = chunks.joinToString(separator = "")
        val result = if (text.isEmpty()) {
            FinishedStream.Empty
        } else if (text.length > maxChars) {
            FinishedStream.Overflow(fallback)
        } else {
            FinishedStream.Ready(text, fallback)
        }
        clear()
        return result
    }

    @Synchronized
    fun cancel(): StreamFallback? {
        val fallback = fallbackOrNull()
        clear()
        return fallback
    }

    private fun fallbackOrNull(): StreamFallback? =
        if (!active || chunks.isEmpty()) null else StreamFallback(listener, bundle, chunks.toList())

    private fun clear() {
        listener = null
        bundle = null
        chunks = mutableListOf()
        totalChars = 0
        active = false
    }
}
