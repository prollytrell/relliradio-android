package com.relli.home.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings for the home screen, kept on the TV. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("rellihome", Context.MODE_PRIVATE)

    inner class Pref<T>(private val key: String, default: T, read: (String, T) -> T, private val write: SharedPreferences.Editor.(String, T) -> Unit) {
        private val state = MutableStateFlow(read(key, default))
        val flow: StateFlow<T> get() = state
        var value: T
            get() = state.value
            set(v) { state.value = v; sp.edit().apply { write(key, v) }.apply() }
    }

    private fun string(key: String, d: String) = Pref(key, d, { k, def -> sp.getString(k, def) ?: def }, { k, v -> putString(k, v) })
    private fun bool(key: String, d: Boolean) = Pref(key, d, { k, def -> sp.getBoolean(k, def) }, { k, v -> putBoolean(k, v) })
    private fun float(key: String, d: Float) = Pref(key, d, { k, def -> sp.getFloat(k, def) }, { k, v -> putFloat(k, v) })
    private fun list(key: String) = Pref(key, emptyList<String>(),
        { k, def -> sp.getString(k, null)?.split(",")?.filter { it.isNotEmpty() } ?: def },
        { k, v -> putString(k, v.joinToString(",")) })

    /** Your app order (package names). Apps not in it go at the end, A–Z. */
    val appOrder = list("appOrder")
    val hidden = list("hidden")

    // Weather
    val city = string("city", "")
    val lat = float("lat", 0f)
    val lon = float("lon", 0f)
    val fahrenheit = bool("fahrenheit", true)
    val clock24 = bool("clock24", false)

    // Rows
    val showContinue = bool("showContinue", true)
    val showLive = bool("showLive", true)
    val showNew = bool("showNew", true)
    val takeOverHome = bool("takeOverHome", true)
}
