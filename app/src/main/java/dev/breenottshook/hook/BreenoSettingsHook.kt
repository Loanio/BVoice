package dev.breenottshook.hook

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import dev.breenottshook.ui.host.HostSettingsDialog

class BreenoSettingsHook : YukiBaseHooker() {
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
        val versionName = context.packageManager.packageVersionName(packageName)
        val descriptors = BreenoSettingsHosts.descriptors
        val availableClasses = descriptors.mapNotNull { descriptor ->
            descriptor.className.takeIf { it.toClassOrNull() != null }
        }.toSet()
        when (val selection = SettingsHostSelector(descriptors).select(versionName, availableClasses)) {
            is SettingsHostSelection.Unavailable -> status.publish(
                "settings_disabled",
                selection.reason
            )
            is SettingsHostSelection.Ambiguous -> status.publish(
                "settings_disabled",
                "ambiguous=${selection.descriptorIds.joinToString()}"
            )
            is SettingsHostSelection.Selected -> install(selection.descriptor, status)
        }
    }

    private fun install(descriptor: SettingsHostDescriptor, status: HookStatusPublisher) {
        val hostClass = descriptor.className.toClassOrNull()
            ?: return status.publish("settings_disabled", "verified host class missing")
        val onCreateMethods = hostClass.declaredMethods.filter {
            it.name == "onCreate" &&
                it.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
        }
        if (onCreateMethods.size != 1) {
            status.publish("settings_disabled", "onCreate(Bundle) candidates=${onCreateMethods.size}")
            return
        }
        onCreateMethods.single().hook {
            after {
                val activity = instanceOrNull as? Activity ?: return@after
                addSettingsEntry(activity)
            }
        }
        status.publish("settings_active", "descriptor=${descriptor.id}")
    }

    private fun addSettingsEntry(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<Button>(ENTRY_TAG) != null) return
        content.addView(
            Button(activity).apply {
                tag = ENTRY_TAG
                text = "第三方音色"
                contentDescription = "打开 BreenoTTSHook 完整配置"
                setOnClickListener { HostSettingsDialog(activity).show() }
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.packageVersionName(packageName: String): String =
        getPackageInfo(packageName, 0).versionName.orEmpty()

    private companion object {
        const val ENTRY_TAG = "dev.breenottshook.settings.entry"
    }
}
