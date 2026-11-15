package com.experience.clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CH_ALARM = "alarm_ringing"
    const val CH_TIMER = "timers"
    const val CH_TIMER_EXPIRED = "timer_expired"
    const val CH_STOPWATCH = "stopwatch"
    const val CH_MISSED = "missed_alarms"
    const val CH_SNOOZE = "snoozed_alarms"

    const val ID_ALARM = 1
    const val ID_STOPWATCH = 2
    const val ID_MISSED_BASE = 1000
    const val ID_TIMER_BASE = 5000

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: Int, importance: Int, silent: Boolean) {
            if (nm.getNotificationChannel(id) != null) return
            nm.createNotificationChannel(NotificationChannel(id, context.getString(name), importance).apply {
                // Sound is played by the app on the alarm stream; the channel stays quiet.
                if (silent) { setSound(null, null); enableVibration(false) }
                setShowBadge(false)
            })
        }
        ch(CH_ALARM, R.string.channel_alarm, NotificationManager.IMPORTANCE_HIGH, true)
        ch(CH_TIMER_EXPIRED, R.string.channel_timer_expired, NotificationManager.IMPORTANCE_HIGH, true)
        ch(CH_TIMER, R.string.channel_timer, NotificationManager.IMPORTANCE_LOW, true)
        ch(CH_STOPWATCH, R.string.channel_stopwatch, NotificationManager.IMPORTANCE_LOW, true)
        ch(CH_SNOOZE, R.string.channel_snooze, NotificationManager.IMPORTANCE_LOW, true)
        ch(CH_MISSED, R.string.channel_missed, NotificationManager.IMPORTANCE_DEFAULT, false)
    }
}
