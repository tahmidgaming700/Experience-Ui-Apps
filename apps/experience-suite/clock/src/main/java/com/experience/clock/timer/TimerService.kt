package com.experience.clock.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.experience.clock.ClockSettings
import com.experience.clock.MainActivity
import com.experience.clock.Notifications
import com.experience.clock.R
import com.experience.clock.ring.Ringer
import com.experience.clock.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service alive while any timer runs or has expired. Shows one notification
 * per active timer (a live countdown chronometer with Pause/Resume, +1 min and Stop) and
 * plays the alarm sound while a timer is expired.
 */
class TimerService : Service() {
    companion object {
        /** Starts or stops the service to match [timers]. */
        fun sync(context: Context, timers: List<Timer>) {
            val active = timers.any { it.state == Timer.State.RUNNING || it.state == Timer.State.EXPIRED }
            val intent = Intent(context, TimerService::class.java)
            if (active) runCatching { context.startForegroundService(intent) }
            else {
                context.stopService(intent)
                val nm = context.getSystemService(NotificationManager::class.java)
                timers.forEach { nm.cancel(Notifications.ID_TIMER_BASE + it.id) }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ringer: Ringer
    private var ringing = false
    private var foregroundId = 0
    private val shown = HashSet<Int>()
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExperienceClock:timer").apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensure(this)
        ringer = Ringer(this)
        TimerRepository.ensureLoaded(this)
        scope.launch { TimerRepository.timers.collect { render(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy the startForegroundService contract immediately, even if nothing is active
        // any more (render() then stops the service right away).
        val timers = TimerRepository.timers.value
        val first = timers.firstOrNull { it.state == Timer.State.RUNNING || it.state == Timer.State.EXPIRED } ?: timers.firstOrNull()
        if (first != null) {
            foregroundId = Notifications.ID_TIMER_BASE + first.id
            startForeground(foregroundId, notification(first))
        } else {
            startForeground(Notifications.ID_TIMER_BASE, Notification.Builder(this, Notifications.CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_timer).setContentTitle(getString(R.string.tab_timer)).build())
        }
        render(timers)
        return START_NOT_STICKY
    }

    private fun render(timers: List<Timer>, forceForeground: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        val active = timers.filter { it.state == Timer.State.RUNNING || it.state == Timer.State.EXPIRED }
            .sortedBy { it.endElapsed }
        if (active.isEmpty()) {
            stopRinging()
            stopForeground(STOP_FOREGROUND_REMOVE)
            shown.forEach { nm.cancel(Notifications.ID_TIMER_BASE + it) }
            shown.clear()
            stopSelf()
            return
        }
        val first = active.first()
        val fgId = Notifications.ID_TIMER_BASE + first.id
        if (foregroundId != fgId || forceForeground) {
            startForeground(fgId, notification(first))
            foregroundId = fgId
        }
        val visible = timers.filter { it.state != Timer.State.RESET }
        visible.forEach { nm.notify(Notifications.ID_TIMER_BASE + it.id, notification(it)) }
        (shown - visible.map { it.id }.toSet()).forEach { nm.cancel(Notifications.ID_TIMER_BASE + it) }
        shown.clear(); shown.addAll(visible.map { it.id })

        val anyExpired = timers.any { it.state == Timer.State.EXPIRED }
        if (anyExpired && !ringing) {
            ringer.start(ClockSettings(this).timerRingtone, vibrate = true, rampMs = 0)
            wakeLock.acquire(10 * 60_000L)
            ringing = true
        } else if (!anyExpired) stopRinging()
    }

    private fun stopRinging() {
        if (ringing) ringer.stop()
        ringing = false
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun action(action: String, id: Int, title: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            this, id * 10 + action.hashCode() % 10,
            Intent(this, TimerReceiver::class.java).setAction(action).putExtra(TimerReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, getString(title), pi).build()
    }

    private fun notification(t: Timer): Notification {
        val now = SystemClock.elapsedRealtime()
        val remaining = t.remaining(now)
        val open = PendingIntent.getActivity(
            this, 20, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_TIMERS_INTERNAL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = t.label.ifBlank { getString(R.string.timer_name, formatDuration(t.lengthMs)) }
        val expired = t.state == Timer.State.EXPIRED
        val b = Notification.Builder(this, if (expired) Notifications.CH_TIMER_EXPIRED else Notifications.CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (expired) getString(R.string.times_up) else title)
            .setContentIntent(open)
            .setOnlyAlertOnce(!expired)
            .setOngoing(t.state != Timer.State.PAUSED)
            .setCategory(if (expired) Notification.CATEGORY_ALARM else Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setGroup("timers")
        when (t.state) {
            Timer.State.RUNNING -> {
                // Countdown chronometer: the system updates it, no per-second notifications.
                b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                    .setWhen(System.currentTimeMillis() + remaining)
                b.addAction(action(TimerReceiver.ACTION_PAUSE, t.id, R.string.pause))
                b.addAction(action(TimerReceiver.ACTION_ADD_MINUTE, t.id, R.string.add_minute))
                b.addAction(action(TimerReceiver.ACTION_STOP, t.id, R.string.stop))
            }
            Timer.State.PAUSED -> {
                b.setContentText(getString(R.string.paused_remaining, formatDuration(remaining)))
                b.addAction(action(TimerReceiver.ACTION_RESUME, t.id, R.string.resume))
                b.addAction(action(TimerReceiver.ACTION_STOP, t.id, R.string.stop))
            }
            Timer.State.EXPIRED -> {
                b.setContentText(title)
                b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() + remaining)
                b.setFullScreenIntent(open, true)
                b.addAction(action(TimerReceiver.ACTION_ADD_MINUTE, t.id, R.string.add_minute))
                b.addAction(action(TimerReceiver.ACTION_STOP, t.id, R.string.stop))
            }
            Timer.State.RESET -> Unit
        }
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        stopRinging()
        super.onDestroy()
    }
}
