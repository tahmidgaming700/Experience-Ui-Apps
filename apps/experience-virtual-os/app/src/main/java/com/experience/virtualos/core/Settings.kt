package com.experience.virtualos.core

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val setupComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val tcgThreads: Int = 0, // 0 = automatic
    val disableProotSeccomp: Boolean = false,
    val keepScreenOnInDisplay: Boolean = true,
    val runtimeManifestUrl: String = "",
)

/** SharedPreferences-backed settings exposed as a StateFlow. */
class SettingsStore(context: Context, private val defaultManifestUrl: String) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val manifestUrl: String get() = _state.value.runtimeManifestUrl.ifBlank { defaultManifestUrl }

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_state.value)
        prefs.edit {
            putBoolean("setup_complete", next.setupComplete)
            putString("theme_mode", next.themeMode.name)
            putBoolean("dynamic_color", next.dynamicColor)
            putInt("tcg_threads", next.tcgThreads)
            putBoolean("disable_proot_seccomp", next.disableProotSeccomp)
            putBoolean("keep_screen_on", next.keepScreenOnInDisplay)
            putString("runtime_manifest_url", next.runtimeManifestUrl)
        }
        _state.value = next
    }

    private fun read() = AppSettings(
        setupComplete = prefs.getBoolean("setup_complete", false),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic_color", true),
        tcgThreads = prefs.getInt("tcg_threads", 0),
        disableProotSeccomp = prefs.getBoolean("disable_proot_seccomp", false),
        keepScreenOnInDisplay = prefs.getBoolean("keep_screen_on", true),
        runtimeManifestUrl = prefs.getString("runtime_manifest_url", "") ?: "",
    )
}
