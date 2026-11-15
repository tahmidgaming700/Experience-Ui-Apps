package com.experience.launcher.data

import android.content.Context
import com.experience.launcher.model.HomeLayout

/** Launcher appearance preferences. */
data class LauncherPrefs(
    val homeColumns: Int = 4,
    val showLabels: Boolean = true,
    val dockEnabled: Boolean = true,
    val showClock: Boolean = true,
)

/** Persists the home layout and preferences in SharedPreferences. */
class LauncherStore(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    val isInitialized: Boolean get() = sp.getBoolean(KEY_INITIALIZED, false)

    fun readLayout() = HomeLayout(
        home = HomeLayout.decodeList(sp.getString(KEY_HOME, null)),
        dock = HomeLayout.decodeList(sp.getString(KEY_DOCK, null)),
    )

    fun writeLayout(layout: HomeLayout) {
        sp.edit()
            .putString(KEY_HOME, HomeLayout.encodeList(layout.home))
            .putString(KEY_DOCK, HomeLayout.encodeList(layout.dock))
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    fun readPrefs() = LauncherPrefs(
        homeColumns = sp.getInt(KEY_COLUMNS, 4),
        showLabels = sp.getBoolean(KEY_LABELS, true),
        dockEnabled = sp.getBoolean(KEY_DOCK_ENABLED, true),
        showClock = sp.getBoolean(KEY_CLOCK, true),
    )

    fun writePrefs(prefs: LauncherPrefs) {
        sp.edit()
            .putInt(KEY_COLUMNS, prefs.homeColumns)
            .putBoolean(KEY_LABELS, prefs.showLabels)
            .putBoolean(KEY_DOCK_ENABLED, prefs.dockEnabled)
            .putBoolean(KEY_CLOCK, prefs.showClock)
            .apply()
    }

    private companion object {
        const val KEY_INITIALIZED = "initialized"
        const val KEY_HOME = "home"
        const val KEY_DOCK = "dock"
        const val KEY_COLUMNS = "columns"
        const val KEY_LABELS = "labels"
        const val KEY_DOCK_ENABLED = "dock_enabled"
        const val KEY_CLOCK = "clock"
    }
}
