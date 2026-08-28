package dev.breenottshook.hook

internal object NativeEntryUpdatePolicy {
    fun shouldUpdate(settingsPageOpen: Boolean): Boolean = !settingsPageOpen
}
