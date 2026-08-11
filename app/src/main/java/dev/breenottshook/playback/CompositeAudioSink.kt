package dev.breenottshook.playback

import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.PcmSegment

class CompositeAudioSink(
    private val primary: AudioSink?,
    private val fallback: AudioSink
) : AudioSink {
    private var active: AudioSink? = null

    override suspend fun open(format: PcmFormat) {
        active = primary?.let { candidate ->
            runCatching { candidate.open(format) }.fold(
                onSuccess = { candidate },
                onFailure = {
                    runCatching { candidate.cancel() }
                    fallback.open(format)
                    fallback
                }
            )
        } ?: fallback.also { it.open(format) }
    }

    override suspend fun write(segment: PcmSegment) {
        checkNotNull(active) { "Audio sink is not open" }.write(segment)
    }

    override suspend fun complete() {
        checkNotNull(active) { "Audio sink is not open" }.complete()
    }

    override suspend fun cancel() {
        active?.cancel()
        active = null
    }
}
