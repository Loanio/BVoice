package dev.breenottshook.hook

import java.lang.reflect.Method

data class EngineMethods(
    val speak: Method,
    val streamStart: Method,
    val streamChunk: Method,
    val streamEnd: Method
)

sealed interface EngineInstallResult {
    data class Ready(val methods: EngineMethods) : EngineInstallResult
    data class Disabled(val reason: String) : EngineInstallResult
}

object EngineTtsInstaller {
    fun resolve(clazz: Class<*>, descriptor: EngineTtsDescriptor): EngineInstallResult {
        fun find(target: MethodDescriptor): Method? = clazz.declaredMethods.singleOrNull {
            it.name == target.name && it.parameterCount == target.parameterTypeNames.size &&
                it.parameterTypes.map(Class<*>::getName) == target.parameterTypeNames &&
                it.returnType.name == target.returnTypeName
        }
        val methods = listOf(descriptor.speak, descriptor.streamStart, descriptor.streamChunk, descriptor.streamEnd)
        val resolved = methods.map(::find)
        if (resolved.any { it == null }) {
            return EngineInstallResult.Disabled(
                "engine descriptor mismatch;candidates=${describeCandidates(clazz)}"
            )
        }
        return EngineInstallResult.Ready(
            EngineMethods(resolved[0]!!, resolved[1]!!, resolved[2]!!, resolved[3]!!)
        )
    }

    private fun describeCandidates(clazz: Class<*>): String = clazz.declaredMethods
        .filter { method ->
            method.returnType == Void.TYPE && (
                (method.parameterCount == 4 && method.parameterTypes.firstOrNull() == String::class.java) ||
                    (method.parameterCount == 2 && method.parameterTypes.lastOrNull()?.name == "android.os.Bundle") ||
                    (method.parameterCount == 1 && method.parameterTypes.single() == String::class.java) ||
                    method.parameterCount == 0
                )
        }
        .joinToString("|") { method ->
            "${method.name}(${method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }})"
        }
        .take(1800)
}
