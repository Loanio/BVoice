package dev.breenottshook.session

data class TtsUtterance(
    val index: Int,
    val text: String
)

/** Splits an ordered stream of text chunks into non-blank, indexed utterances. */
fun splitUtterances(chunks: List<String>): List<TtsUtterance> {
    val result = mutableListOf<TtsUtterance>()
    val current = StringBuilder()

    fun emit() {
        val text = current.toString().trim()
        if (text.isNotEmpty()) {
            result += TtsUtterance(result.size, text)
        }
        current.clear()
    }

    chunks.forEach { chunk ->
        chunk.forEachIndexed { position, character ->
            when {
                character == '\r' -> {
                    if (position + 1 >= chunk.length || chunk[position + 1] != '\n') emit()
                }
                character == '\n' -> emit()
                else -> {
                    current.append(character)
                    if (character in SENTENCE_ENDINGS) emit()
                }
            }
        }
    }
    emit()
    return result
}

private val SENTENCE_ENDINGS = setOf('。', '！', '？', '!', '?', '；', ';')
