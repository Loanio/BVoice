package dev.breenottshook.config

import android.content.Context

class SharedPrefsConfigPersistence(context: Context) : ConfigPersistence {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): PersistedConfig? {
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return PersistedConfig(
            version = preferences.getLong(KEY_VERSION, 0),
            payload = payload
        )
    }

    override fun write(value: PersistedConfig): Boolean =
        preferences.edit()
            .putLong(KEY_VERSION, value.version)
            .putString(KEY_PAYLOAD, value.payload)
            .commit()

    fun readHookStatus(): String = preferences.getString(KEY_HOOK_STATUS, "{}") ?: "{}"

    fun writeHookStatus(value: String): Boolean =
        preferences.edit().putString(KEY_HOOK_STATUS, value).commit()

    private companion object {
        const val FILE_NAME = "breeno_tts_hook"
        const val KEY_VERSION = "config_version"
        const val KEY_PAYLOAD = "config_payload"
        const val KEY_HOOK_STATUS = "hook_status"
    }
}
