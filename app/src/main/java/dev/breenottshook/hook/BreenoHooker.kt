package dev.breenottshook.hook

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import dev.breenottshook.config.ConfigContract
import dev.breenottshook.config.ConfigRepository
import dev.breenottshook.config.HookConfigCache
import dev.breenottshook.api.AndroidApiDiagnostics
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.playback.AudioTrackSink
import dev.breenottshook.session.GptSovitsEngine
import dev.breenottshook.session.TtsSessionCoordinator
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BreenoHooker : YukiBaseHooker() {
    override fun onHook() {
        DeferredInstaller<Context>(::installForContext).start(
            current = appContext,
            defer = { install ->
                onAppLifecycle {
                    onCreate { install(this) }
                }
            }
        )
    }

    private fun installForContext(context: Context) {
        Log.i(LOG_TAG, "engine_install stage=begin")
        val status = HookStatusPublisher(context)
        runCatching {
            val packageVersion = context.packageManager.packageVersionName(packageName)
            val profiles = listOf(Breeno1183Profile(), Breeno1299Profile())
            val selector = ProfileSelector(profiles)
            val engineProfile = Breeno1299Profile()
            val engineRoute = engineProfile.ttsRoute as TtsRoute.Engine
            val engineResolved = runCatching {
                val engineClass = Class.forName(
                    engineRoute.descriptor.className,
                    false,
                    appClassLoader
                )
                EngineTtsInstaller.resolve(engineClass, engineRoute.descriptor) is EngineInstallResult.Ready
            }.getOrDefault(false)
            Log.i(
                LOG_TAG,
                "engine_install engine_probe=${engineRoute.descriptor.className};resolved=$engineResolved;packageVersion=$packageVersion"
            )
            val selection = if (engineResolved) {
                ProfileSelection.Selected(engineProfile)
            } else {
                selector.select(
                    packageVersion = packageVersion,
                    classProbe = ClassProbe { it.toClassOrNull() != null }
                )
            }
            when (selection) {
                is ProfileSelection.Unsupported -> status.publish(
                    state = "unsupported",
                    detail = "version=${selection.packageVersion}"
                )
                is ProfileSelection.Ambiguous -> status.publish(
                    state = "disabled",
                    detail = "ambiguous=${selection.profileIds.joinToString()}"
                )
                is ProfileSelection.Selected -> when (val route = selection.profile.ttsRoute) {
                    is TtsRoute.WebSocket -> installTransportFallback(
                        context = context,
                        profile = selection.profile,
                        status = status
                    )
                    is TtsRoute.Engine -> installEngine(
                        context = context,
                        profile = selection.profile,
                        route = route,
                        status = status
                    )
                }
            }
        }.onFailure { error ->
            Log.e(LOG_TAG, "engine_install stage=failed;type=${error.javaClass.simpleName}", error)
        }
    }

    private fun installEngine(
        context: Context,
        profile: VersionProfile,
        route: TtsRoute.Engine,
        status: HookStatusPublisher
    ) {
        val clazz = route.descriptor.className.toClassOrNull()
            ?: return status.publish("disabled", "engine class disappeared after profile selection")
        val resolved = EngineTtsInstaller.resolve(clazz, route.descriptor)
        if (resolved !is EngineInstallResult.Ready) {
            return status.publish("disabled", (resolved as EngineInstallResult.Disabled).reason)
        }
        Log.i(LOG_TAG, "engine_install stage=resolved")
        val configCache = HookConfigCache(ConfigRepository(context.contentResolver))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val coordinator = TtsSessionCoordinator(
            scope = scope,
            configProvider = { configCache.current().value },
            synthesisEngine = GptSovitsEngine(GptSovitsClient(okhttp3.OkHttpClient(), diagnostics = AndroidApiDiagnostics)),
            sinkProvider = { AudioTrackSink(context.applicationContext) }
        )
        val runtime = BreenoEngineRuntime(
            configProvider = { configCache.refresh(); configCache.current().value },
            submit = { coordinator.submit(it) },
            submitStream = { utterances, callbacks, originalCall ->
                coordinator.submitStream(utterances, callbacks, originalCall)
            },
            cancelHandler = { coordinator.cancelActive(it) },
            scope = scope,
            diagnostics = { message -> Log.i(LOG_TAG, message) }
        )
        val methods = resolved.methods
        val bypass = ConcurrentHashMap.newKeySet<String>()
        fun key(method: Method, receiver: Any, args: Array<Any?>): String =
            buildString {
                append(System.identityHashCode(receiver)).append(':').append(method.name)
                args.forEach { append(':').append(System.identityHashCode(it)) }
            }
        methods.speak.hook {
            before {
                val receiver = instanceOrNull ?: return@before
                val callKey = key(methods.speak, receiver, args)
                if (bypass.remove(callKey)) return@before
                val text = args.getOrNull(0) as? String ?: return@before
                val listener = args.getOrNull(1)
                val original: () -> Unit = { bypass += callKey; runCatching { methods.speak.invoke(receiver, *args) }; Unit }
                if (runtime.onSpeak(text, listener, original)) result = null
            }
        }
        Log.i(LOG_TAG, "engine_install stage=hooked;method=${methods.speak.name}")
        methods.streamStart.hook {
            before {
                val receiver = instanceOrNull ?: return@before
                val callKey = key(methods.streamStart, receiver, args)
                if (bypass.remove(callKey)) return@before
                val original: () -> Unit = { bypass += callKey; runCatching { methods.streamStart.invoke(receiver, *args) }; Unit }
                if (runtime.onStreamStart(args.getOrNull(0), args.getOrNull(1), original)) result = null
            }
        }
        Log.i(LOG_TAG, "engine_install stage=hooked;method=${methods.streamStart.name}")
        methods.streamChunk.hook {
            before {
                val receiver = instanceOrNull ?: return@before
                val callKey = key(methods.streamChunk, receiver, args)
                if (bypass.remove(callKey)) return@before
                val text = args.getOrNull(0) as? String ?: return@before
                val original: () -> Unit = { bypass += callKey; runCatching { methods.streamChunk.invoke(receiver, *args) }; Unit }
                if (runtime.onStreamChunk(text, original)) result = null
            }
        }
        Log.i(LOG_TAG, "engine_install stage=hooked;method=${methods.streamChunk.name}")
        methods.streamEnd.hook {
            before {
                val receiver = instanceOrNull ?: return@before
                val callKey = key(methods.streamEnd, receiver, args)
                if (bypass.remove(callKey)) return@before
                val original: () -> Unit = { bypass += callKey; runCatching { methods.streamEnd.invoke(receiver, *args) }; Unit }
                if (runtime.onStreamEnd(original)) result = null
            }
        }
        Log.i(LOG_TAG, "engine_install stage=hooked;method=${methods.streamEnd.name}")
        status.publish("active", "profile=${profile.id};engine=true;transport=false;originalPlayer=false")
    }

    private fun installTransportFallback(
        context: Context,
        profile: VersionProfile,
        status: HookStatusPublisher
    ) {
        val transport = (profile.ttsRoute as? TtsRoute.WebSocket)?.descriptor
            ?: return status.publish("disabled", "websocket route missing")
        val socketClass = transport.className.toClassOrNull()
            ?: return status.publish("disabled", "RealWebSocket disappeared after profile selection")
        val sendMethods = socketClass.declaredMethods.filter { method ->
            method.matches(transport.send)
        }
        if (sendMethods.size != 1) {
            status.publish("disabled", "send(String) candidates=${sendMethods.size}")
            return
        }

        val runtime = BreenoTransportRuntime(
            context = context,
            configCache = HookConfigCache(ConfigRepository(context.contentResolver)),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            status = status
        )
        val sendMethod = sendMethods.single().apply { isAccessible = true }
        sendMethod.hook {
            before {
                val socket = instanceOrNull ?: return@before
                val payload = args.firstOrNull() as? String ?: return@before
                runtime.onSend(socket, sendMethod, payload)?.let { interceptedResult ->
                    result = interceptedResult
                }
            }
        }

        socketClass.declaredMethods
            .filter { it.matches(transport.cancel) }
            .forEach { method ->
                method.hook { before { runtime.cancelActive("original websocket cancelled") } }
            }
        socketClass.declaredMethods
            .filter { it.matches(transport.close) }
            .forEach { method ->
                method.hook { before { runtime.cancelActive("original websocket closed") } }
            }

        installHostGatedCleartextPolicy(runtime::shouldPermitCleartext)
        status.publish(
            state = "active",
            detail = "profile=${profile.id};transport=true;originalPlayer=false"
        )
    }

    private fun installHostGatedCleartextPolicy(shouldPermit: (String) -> Boolean) {
        val policyClass = runCatching {
            Class.forName("android.security.NetworkSecurityPolicy", false, appClassLoader)
        }.getOrNull() ?: return
        policyClass.declaredMethods
            .filter {
                it.name == "isCleartextTrafficPermitted" &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }
            .forEach { method ->
                method.hook {
                    before {
                        val host = args.firstOrNull() as? String ?: return@before
                        if (shouldPermit(host)) result = true
                    }
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.packageVersionName(packageName: String): String =
        getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun Method.matches(descriptor: MethodDescriptor): Boolean =
        name == descriptor.name &&
            parameterTypes.map { it.name } == descriptor.parameterTypeNames &&
            returnType.name == descriptor.returnTypeName

    private companion object {
        const val LOG_TAG = "BreenoTTSHook"
    }
}
