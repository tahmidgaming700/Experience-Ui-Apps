package com.experience.clock.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.widget.Toast
import com.experience.clock.ClockSettings
import com.experience.clock.MainActivity
import com.experience.clock.R
import com.experience.clock.alarm.Alarm
import com.experience.clock.alarm.AlarmRepository
import com.experience.clock.alarm.AlarmService
import com.experience.clock.alarm.NextAlarm
import com.experience.clock.timer.TimerRepository
import com.experience.clock.ui.formatDuration
import java.time.ZonedDateTime

/**
 * Invisible activity implementing the android.provider.AlarmClock API for other apps and
 * voice assistants: SET_ALARM, SET_TIMER, DISMISS_ALARM and SNOOZE_ALARM (protected by
 * com.android.alarm.permission.SET_ALARM in the manifest). With EXTRA_SKIP_UI the work is
 * done silently; otherwise the matching tab of [MainActivity] is opened.
 */
class HandleApiCalls : Activity() {
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        Thread {
            val result = runCatching { handle(intent) }.getOrNull()
            main.post {
                result?.let { (message, open) ->
                    message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                    open?.let { startActivity(it) }
                }
                finish()
            }
        }.start()
    }

    private fun mainIntent(action: String) = Intent(this, MainActivity::class.java).setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Returns an optional toast and an optional activity to open. Runs off the main thread. */
    private fun handle(intent: Intent): Pair<String?, Intent?> {
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        return when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> setAlarm(intent, skipUi)
            AlarmClock.ACTION_SET_TIMER -> setTimer(intent, skipUi)
            AlarmClock.ACTION_DISMISS_ALARM -> dismissAlarm(intent)
            AlarmClock.ACTION_SNOOZE_ALARM -> snoozeAlarm(intent)
            else -> null to null
        }
    }

    private fun setAlarm(intent: Intent, skipUi: Boolean): Pair<String?, Intent?> {
        if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            // No time given: let the user pick one.
            return null to mainIntent(MainActivity.ACTION_SHOW_ALARMS_INTERNAL).putExtra(MainActivity.EXTRA_NEW_ALARM, true)
        }
        val isPm = if (intent.hasExtra(AlarmClock.EXTRA_IS_PM)) intent.getBooleanExtra(AlarmClock.EXTRA_IS_PM, false) else null
        val hour = ApiIntents.hour24(intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0), isPm)
        val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59)
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        val days = ApiIntents.daysFromCalendar(intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS))
        val ringtone = when {
            !intent.hasExtra(AlarmClock.EXTRA_RINGTONE) -> null
            intent.getStringExtra(AlarmClock.EXTRA_RINGTONE) == AlarmClock.VALUE_RINGTONE_SILENT -> ""
            else -> intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)
        }
        val vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true)

        // Re-enable an identical alarm rather than creating duplicates.
        val existing = AlarmRepository.load(this).firstOrNull { it.hour == hour && it.minute == minute && it.days == days && it.label == label }
        val alarm = if (existing != null) {
            existing.copy(enabled = true, snoozedUntil = null, skipUntil = null, vibrate = vibrate, ringtone = ringtone ?: existing.ringtone)
                .also { AlarmRepository.update(this, it) }
        } else {
            AlarmRepository.add(this, Alarm(hour = hour, minute = minute, days = days, label = label, ringtone = ringtone,
                vibrate = vibrate, snoozeMinutes = ClockSettings(this).defaultSnoozeMinutes))
        }
        val message = getString(R.string.alarm_set_for, AlarmService.formatTime(this, hour, minute))
        return message to if (skipUi) null else mainIntent(MainActivity.ACTION_SHOW_ALARMS_INTERNAL).putExtra(MainActivity.EXTRA_HIGHLIGHT_ALARM, alarm.id)
    }

    private fun setTimer(intent: Intent, skipUi: Boolean): Pair<String?, Intent?> {
        val length = ApiIntents.timerLengthMs(intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
            ?: return null to (if (skipUi) null else mainIntent(MainActivity.ACTION_SHOW_TIMERS_INTERNAL))
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        TimerRepository.add(this, length, label, start = true)
        val message = getString(R.string.timer_started, formatDuration(length))
        return message to if (skipUi) null else mainIntent(MainActivity.ACTION_SHOW_TIMERS_INTERNAL)
    }

    private fun dismissAlarm(intent: Intent): Pair<String?, Intent?> {
        if (AlarmService.ringing.value != null) {
            startService(AlarmService.intent(this, AlarmService.ACTION_DISMISS))
            return getString(R.string.alarm_dismissed) to null
        }
        val alarms = AlarmRepository.load(this).filter { it.enabled }
        val mode = intent.getStringExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE) ?: AlarmClock.ALARM_SEARCH_MODE_NEXT
        val targets: List<Alarm> = when (mode) {
            AlarmClock.ALARM_SEARCH_MODE_ALL -> alarms
            AlarmClock.ALARM_SEARCH_MODE_LABEL -> {
                val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
                alarms.filter { label.isNotBlank() && it.label.equals(label, ignoreCase = true) }
            }
            AlarmClock.ALARM_SEARCH_MODE_TIME -> {
                val isPm = if (intent.hasExtra(AlarmClock.EXTRA_IS_PM)) intent.getBooleanExtra(AlarmClock.EXTRA_IS_PM, false) else null
                val h = ApiIntents.hour24(intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1), isPm)
                val m = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
                alarms.filter { it.hour == h && it.minute == m }
            }
            else -> listOfNotNull(NextAlarm.soonest(alarms, ZonedDateTime.now())?.first)
        }
        if (targets.isEmpty()) return getString(R.string.no_alarm_found) to null
        // "Dismiss" an upcoming alarm: a one-time alarm is turned off, a repeating one skips its next ring.
        targets.forEach { AlarmRepository.skipNext(this, it) }
        return resources.getQuantityString(R.plurals.alarms_dismissed, targets.size, targets.size) to null
    }

    private fun snoozeAlarm(intent: Intent): Pair<String?, Intent?> {
        if (AlarmService.ringing.value == null) return getString(R.string.no_alarm_ringing) to null
        val minutes = intent.getIntExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, 0)
        startService(AlarmService.intent(this, AlarmService.ACTION_SNOOZE).putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, minutes))
        return getString(R.string.alarm_snoozed) to null
    }
}
