package com.octetproof.sample

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

/**
 * Sample preferences, persisted in SharedPreferences and Compose-observable.
 * Mirrors iOS AppSettings.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("octet_sample_settings", Context.MODE_PRIVATE)

    /** A Compose-observable Boolean backed by SharedPreferences (persists on set). */
    private inner class BoolPref(private val key: String, default: Boolean) {
        private val state = mutableStateOf(prefs.getBoolean(key, default))
        operator fun getValue(thisRef: Any?, p: KProperty<*>): Boolean = state.value
        operator fun setValue(thisRef: Any?, p: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    /** Applied at Octet.start; flipping it shows a "needs restart" hint. */
    var uploadsEnabled: Boolean by BoolPref("uploadsEnabled", true)
}
