package dev.breenottshook.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

class ConfigProvider : ContentProvider() {
    private lateinit var persistence: SharedPrefsConfigPersistence
    private lateinit var store: AtomicConfigStore

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        persistence = SharedPrefsConfigPersistence(appContext)
        store = AtomicConfigStore(persistence)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        enforceAllowedCaller()
        return when (method) {
            ConfigContract.METHOD_GET_CONFIG -> store.read().toBundle()
            ConfigContract.METHOD_UPDATE_CONFIG -> update(extras)
            ConfigContract.METHOD_GET_HOOK_STATUS -> Bundle().apply {
                putString(ConfigContract.KEY_HOOK_STATUS, persistence.readHookStatus())
            }
            ConfigContract.METHOD_PUT_HOOK_STATUS -> Bundle().apply {
                val value = extras?.getString(ConfigContract.KEY_HOOK_STATUS).orEmpty()
                putBoolean(ConfigContract.KEY_RESULT, persistence.writeHookStatus(value))
            }
            else -> throw IllegalArgumentException("Unknown provider method: $method")
        }
    }

    private fun update(extras: Bundle?): Bundle {
        val payload = extras?.getString(ConfigContract.KEY_PAYLOAD)
            ?: return errorBundle("missing_payload")
        val expected = extras.getLong(ConfigContract.KEY_EXPECTED_VERSION, -1L)
            .takeIf { it >= 0 }
        val config = runCatching { ConfigCodec.decode(payload) }
            .getOrElse { return errorBundle("invalid_payload") }
        return when (val result = store.update(expected, config)) {
            is UpdateResult.Success -> {
                context?.contentResolver?.notifyChange(ConfigContract.URI, null)
                result.snapshot.toBundle()
            }
            is UpdateResult.VersionConflict -> errorBundle(
                "version_conflict",
                result.currentVersion
            )
            is UpdateResult.Invalid -> errorBundle(
                result.issues.joinToString(separator = ",") { it.field }
            )
            UpdateResult.PersistenceFailure -> errorBundle("persistence_failure")
        }
    }

    private fun enforceAllowedCaller() {
        val packages = context?.packageManager
            ?.getPackagesForUid(Binder.getCallingUid())
            ?.toSet()
            .orEmpty()
        if (!ConfigCallerAuthorizer.isAllowed(packages)) {
            throw SecurityException("Caller is not allowed to access BreenoTTSHook configuration")
        }
    }

    private fun ConfigSnapshot.toBundle() = Bundle().apply {
        putLong(ConfigContract.KEY_VERSION, version)
        putString(ConfigContract.KEY_PAYLOAD, ConfigCodec.encode(value))
        putBoolean(ConfigContract.KEY_RESULT, true)
    }

    private fun errorBundle(error: String, version: Long? = null) = Bundle().apply {
        putBoolean(ConfigContract.KEY_RESULT, false)
        putString(ConfigContract.KEY_ERROR, error)
        version?.let { putLong(ConfigContract.KEY_VERSION, it) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
