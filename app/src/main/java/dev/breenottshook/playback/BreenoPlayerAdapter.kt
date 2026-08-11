package dev.breenottshook.playback

import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.PcmSegment

interface BreenoPcmBridge {
    suspend fun start(format: PcmFormat)
    suspend fun push(bytes: ByteArray)
    suspend fun finish()
    suspend fun stop()
}

class BreenoPlayerAdapter(
    private val bridge: BreenoPcmBridge
) : AudioSink {
    override suspend fun open(format: PcmFormat) = bridge.start(format)
    override suspend fun write(segment: PcmSegment) = bridge.push(segment.bytes)
    override suspend fun complete() = bridge.finish()
    override suspend fun cancel() = bridge.stop()
}
