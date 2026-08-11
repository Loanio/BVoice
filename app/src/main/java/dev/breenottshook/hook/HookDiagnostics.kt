package dev.breenottshook.hook

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object HookDiagnostics {
    fun utterance(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
            .take(12)
        return "chars=${text.length},sha256=$digest"
    }
}
