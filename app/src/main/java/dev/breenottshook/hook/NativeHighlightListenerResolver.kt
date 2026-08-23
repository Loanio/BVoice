package dev.breenottshook.hook

import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal object NativeHighlightListenerResolver {
    fun resolve(managerClass: Class<*>, log: (String) -> Unit = {}): Any? {
        val fields = generateSequence(managerClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { Modifier.isStatic(it.modifiers) }
            .toList()
        val ordered = fields.sortedBy { if (it.name == "f21459p") 0 else 1 }
        for (field in ordered) {
            val value = runCatching { field.isAccessible = true; field.get(null) }.getOrNull() ?: continue
            if (!looksLikeHighlightListener(field, value)) continue
            log("highlight_listener_resolve success=true;field=${field.name}")
            return value
        }
        log("highlight_listener_resolve failed=listener_not_found")
        return null
    }

    private fun looksLikeHighlightListener(field: Field, value: Any): Boolean {
        val typeName = field.type.name
        if (typeName.contains("StreamTtsListener")) return true
        return value.javaClass.methods.any {
            it.name == "onNextSliceStart" && it.parameterTypes.size == 1
        }
    }
}
