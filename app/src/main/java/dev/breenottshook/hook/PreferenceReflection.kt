package dev.breenottshook.hook

import java.lang.reflect.Method

object PreferenceReflection {
    fun textSummaryMethod(methods: Array<Method>): Method? = methods.firstOrNull {
        it.name == "setSummary" &&
            it.parameterTypes.contentEquals(arrayOf(CharSequence::class.java))
    }
}
