package dev.breenottshook.config

import android.net.Uri

object ConfigContract {
    const val AUTHORITY = "dev.breenottshook.config"
    const val MODULE_PACKAGE = "dev.breenottshook"
    const val BREENO_PACKAGE = "com.heytap.speechassist"

    val URI: Uri = Uri.parse("content://$AUTHORITY/config")

    const val METHOD_GET_CONFIG = "get_config"
    const val METHOD_UPDATE_CONFIG = "update_config"
    const val METHOD_GET_HOOK_STATUS = "get_hook_status"
    const val METHOD_PUT_HOOK_STATUS = "put_hook_status"

    const val KEY_VERSION = "version"
    const val KEY_EXPECTED_VERSION = "expected_version"
    const val KEY_PAYLOAD = "payload"
    const val KEY_RESULT = "result"
    const val KEY_ERROR = "error"
    const val KEY_HOOK_STATUS = "hook_status"
}

object ConfigCallerAuthorizer {
    fun isAllowed(uidPackages: Set<String>): Boolean =
        ConfigContract.MODULE_PACKAGE in uidPackages ||
            ConfigContract.BREENO_PACKAGE in uidPackages
}
