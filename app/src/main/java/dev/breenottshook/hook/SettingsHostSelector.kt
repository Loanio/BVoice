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
            className = MAIN_SETTINGS_ACTIVITY
        )
    )
}
