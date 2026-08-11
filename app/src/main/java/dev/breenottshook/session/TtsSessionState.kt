package dev.breenottshook.session

sealed interface TtsSessionState {
    data object Idle : TtsSessionState
    data class Requesting(val generation: Long) : TtsSessionState
    data class Buffering(val generation: Long) : TtsSessionState
    data class Playing(val generation: Long) : TtsSessionState
    data class Completed(val generation: Long) : TtsSessionState
    data class Failed(val generation: Long, val message: String) : TtsSessionState
    data class Cancelled(val generation: Long, val reason: String) : TtsSessionState
}
