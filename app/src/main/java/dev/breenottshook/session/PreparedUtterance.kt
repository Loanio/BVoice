package dev.breenottshook.session

import dev.breenottshook.audio.PcmSegment

data class PreparedUtterance(
    val utterance: TtsUtterance,
    val segments: List<PcmSegment>
)
