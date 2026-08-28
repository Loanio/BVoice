package dev.breenottshook.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import dev.breenottshook.config.ConfigCodec
import dev.breenottshook.config.ConfigContract
import dev.breenottshook.ui.host.HostSettingsPage
import java.lang.reflect.Proxy

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
        installApplicationLifecycleFallback(context)
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

    private fun installApplicationLifecycleFallback(context: Context) {
        val application = context.applicationContext as? Application ?: return
        if (!registeredApplications.add(application)) {
            return
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val name = activity.javaClass.name.lowercase()
                if (name.contains("setting")) {
                    scheduleSettingsEntry(activity)
                    HostSettingsPage.restoreIfNeeded(activity)
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = unregisterSummaryObserver(activity)
        })
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
        hostClass.declaredMethods.filter {
            it.name == "onResume" && it.parameterTypes.isEmpty()
        }.singleOrNull()?.hook {
            after {
                val activity = instanceOrNull as? Activity ?: return@after
                scheduleSettingsEntry(activity)
            }
        }
        descriptor.fragmentClassName?.toClassOrNull()?.let { fragmentClass ->
            fragmentClass.declaredMethods.filter {
                it.name == "onViewCreated" &&
                    it.parameterTypes.contentEquals(arrayOf(View::class.java, Bundle::class.java))
            }.singleOrNull()?.hook {
                after {
                    val activity = runCatching {
                        instanceOrNull?.javaClass?.methods?.firstOrNull {
                            it.name == "getActivity" && it.parameterCount == 0
                        }?.invoke(instanceOrNull) as? Activity
                    }.getOrNull() ?: return@after
                    addSettingsEntry(activity)
                    scheduleSettingsEntry(activity)
                }
            }
        }
        descriptor.fragmentClassName?.toClassOrNull()?.declaredMethods?.filter {
            it.name == "onResume" && it.parameterTypes.isEmpty()
        }?.singleOrNull()?.hook {
            after {
                val activity = runCatching {
                    instanceOrNull?.javaClass?.methods?.firstOrNull {
                        it.name == "getActivity" && it.parameterCount == 0
                    }?.invoke(instanceOrNull) as? Activity
                }.getOrNull() ?: return@after
                scheduleSettingsEntry(activity)
            }
        }
        status.publish("settings_active", "descriptor=${descriptor.id}")
    }

    private val boundPreferenceClasses = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<Class<*>, Boolean>()
    )

    private fun installEntryAssignmentBinding(preferenceClass: Class<*>) {
        Log.i(LOG_TAG, "native_entry bind_candidate class=${preferenceClass.name} already=${!boundPreferenceClasses.add(preferenceClass)}")
        val bind = preferenceClass.methods.firstOrNull { method ->
            method.name == "onBindViewHolder" && method.parameterCount == 1
        } ?: run {
            Log.w(LOG_TAG, "native_entry bind_missing class=${preferenceClass.name}")
            return
        }
        Log.i(LOG_TAG, "native_entry bind_install owner=${bind.declaringClass.name}")
        val bindOwner = bind.declaringClass
        runCatching { bindOwner.getDeclaredMethod("onBindViewHolder", *bind.parameterTypes).hook {
            before {
                val preference = instanceOrNull ?: return@before
                val key = runCatching {
                    preference.javaClass.methods.firstOrNull { it.name == "getKey" && it.parameterCount == 0 }
                        ?.invoke(preference)?.toString()
                }.getOrNull()
                if (key != SettingsPreferenceEntry.key) return@before
                // Keep the host's default binding from rendering the value under
                // the title. The visible value is rendered in the assignment slot.
                runCatching {
                    PreferenceReflection.textSummaryMethod(preference.javaClass.methods)
                        ?.invoke(preference, "")
                }
            }
            after {
                val preference = instanceOrNull ?: return@after
                val key = runCatching {
                    preference.javaClass.methods.firstOrNull { it.name == "getKey" && it.parameterCount == 0 }
                        ?.invoke(preference)?.toString()
                }.getOrNull()
                if (key != SettingsPreferenceEntry.key) return@after
                val holder = args.getOrNull(0) ?: return@after
                val item = holder.javaClass.methods.firstOrNull {
                    it.name == "getItemView" && it.parameterCount == 0
                }?.invoke(holder) as? View ?: return@after
                val styled = HostPreferenceRowStyler.styleBoundItem(
                    item,
                    currentVoiceSummary(item.context)
                )
                if (styled) Log.i(LOG_TAG, "native_entry assignment_bound")
                else Log.w(LOG_TAG, "native_entry assignment_missing")
            }
        } }.onFailure { Log.e(LOG_TAG, "native_entry bind_failed=${it.javaClass.simpleName}") }
    }

    private fun scheduleSettingsEntry(activity: Activity) {
        activity.window?.decorView?.post {
            addSettingsEntry(activity)
            activity.window?.decorView?.postDelayed({ addSettingsEntry(activity) }, 350L)
        }
    }

    private fun addSettingsEntry(activity: Activity) {
        if (!NativeEntryUpdatePolicy.shouldUpdate(HostSettingsPage.isOpen(activity))) return
        installSummaryObserver(activity)
        val screen = findPreferenceScreen(activity) ?: run {
            Log.w(LOG_TAG, "native_entry screen_missing activity=${activity.javaClass.name}")
            return
        }
        val existing = findPreference(screen, SettingsPreferenceEntry.key)
        if (existing != null) {
            setPreferenceSummary(existing, currentVoiceSummary(activity))
            scheduleNativeEntryStyle(activity)
            Log.i(LOG_TAG, "native_entry already_present")
            return
        }
        val locale = activity.resources.configuration.locales[0]
        val location = findPreferenceByTitles(screen, SettingsPreferenceEntry.anchorTitles(locale)) ?: run {
            Log.w(LOG_TAG, "native_entry anchor_missing screen=${screen.javaClass.name}")
            return
        }
        val anchor = location.node
        val parent = location.parent ?: screen
        val preference = createPreference(activity, anchor) ?: return
        val addPreference = parent.javaClass.methods.firstOrNull {
            it.name == "addPreference" && it.parameterCount == 1
        } ?: run {
            Log.e(LOG_TAG, "native_entry add_method_missing parent=${parent.javaClass.name}")
            return
        }
        runCatching { addPreference.invoke(parent, preference) }
            .onSuccess {
                scheduleNativeEntryStyle(activity)
                Log.i(LOG_TAG, "native_entry added anchor=${anchor.javaClass.name}")
            }
            .onFailure { Log.e(LOG_TAG, "native_entry add_failed=${it.javaClass.simpleName}") }
    }

    private fun styleNativeEntryRow(activity: Activity) {
        if (!NativeEntryUpdatePolicy.shouldUpdate(HostSettingsPage.isOpen(activity))) return
        val locale = activity.resources.configuration.locales[0]
        val styled = HostPreferenceRowStyler.styleActivity(
            activity,
            SettingsPreferenceEntry.title(locale),
            currentVoiceSummary(activity)
        )
        if (styled) Log.i(LOG_TAG, "native_entry style_applied")
        else Log.d(LOG_TAG, "native_entry style_wait")
    }

    private fun scheduleNativeEntryStyle(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        longArrayOf(0L, 250L, 600L, 1200L, 2200L, 3500L, 5000L).forEach { delay ->
            decor.postDelayed({ styleNativeEntryRow(activity) }, delay)
        }
    }

    private fun installSummaryObserver(activity: Activity) {
        if (summaryObservers.containsKey(activity)) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (NativeEntryUpdatePolicy.shouldUpdate(HostSettingsPage.isOpen(activity))) {
                    activity.window?.decorView?.post { styleNativeEntryRow(activity) }
                }
            }
        }
        runCatching {
            activity.contentResolver.registerContentObserver(ConfigContract.URI, false, observer)
            summaryObservers[activity] = observer
        }.onFailure { Log.w(LOG_TAG, "native_entry observer_failed=${it.javaClass.simpleName}") }
    }

    private fun unregisterSummaryObserver(activity: Activity) {
        summaryObservers.remove(activity)?.let { observer ->
            runCatching { activity.contentResolver.unregisterContentObserver(observer) }
        }
    }

    private fun findPreferenceScreen(activity: Activity): Any? {
        val manager = activity.javaClass.methods.firstOrNull {
            it.name == "getSupportFragmentManager" && it.parameterCount == 0
        }?.invoke(activity) ?: return null
        val fragments = manager.javaClass.methods.firstOrNull {
            it.name == "getFragments" && it.parameterCount == 0
        }?.invoke(manager) as? List<*> ?: return null
        return fragments.asSequence().mapNotNull(::findPreferenceScreen).firstOrNull()
    }

    private fun findPreferenceScreen(fragment: Any?): Any? {
        fragment ?: return null
        fragment.javaClass.methods.firstOrNull {
            it.name == "getPreferenceScreen" && it.parameterCount == 0
        }?.invoke(fragment)?.let { return it }
        val childManager = fragment.javaClass.methods.firstOrNull {
            it.name == "getChildFragmentManager" && it.parameterCount == 0
        }?.invoke(fragment) ?: return null
        val children = childManager.javaClass.methods.firstOrNull {
            it.name == "getFragments" && it.parameterCount == 0
        }?.invoke(childManager) as? List<*> ?: return null
        return children.asSequence().mapNotNull(::findPreferenceScreen).firstOrNull()
    }

    private fun findPreference(screen: Any, key: String): Any? = screen.javaClass.methods.firstOrNull {
        it.name == "findPreference" && it.parameterTypes.contentEquals(arrayOf(CharSequence::class.java))
    }?.invoke(screen, key)

    private fun findPreferenceByTitle(
        screen: Any,
        title: String
    ): PreferenceTraversal.Located<Any>? {
        return PreferenceTraversal.findWithParent(screen, ::preferenceChildren) { preference ->
            preference.javaClass.methods.firstOrNull { method ->
                method.name == "getTitle" && method.parameterCount == 0
            }?.invoke(preference)?.toString() == title
        }
    }

    private fun findPreferenceByTitles(
        screen: Any,
        titles: Set<String>
    ): PreferenceTraversal.Located<Any>? {
        return PreferenceTraversal.findWithParent(screen, ::preferenceChildren) { preference ->
            preference.javaClass.methods.firstOrNull { method ->
                method.name == "getTitle" && method.parameterCount == 0
            }?.invoke(preference)?.toString() in titles
        }
    }

    private fun preferenceChildren(preference: Any): Iterable<Any> {
        val count = preference.javaClass.methods.firstOrNull {
            it.name == "getPreferenceCount" && it.parameterCount == 0
        }?.invoke(preference) as? Int ?: return emptyList()
        val getPreference = preference.javaClass.methods.firstOrNull {
            it.name == "getPreference" && it.parameterCount == 1
        } ?: return emptyList()
        return (0 until count).mapNotNull { index -> runCatching { getPreference.invoke(preference, index) }.getOrNull() }
    }

    private fun createPreference(activity: Activity, anchor: Any): Any? = runCatching {
        val loader = anchor.javaClass.classLoader
        // Do not pass the module's AndroidX Preference subclass into the host
        // tree: Vector gives the host and module separate class loaders, so
        // Preference.OnPreferenceClickListener is not assignable across them.
        // Construct the host's own Preference class reflectively instead.
        val preferenceClass = anchor.javaClass
        installEntryAssignmentBinding(preferenceClass)
        val diagnosticMethods = preferenceClass.methods
            .filter { it.name in setOf("setAssignment", "setSummary", "getAssignment", "onBindViewHolder") }
            .joinToString(",") { method ->
                "${method.name}(${method.parameterTypes.joinToString(";") { it.name }}):${method.returnType.name}"
            }
        Log.i(LOG_TAG, "native_entry anchor_class=${preferenceClass.name} methods=$diagnosticMethods")
        val preference = newHostPreference(preferenceClass, activity)
        preferenceClass.getMethod("setKey", String::class.java).invoke(preference, SettingsPreferenceEntry.key)
        val locale = activity.resources.configuration.locales[0]
        preferenceClass.getMethod("setTitle", CharSequence::class.java)
            .invoke(preference, SettingsPreferenceEntry.title(locale))
        val summary = currentVoiceSummary(activity)
        val assignmentSetter = preferenceClass.methods.firstOrNull {
            it.name == "setAssignment" && it.parameterCount == 1 &&
                it.parameterTypes[0].isAssignableFrom(String::class.java)
        }
        if (assignmentSetter != null) {
            assignmentSetter.invoke(preference, summary)
            Log.i(LOG_TAG, "native_entry assignment_setter=host")
        } else {
            preferenceClass.getMethod("setSummary", CharSequence::class.java)
                .invoke(preference, "")
            Log.i(LOG_TAG, "native_entry assignment_setter=custom_row")
        }
        val anchorOrder = anchor.javaClass.getMethod("getOrder").invoke(anchor) as Int
        preferenceClass.getMethod("setOrder", Int::class.javaPrimitiveType)
            .invoke(preference, SettingsPreferenceEntry.orderAfter(anchorOrder))
        val listenerType = Class.forName("androidx.preference.Preference\$OnPreferenceClickListener", false, loader)
        val listener = Proxy.newProxyInstance(loader, arrayOf(listenerType)) { _, method, _ ->
            if (method.name == "onPreferenceClick") HostSettingsPage.open(activity)
            true
        }
        preferenceClass.getMethod("setOnPreferenceClickListener", listenerType).invoke(preference, listener)
        preference
    }.onFailure { Log.e(LOG_TAG, "native_entry create_failed=${it.javaClass.simpleName}") }.getOrNull()

    private fun newHostPreference(type: Class<*>, context: Context): Any {
        type.constructors.firstOrNull { constructor ->
            constructor.parameterTypes.firstOrNull() == Context::class.java &&
                constructor.parameterTypes.drop(1).all { it == android.util.AttributeSet::class.java || it == Int::class.javaPrimitiveType }
        }?.let { constructor ->
            val args = constructor.parameterTypes.drop(1).map {
                when (it) {
                    android.util.AttributeSet::class.java -> null
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }.toTypedArray()
            return constructor.newInstance(*arrayOf(context, *args))
        }
        return type.getConstructor(Context::class.java).newInstance(context)
    }

    private fun setPreferenceSummary(preference: Any, summary: String) {
        runCatching {
            val methods = preference.javaClass.methods
            val diagnosticMethods = methods
                .filter { it.name in setOf("setAssignment", "setSummary", "getAssignment", "onBindViewHolder") }
                .joinToString(",") { method ->
                    "${method.name}(${method.parameterTypes.joinToString(";") { it.name }}):${method.returnType.name}"
                }
            Log.i(LOG_TAG, "native_entry existing_class=${preference.javaClass.name} methods=$diagnosticMethods")
            val assignmentSetter = methods.firstOrNull {
                it.name == "setAssignment" && it.parameterCount == 1 &&
                    it.parameterTypes[0].isAssignableFrom(String::class.java)
            }
            if (assignmentSetter != null) {
                assignmentSetter.invoke(preference, summary)
                Log.i(LOG_TAG, "native_entry existing_assignment=host")
            } else {
                PreferenceReflection.textSummaryMethod(methods)?.invoke(preference, "")
                Log.i(LOG_TAG, "native_entry existing_assignment=custom_row")
            }
        }.onFailure { Log.e(LOG_TAG, "native_entry summary_failed=${it.javaClass.simpleName}") }
    }

    private fun currentVoiceSummary(context: Context): String = runCatching {
        val payload = context.contentResolver.call(
            ConfigContract.URI,
            ConfigContract.METHOD_GET_CONFIG,
            null,
            null
        )?.getString(ConfigContract.KEY_PAYLOAD).orEmpty()
        val locale = context.resources.configuration.locales[0]
        ConfigCodec.decode(payload).effectiveCharacter.ifBlank {
            if (locale.language.equals("en", ignoreCase = true)) "Tap to configure" else SettingsPreferenceEntry.defaultSummary
        }
    }.getOrDefault(SettingsPreferenceEntry.defaultSummary)

    @Suppress("DEPRECATION")
    private fun PackageManager.packageVersionName(packageName: String): String =
        getPackageInfo(packageName, 0).versionName.orEmpty()

    private companion object {
        const val LOG_TAG = "BreenoTTSHook"
        val registeredApplications = java.util.Collections.newSetFromMap(
            java.util.WeakHashMap<Application, Boolean>()
        )
        val summaryObservers = java.util.WeakHashMap<Activity, ContentObserver>()
    }
}
