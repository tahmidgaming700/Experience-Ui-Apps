package com.experience.clock

import android.content.Context
import android.content.SharedPreferences

/** User preferences, in device-protected storage so ringing works before first unlock. */
class ClockSettings(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Minutes after which a ringing alarm stops by itself; 0 = never. */
    var autoSilenceMinutes: Int
        get() = prefs.getInt("auto_silence", 10)
        set(v) = prefs.edit().putInt("auto_silence", v).apply()

    var defaultSnoozeMinutes: Int
        get() = prefs.getInt("snooze", 10)
        set(v) = prefs.edit().putInt("snooze", v).apply()

    /** Seconds over which alarm volume rises to full; 0 = immediately full. */
    var volumeRampSeconds: Int
        get() = prefs.getInt("ramp", 30)
        set(v) = prefs.edit().putInt("ramp", v).apply()

    /** Timer sound: null = default alarm sound, "" = silent. */
    var timerRingtone: String?
        get() = prefs.getString("timer_ringtone", null)
        set(v) = prefs.edit().putString("timer_ringtone", v).apply()

    var worldClocks: List<String>
        get() = prefs.getString("world_clocks", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = prefs.edit().putString("world_clocks", v.joinToString(",")).apply()
}
