package dev.breenottshook.session

fun interface OriginalCall {
    fun resume()
}

interface TtsCallbacks {
    fun onStarted()
    fun onUtteranceStarted(utterance: TtsUtterance) = Unit
    fun onCompleted()
    fun onError(error: Throwable)
    fun onCancelled(reason: String)
}

data class TtsInvocation(
    val text: String,
    val originalCall: OriginalCall,
    val callbacks: TtsCallbacks
)
