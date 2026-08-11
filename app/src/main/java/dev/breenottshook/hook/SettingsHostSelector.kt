package dev.breenottshook.hook

data class SettingsHostDescriptor(
    val id: String,
    val versionName: String,
    val className: String
)

sealed interface SettingsHostSelection {
    data class Selected(val descriptor: SettingsHostDescriptor) : SettingsHostSelection
    data class Ambiguous(val descriptorIds: List<String>) : SettingsHostSelection
    data class Unavailable(val reason: String) : SettingsHostSelection
}

class SettingsHostSelector(
    private val descriptors: List<SettingsHostDescriptor>
) {
    fun select(versionName: String, availableClasses: Set<String>): SettingsHostSelection {
        val matches = descriptors.filter {
            it.versionName == versionName && it.className in availableClasses
        }
        return when (matches.size) {
            0 -> SettingsHostSelection.Unavailable(
                "没有经过验证且适用于 $versionName 的设置宿主描述符"
            )
            1 -> SettingsHostSelection.Selected(matches.single())
            else -> SettingsHostSelection.Ambiguous(matches.map { it.id })
        }
    }
}

object Breeno1183SettingsHosts {
    // Intentionally empty until the installed 11.8.3 APK is inspected.
    val descriptors: List<SettingsHostDescriptor> = emptyList()
}
