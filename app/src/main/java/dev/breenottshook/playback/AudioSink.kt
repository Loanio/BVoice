package dev.breenottshook.playback

import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.PcmSegment

interface AudioSink {
    suspend fun open(format: PcmFormat)
    suspend fun write(segment: PcmSegment)
    suspend fun complete()
    suspend fun cancel()
}
