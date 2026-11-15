package com.experience.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.experience.clock.timer.TimerRepository

/**
 * - ACTION_FIRE (from AlarmManager): starts the ringing foreground service.
 * - Boot (also LOCKED_BOOT_COMPLETED: alarms live in device-protected storage), time/zone
 *   changes and app updates: AlarmManager forgets alarms on reboot and wall-clock alarms
 *   need recomputing after a clock or zone change, so everything is rescheduled.
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE = "com.experience.clock.action.FIRE_ALARM"
        const val ACTION_CANCEL_SNOOZE = "com.experience.clock.action.CANCEL_SNOOZE"
        const val EXTRA_ID = "alarm_id"

        /** Held from the broadcast until the service has started ringing. */
        @Volatile var wakeLock: PowerManager.WakeLock? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val pm = context.getSystemService(PowerManager::class.java)
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExperienceClock:fire").apply { acquire(60_000) }
                context.startForegroundService(
                    Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_RING)
                        .putExtra(EXTRA_ID, intent.getLongExtra(EXTRA_ID, -1))
                )
            }
            ACTION_CANCEL_SNOOZE -> background {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                AlarmRepository.dismissed(context, id)
                context.getSystemService(android.app.NotificationManager::class.java).cancel(AlarmService.snoozeNotificationId(id))
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> background {
                AlarmRepository.scheduleAll(context)
                AlarmRepository.load(context)
                val reboot = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
                TimerRepository.restore(context, afterReboot = reboot)
            }
        }
    }

    private fun background(block: () -> Unit) {
        val pending = goAsync()
        Thread { try { block() } finally { pending.finish() } }.start()
    }
}
