package com.octetproof.sample

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

/**
 * Sample preferences, persisted in SharedPreferences and Compose-observable.
 * Mirrors iOS AppSettings. The debug-tier toggles (`semanticV2`, `verboseLogs`)
 * are only surfaced in the hidden menu behind `BuildConfig.DEBUG`, so a release
 * build never shows them; the stored values are harmless either way.
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

    // Release-tier.
    var showDebug: Boolean by BoolPref("showDebug", true)
    var showDeviceTab: Boolean by BoolPref("showDeviceTab", false)
    /** Applied at Octet.start; flipping it shows a "needs restart" hint. */
    var uploadsEnabled: Boolean by BoolPref("uploadsEnabled", true)
    /** Set once the 5-tap menu is unlocked so a ⚙ affordance can reappear. */
    var devMenuUnlocked: Boolean by BoolPref("devMenuUnlocked", false)

    // Debug-tier (only read/shown behind BuildConfig.DEBUG).
    var semanticV2: Boolean by BoolPref("semanticV2", false)
    var verboseLogs: Boolean by BoolPref("verboseLogs", false)
}
