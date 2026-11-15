package com.experience.clock.stopwatch

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.experience.clock.MainActivity
import com.experience.clock.Notifications
import com.experience.clock.R
import com.experience.clock.ui.formatStopwatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Stopwatch state shared by the UI and the notification, persisted across process death. */
object StopwatchRepository {
    private val _state = MutableStateFlow(Stopwatch())
    val state: StateFlow<Stopwatch> = _state
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString("state", null) ?: return
        _state.value = runCatching {
            val o = JSONObject(raw)
            val laps = o.getJSONArray("laps").let { a -> (0 until a.length()).map { a.getLong(it) } }
            Stopwatch(o.getBoolean("running"), o.getLong("start"), o.getLong("acc"), laps).afterReboot(SystemClock.elapsedRealtime())
        }.getOrDefault(Stopwatch())
    }

    @Synchronized
    fun update(context: Context, f: (Stopwatch, Long) -> Stopwatch) {
        ensureLoaded(context)
        val next = f(_state.value, SystemClock.elapsedRealtime())
        _state.value = next
        val o = JSONObject().put("running", next.running).put("start", next.startElapsed).put("acc", next.accumulatedMs)
            .put("laps", JSONArray(next.laps))
        prefs(context).edit().putString("state", o.toString()).apply()
        StopwatchService.sync(context, next)
    }
}

/**
 * Keeps the stopwatch visible while the app is in the background: a foreground service
 * with a chronometer notification (Pause / Lap / Reset) while running, and a dismissible
 * notification (Resume / Reset) while paused.
 */
class StopwatchService : Service() {
    companion object {
        const val ACTION_START = "com.experience.clock.action.SW_START"
        const val ACTION_PAUSE = "com.experience.clock.action.SW_PAUSE"
        const val ACTION_LAP = "com.experience.clock.action.SW_LAP"
        const val ACTION_RESET = "com.experience.clock.action.SW_RESET"
        const val ACTION_SYNC = "com.experience.clock.action.SW_SYNC"

        fun sync(context: Context, s: Stopwatch) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (s.running) {
                runCatching { context.startForegroundService(Intent(context, StopwatchService::class.java).setAction(ACTION_SYNC)) }
            } else {
                context.stopService(Intent(context, StopwatchService::class.java))
                if (s.isReset) nm.cancel(Notifications.ID_STOPWATCH)
                else nm.notify(Notifications.ID_STOPWATCH, notification(context, s))
            }
        }

        private fun pi(context: Context, action: String) = PendingIntent.getForegroundService(
            context, action.hashCode(), Intent(context, StopwatchService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun notification(context: Context, s: Stopwatch): Notification {
            Notifications.ensure(context)
            val elapsed = s.elapsed(SystemClock.elapsedRealtime())
            val open = PendingIntent.getActivity(
                context, 30, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_STOPWATCH_INTERNAL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val b = Notification.Builder(context, Notifications.CH_STOPWATCH)
                .setSmallIcon(R.drawable.ic_stat_stopwatch)
                .setContentTitle(context.getString(R.string.tab_stopwatch))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_PROGRESS)
            fun act(action: String, title: Int) = Notification.Action.Builder(null, context.getString(title), pi(context, action)).build()
            if (s.running) {
                b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - elapsed).setOngoing(true)
                if (s.laps.isNotEmpty()) b.setContentText(context.getString(R.string.lap_n, s.laps.size + 1))
                b.addAction(act(ACTION_PAUSE, R.string.pause)).addAction(act(ACTION_LAP, R.string.lap)).addAction(act(ACTION_RESET, R.string.reset))
            } else {
                b.setContentText(context.getString(R.string.paused_at, formatStopwatch(elapsed))).setShowWhen(false)
                b.addAction(act(ACTION_START, R.string.resume)).addAction(act(ACTION_RESET, R.string.reset))
            }
            return b.build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        StopwatchRepository.ensureLoaded(this)
        // Every start must reach startForeground, even if the action ends up pausing.
        startForeground(Notifications.ID_STOPWATCH, notification(this, StopwatchRepository.state.value))
        when (intent?.action) {
            ACTION_START -> StopwatchRepository.update(this) { s, now -> s.start(now) }
            ACTION_PAUSE -> StopwatchRepository.update(this) { s, now -> s.pause(now) }
            ACTION_LAP -> StopwatchRepository.update(this) { s, now -> s.lap(now) }
            ACTION_RESET -> StopwatchRepository.update(this) { s, _ -> s.reset() }
        }
        val s = StopwatchRepository.state.value
        if (s.running) {
            startForeground(Notifications.ID_STOPWATCH, notification(this, s))
        } else {
            // Leave the paused notification in place (detached) and stop.
            stopForeground(STOP_FOREGROUND_DETACH)
            val nm = getSystemService(NotificationManager::class.java)
            if (s.isReset) nm.cancel(Notifications.ID_STOPWATCH) else nm.notify(Notifications.ID_STOPWATCH, notification(this, s))
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
