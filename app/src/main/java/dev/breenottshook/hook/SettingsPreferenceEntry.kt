package dev.breenottshook.hook

import java.util.Locale

object SettingsPreferenceEntry {
    const val key = "dev.breenottshook.preference.third_party_voice"
    const val title = "第三方音色"
    const val defaultSummary = "点击配置"

    fun title(locale: Locale): String =
        if (locale.language.equals("en", ignoreCase = true)) "Third-party voice" else title

    fun anchorTitles(locale: Locale): Set<String> = if (locale.language.equals("en", ignoreCase = true)) {
        setOf(
            "Voice", "Voice color", "Voice & sound", "Voice and sound", "Voice settings",
            "Xiaobu voice", "Xiaobu sound", "Speech voice", "小布音色"
        )
    } else {
        setOf("小布音色", "第三方音色")
    }

    fun orderAfter(anchorOrder: Int): Int =
        if (anchorOrder == Int.MAX_VALUE) Int.MAX_VALUE else anchorOrder + 1
}
