package dev.breenottshook.api

import java.time.Instant
import java.util.ArrayDeque

object DiagnosticLogStore {
    private const val MAX_ENTRIES = 400
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun append(level: String, message: String) {
        val line = "${Instant.now()} [$level] $message"
        if (entries.size >= MAX_ENTRIES) entries.removeFirst()
        entries.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    @Synchronized
    fun exportText(): String = snapshot().joinToString(separator = "\n")

    @Synchronized
    fun clear() = entries.clear()
}
