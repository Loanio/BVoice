package dev.breenottshook.hook

data class SettingsHostDescriptor(
    val id: String,
    val versionName: String,
    val className: String,
    val fragmentClassName: String? = null
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
        val matches = descriptors.filter { it.className in availableClasses }
            .distinctBy { it.className }
        return when (matches.size) {
            0 -> SettingsHostSelection.Unavailable(
                "没有找到可用的设置宿主描述符"
            )
            1 -> SettingsHostSelection.Selected(matches.single())
            else -> SettingsHostSelection.Ambiguous(matches.map { it.id })
        }
    }
}

object BreenoSettingsHosts {
    private const val MAIN_SETTINGS_ACTIVITY =
        "com.heytap.speechassist.home.settings.ui.SettingsActivity"

    val descriptors: List<SettingsHostDescriptor> = listOf(
        SettingsHostDescriptor(
            id = "breeno-11.8.3-main-settings",
            versionName = "11.8.3",
            className = MAIN_SETTINGS_ACTIVITY
        ),
        SettingsHostDescriptor(
            id = "breeno-12.9.9-main-settings",
            versionName = "12.9.9",
            className = "com.heytap.speechassist.home.settings.p294ui.SettingsActivity",
            fragmentClassName = "com.heytap.speechassist.home.settings.p294ui.fragment.SpeechSettingFragment"
        ),
        // Some 12.9.9 builds retain the runtime package name without JADX's
        // synthetic p294 alias. This class name is verified on-device.
        SettingsHostDescriptor(
            id = "breeno-12.9.9-runtime-settings",
            versionName = "12.9.9",
            className = MAIN_SETTINGS_ACTIVITY
        )
    )
}
