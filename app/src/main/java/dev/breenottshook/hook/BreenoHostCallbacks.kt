package dev.breenottshook.hook

import dev.breenottshook.session.TtsCallbacks
import dev.breenottshook.session.TtsUtterance
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object BreenoHostCallbacks {
    const val MODULE_ERROR_CODE = -1
    const val MODULE_INTERRUPTED_REASON = 1

    fun normal(listener: Any?): TtsCallbacks = ReflectiveCallbacks(
        listener = listener,
        startName = "onSpeakStart",
        completeName = "onSpeakCompleted",
        errorName = "onTtsError",
        cancelName = "onSpeakInterrupted"
    )

    fun stream(listener: Any?): TtsCallbacks = ReflectiveCallbacks(
        listener = listener,
        startName = "onSpeakBegin",
        completeName = "onEnd",
        streamCompleteName = "onCompleted",
        utteranceStartName = "onNextSliceStart"
    )

    private class ReflectiveCallbacks(
        private val listener: Any?,
        private val startName: String,
        private val completeName: String,
        private val errorName: String? = null,
        private val cancelName: String? = null,
        private val streamCompleteName: String? = null,
        private val utteranceStartName: String? = null,
        private val diagnostic: (String, Throwable?) -> Unit = { _, _ -> }
    ) : TtsCallbacks {
        private var terminal = false

        override fun onStarted() {
            invoke(startName)
        }

        override fun onUtteranceStarted(utterance: TtsUtterance) {
            utteranceStartName?.let { invokeSlice(it, utterance) }
        }

        @Synchronized
        override fun onCompleted() {
            if (terminal) return
            terminal = true
            invoke(completeName)
            streamCompleteName?.let { invoke(it, null) }
        }

        @Synchronized
        override fun onError(error: Throwable) {
            if (terminal) return
            terminal = true
            if (streamCompleteName != null) {
                invoke(completeName)
                invoke(streamCompleteName, speechException(error))
            } else {
                invoke(errorName ?: return, MODULE_ERROR_CODE, error.javaClass.simpleName)
            }
        }

        @Synchronized
        override fun onCancelled(reason: String) {
            if (terminal) return
            terminal = true
            if (streamCompleteName != null) {
                invoke(completeName)
                invoke(streamCompleteName, speechException(IllegalStateException(reason)))
            } else {
                invoke(cancelName ?: return, MODULE_INTERRUPTED_REASON)
            }
        }

        private fun invoke(name: String, vararg args: Any?) {
            val target = listener ?: return
            val method = MethodCache.resolve(target.javaClass, name, args)
                ?: return
            runCatching { method.invoke(target, *args) }
                .onFailure { diagnostic(name, it) }
        }

        private fun invokeSlice(name: String, utterance: TtsUtterance) {
            val target = listener ?: return
            val method = (target.javaClass.methods.asSequence() + target.javaClass.declaredMethods.asSequence())
                .firstOrNull { it.name == name && it.parameterCount == 1 }
                ?.apply { isAccessible = true } ?: return
            val type = method.parameterTypes.single()
            val value = runCatching {
                type.getConstructor(Int::class.javaPrimitiveType, String::class.java, Long::class.javaPrimitiveType)
                    .newInstance(utterance.index, utterance.text, utterance.text.length.toLong())
            }.getOrElse {
                diagnostic("$name:payload", it)
                return
            }
            runCatching { method.invoke(target, value) }
                .onFailure { diagnostic(name, it) }
        }

        private fun speechException(error: Throwable): Any? = runCatching {
            val type = Class.forName("com.heytap.voiceassistant.sdk.tts.SpeechException", false, listener?.javaClass?.classLoader)
            type.getConstructor(Int::class.javaPrimitiveType, String::class.java)
                .newInstance(MODULE_ERROR_CODE, error.javaClass.simpleName)
        }.onFailure { diagnostic("SpeechException", it) }.getOrNull()
    }

    private object MethodCache {
        private val cache = ConcurrentHashMap<String, Method?>()

        fun resolve(type: Class<*>, name: String, args: Array<out Any?>): Method? {
            val key = buildString {
                append(type.name).append('#').append(name).append('(')
                args.forEach { append(it?.javaClass?.name ?: "null").append(';') }
            }
            cache[key]?.let { return it }
            val resolved = (type.methods.asSequence() + type.declaredMethods.asSequence()).firstOrNull { method ->
                method.name == name && method.parameterCount == args.size
            }?.apply { isAccessible = true }
            if (resolved != null) cache[key] = resolved
            return resolved
        }

    }
}
