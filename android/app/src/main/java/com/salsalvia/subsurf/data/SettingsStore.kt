package com.salsalvia.subsurf.data

import android.content.Context
import android.content.SharedPreferences
import com.salsalvia.subsurf.BuildConfig

/** Tiny persistence layer: only the backend base URL is stored locally. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.API_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_URL, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "subsurf_settings"
        private const val KEY_URL = "api_base_url"
    }
}
