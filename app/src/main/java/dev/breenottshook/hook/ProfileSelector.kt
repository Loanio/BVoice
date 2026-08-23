package dev.breenottshook.hook

sealed interface ProfileSelection {
    data class Selected(val profile: VersionProfile) : ProfileSelection
    data class Unsupported(val packageVersion: String) : ProfileSelection
    data class Ambiguous(val profileIds: List<String>) : ProfileSelection
}

class ProfileSelector(
    private val profiles: List<VersionProfile>
) {
    fun select(packageVersion: String, classProbe: ClassProbe): ProfileSelection {
        val matches = profiles.filter { it.matches(packageVersion, classProbe) }
        return when (matches.size) {
            0 -> ProfileSelection.Unsupported(packageVersion)
            1 -> ProfileSelection.Selected(matches.single())
            else -> {
                val engine = matches.filter { it.ttsRoute is TtsRoute.Engine }
                if (engine.size == 1) ProfileSelection.Selected(engine.single())
                else ProfileSelection.Ambiguous(matches.map { it.id })
            }
        }
    }

    fun selectEnginePreferred(
        packageVersion: String,
        classProbe: ClassProbe,
        engineProfileId: String
    ): ProfileSelection {
        val engineProfile = profiles.firstOrNull { profile ->
            profile.id == engineProfileId &&
                (profile.ttsRoute as? TtsRoute.Engine)?.descriptor?.className?.let(classProbe::exists) == true
        }
        return engineProfile?.let(ProfileSelection::Selected) ?: select(packageVersion, classProbe)
    }
}
