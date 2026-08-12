package dev.breenottshook.hook

import android.content.Context
import android.content.pm.PackageManager
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import dev.breenottshook.config.ConfigContract
import dev.breenottshook.config.ConfigRepository
import dev.breenottshook.config.HookConfigCache
import java.lang.reflect.Method
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
        val status = HookStatusPublisher(context)
        val packageVersion = context.packageManager.packageVersionName(packageName)
        val selector = ProfileSelector(listOf(Breeno1183Profile()))
        when (val selection = selector.select(
            packageVersion = packageVersion,
            classProbe = ClassProbe { it.toClassOrNull() != null }
        )) {
            is ProfileSelection.Unsupported -> status.publish(
                state = "unsupported",
                detail = "version=${selection.packageVersion}"
            )
            is ProfileSelection.Ambiguous -> status.publish(
                state = "disabled",
                detail = "ambiguous=${selection.profileIds.joinToString()}"
            )
            is ProfileSelection.Selected -> installTransportFallback(
                context = context,
                profile = selection.profile,
                status = status
            )
        }
    }

    private fun installTransportFallback(
        context: Context,
        profile: VersionProfile,
        status: HookStatusPublisher
    ) {
        val socketClass = Breeno1183Profile.REAL_WEB_SOCKET_CLASS.toClassOrNull()
            ?: return status.publish("disabled", "RealWebSocket disappeared after profile selection")
        val sendMethods = socketClass.declaredMethods.filter { method ->
            method.name == "send" &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                (method.returnType == Boolean::class.javaPrimitiveType ||
                    method.returnType == Boolean::class.javaObjectType)
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
            .filter { it.name == "cancel" && it.parameterCount == 0 }
            .forEach { method ->
                method.hook { before { runtime.cancelActive("original websocket cancelled") } }
            }
        socketClass.declaredMethods
            .filter { it.name == "close" && it.parameterCount == 2 }
            .forEach { method ->
                method.hook { before { runtime.cancelActive("original websocket closed") } }
            }

        installHostGatedCleartextPolicy(runtime)
        status.publish(
            state = "active",
            detail = "profile=${profile.id};transport=true;originalPlayer=false"
        )
    }

    private fun installHostGatedCleartextPolicy(runtime: BreenoTransportRuntime) {
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
                        if (runtime.shouldPermitCleartext(host)) result = true
                    }
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.packageVersionName(packageName: String): String =
        getPackageInfo(packageName, 0).versionName.orEmpty()
}
