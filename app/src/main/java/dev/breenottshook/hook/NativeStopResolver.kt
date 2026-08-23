package dev.breenottshook.hook

import java.lang.reflect.Method

internal object NativeStopResolver {
    fun resolve(managerClass: Class<*>): Method? = (managerClass.declaredMethods.asSequence() + managerClass.methods.asSequence())
        .distinctBy { it.toGenericString() }
        .singleOrNull {
        (it.name == "q" || it.name == "m25028q") && it.parameterCount == 0 && it.returnType == Void.TYPE
    }

    fun resolveEngineStop(engineClass: Class<*>): Method? = engineClass.declaredMethods.singleOrNull {
        it.name == "H0" && it.parameterCount == 0 && it.returnType == Void.TYPE
    }
}
