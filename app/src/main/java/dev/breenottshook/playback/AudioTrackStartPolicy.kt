package dev.breenottshook.playback

internal object AudioTrackStartPolicy {
    fun shouldStartOnWrite(bytes: ByteArray): Boolean = bytes.isNotEmpty()
}
