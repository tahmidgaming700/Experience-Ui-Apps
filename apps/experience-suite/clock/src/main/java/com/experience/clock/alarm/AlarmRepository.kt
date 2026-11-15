package com.experience.clock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.experience.clock.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId

/**
 * Single entry point for alarm changes: every write re-schedules with AlarmManager and
 * refreshes [alarms]. Methods block on SQLite; call from a background thread.
 */
object AlarmRepository {
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms

    @Volatile private var store: AlarmStore? = null
    private fun store(context: Context) = store ?: synchronized(this) { store ?: AlarmStore(context).also { store = it } }

    fun load(context: Context): List<Alarm> = store(context).all().also { _alarms.value = it }
    fun get(context: Context, id: Long): Alarm? = store(context).get(id)

    fun add(context: Context, alarm: Alarm): Alarm = store(context).insert(alarm).also { changed(context) }

    fun update(context: Context, alarm: Alarm) {
        store(context).update(alarm)
        changed(context)
    }

    fun delete(context: Context, id: Long) {
        cancel(context, id)
        store(context).delete(id)
        changed(context)
    }

    /** Enabling clears any snooze/skip; disabling cancels the pending alarm. */
    fun setEnabled(context: Context, alarm: Alarm, enabled: Boolean) =
        update(context, alarm.copy(enabled = enabled, snoozedUntil = null, skipUntil = null))

    private fun changed(context: Context) {
        scheduleAll(context)
        load(context)
    }

    private fun firePendingIntent(context: Context, id: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).putExtra(AlarmReceiver.EXTRA_ID, id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancel(context: Context, id: Long) {
        firePendingIntent(context, id, PendingIntent.FLAG_NO_CREATE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    /**
     * (Re)schedules every alarm with setAlarmClock: exact, allowed in Doze, shown in the
     * status bar and lock screen; tapping the status-bar entry opens the alarms tab.
     */
    fun scheduleAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val show = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_ALARMS_INTERNAL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        for (alarm in store(context).all()) {
            val next = NextAlarm.nextMillis(alarm, now, zone)
            if (next == null) cancel(context, alarm.id)
            else am.setAlarmClock(AlarmManager.AlarmClockInfo(next, show), firePendingIntent(context, alarm.id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        }
    }

    /** Called after the alarm rang and was dismissed (or auto-silenced). */
    fun dismissed(context: Context, id: Long) {
        val a = get(context, id) ?: return
        update(context, a.copy(enabled = a.repeats, snoozedUntil = null, skipUntil = null))
    }

    fun snoozed(context: Context, id: Long, untilMillis: Long) {
        val a = get(context, id) ?: return
        update(context, a.copy(snoozedUntil = untilMillis))
    }

    /** "Dismiss upcoming": skips the next occurrence of a repeating alarm, disables a one-time alarm. */
    fun skipNext(context: Context, alarm: Alarm) {
        val now = System.currentTimeMillis()
        val next = NextAlarm.nextMillis(alarm, now, ZoneId.systemDefault()) ?: return
        if (alarm.repeats && alarm.snoozedUntil == null) update(context, alarm.copy(skipUntil = next))
        else update(context, alarm.copy(enabled = alarm.repeats, snoozedUntil = null))
    }
}
