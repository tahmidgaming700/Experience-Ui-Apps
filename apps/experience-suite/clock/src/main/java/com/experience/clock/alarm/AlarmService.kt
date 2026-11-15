package com.experience.clock.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import com.experience.clock.ClockSettings
import com.experience.clock.MainActivity
import com.experience.clock.Notifications
import com.experience.clock.R
import com.experience.clock.ring.Ringer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Foreground service that rings an alarm: sound on the alarm stream with gradual volume,
 * vibration, and a high-priority full-screen notification that launches [AlarmActivity]
 * (over the lock screen). Dismiss/Snooze arrive as service intents from the activity,
 * the notification actions or AlarmClock API calls. Rings stop by themselves after the
 * auto-silence period.
 */
class AlarmService : Service() {
    companion object {
        const val ACTION_RING = "com.experience.clock.action.RING"
        const val ACTION_DISMISS = "com.experience.clock.action.DISMISS"
        const val ACTION_SNOOZE = "com.experience.clock.action.SNOOZE"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        private val _ringing = MutableStateFlow<Alarm?>(null)
        /** The alarm currently ringing, observed by [AlarmActivity] and the API handler. */
        val ringing: StateFlow<Alarm?> = _ringing

        fun snoozeNotificationId(id: Long) = 3000 + (id % 1000).toInt()

        fun intent(context: Context, action: String) = Intent(context, AlarmService::class.java).setAction(action)

        fun formatTime(context: Context, hour: Int, minute: Int): String {
            val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
            return DateFormat.getTimeFormat(context).format(cal.time)
        }
    }

    private lateinit var ringer: Ringer
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var current: Alarm? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ringer = Ringer(this)
        Notifications.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                // Must call startForeground promptly; use a placeholder until the alarm is loaded.
                startForeground(Notifications.ID_ALARM, ringingNotification(null))
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1)
                io.execute {
                    val alarm = AlarmRepository.get(this, id)
                    handler.post { if (alarm == null || !alarm.enabled) finishIfIdle() else ring(alarm) }
                }
            }
            ACTION_DISMISS -> current?.let { stopRinging(it, snoozeMinutes = null) } ?: finishIfIdle()
            ACTION_SNOOZE -> current?.let {
                val m = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 0).takeIf { v -> v > 0 } ?: it.snoozeMinutes
                stopRinging(it, snoozeMinutes = m)
            } ?: finishIfIdle()
            else -> finishIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarm: Alarm) {
        // A second alarm while one is ringing replaces it; the first counts as missed.
        current?.let { previous -> postMissed(previous); io.execute { AlarmRepository.dismissed(this, previous.id) } }
        current = alarm
        _ringing.value = alarm
        val settings = ClockSettings(this)
        ringer.start(alarm.ringtone, alarm.vibrate, settings.volumeRampSeconds * 1000L)
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALARM, ringingNotification(alarm))
        getSystemService(NotificationManager::class.java).cancel(snoozeNotificationId(alarm.id))
        AlarmReceiver.wakeLock?.let { if (it.isHeld) it.release() }
        AlarmReceiver.wakeLock = null
        // Clear the consumed snooze and reschedule the remaining alarms.
        io.execute {
            AlarmRepository.get(this, alarm.id)?.let { AlarmRepository.update(this, it.copy(snoozedUntil = null, skipUntil = null)) }
        }
        handler.removeCallbacksAndMessages(null)
        val silence = settings.autoSilenceMinutes
        if (silence > 0) {
            handler.postDelayed({ current?.let { postMissed(it); stopRinging(it, snoozeMinutes = null) } }, silence * 60_000L)
        }
        // Keep the CPU awake while ringing.
        wakeLock.acquire(((if (silence > 0) silence else 60) + 1) * 60_000L)
    }

    private val wakeLock by lazy {
        getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ExperienceClock:ringing").apply { setReferenceCounted(false) }
    }

    private fun stopRinging(alarm: Alarm, snoozeMinutes: Int?) {
        ringer.stop()
        handler.removeCallbacksAndMessages(null)
        current = null
        _ringing.value = null
        io.execute {
            if (snoozeMinutes != null) {
                val until = System.currentTimeMillis() + snoozeMinutes * 60_000L
                AlarmRepository.snoozed(this, alarm.id, until)
                handler.post { postSnoozed(alarm, until) }
            } else {
                AlarmRepository.dismissed(this, alarm.id)
            }
        }
        finishIfIdle()
    }

    private fun finishIfIdle() {
        if (current != null) return
        if (wakeLock.isHeld) wakeLock.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ringer.stop()
        handler.removeCallbacksAndMessages(null)
        _ringing.value = null
        io.shutdown()
        super.onDestroy()
    }

    private fun servicePi(action: String, code: Int) =
        PendingIntent.getService(this, code, intent(this, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun ringingNotification(alarm: Alarm?): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 1, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = alarm?.label?.takeIf { it.isNotBlank() } ?: getString(R.string.alarm)
        val text = alarm?.let { formatTime(this, it.hour, it.minute) } ?: ""
        return Notification.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(Notification.Action.Builder(null, getString(R.string.snooze), servicePi(ACTION_SNOOZE, 2)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.dismiss), servicePi(ACTION_DISMISS, 3)).build())
            .build()
    }

    private fun postSnoozed(alarm: Alarm, until: Long) {
        val cancel = PendingIntent.getBroadcast(
            this, snoozeNotificationId(alarm.id),
            Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CANCEL_SNOOZE).putExtra(AlarmReceiver.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cal = Calendar.getInstance().apply { timeInMillis = until }
        val n = Notification.Builder(this, Notifications.CH_SNOOZE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.snoozed_until, formatTime(this, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))))
            .setContentText(alarm.label.ifBlank { getString(R.string.alarm) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, getString(R.string.dismiss), cancel).build())
            .build()
        getSystemService(NotificationManager::class.java).notify(snoozeNotificationId(alarm.id), n)
    }

    private fun postMissed(alarm: Alarm) {
        val n = Notification.Builder(this, Notifications.CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.missed_alarm))
            .setContentText(formatTime(this, alarm.hour, alarm.minute) + (if (alarm.label.isNotBlank()) " · " + alarm.label else ""))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_MISSED_BASE + (alarm.id % 1000).toInt(), n)
    }
}
