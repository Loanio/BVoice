package dev.breenottshook.audio

data class PcmSegment(
    val format: PcmFormat,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is PcmSegment && format == other.format && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * format.hashCode() + bytes.contentHashCode()
}

sealed interface DecodeFinish {
    data object Complete : DecodeFinish
    data class Truncated(val bufferedBytes: Int) : DecodeFinish
}
