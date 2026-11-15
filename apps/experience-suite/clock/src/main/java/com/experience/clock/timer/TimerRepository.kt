package com.experience.clock.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Timers persisted as JSON; every change re-arms expiry alarms and syncs [TimerService]. */
object TimerRepository {
    private val _timers = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = _timers
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("timers", Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        _timers.value = Timer.fromJson(prefs(context).getString("timers", null))
        loaded = true
    }

    @Synchronized
    private fun mutate(context: Context, block: (List<Timer>) -> List<Timer>) {
        ensureLoaded(context)
        val next = block(_timers.value)
        _timers.value = next
        prefs(context).edit().putString("timers", Timer.toJson(next)).apply()
        arm(context, next)
        TimerService.sync(context, next)
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun wall() = System.currentTimeMillis()

    fun add(context: Context, lengthMs: Long, label: String = "", start: Boolean = true): Int {
        var newId = 0
        mutate(context) { list ->
            newId = (list.maxOfOrNull { it.id } ?: 0) + 1
            val t = Timer(newId, lengthMs, label)
            list + if (start) t.start(now(), wall()) else t
        }
        return newId
    }

    private fun update(context: Context, id: Int, f: (Timer) -> Timer) = mutate(context) { l -> l.map { if (it.id == id) f(it) else it } }

    fun start(context: Context, id: Int) = update(context, id) { it.start(now(), wall()) }
    fun pause(context: Context, id: Int) = update(context, id) { it.pause(now()) }
    fun reset(context: Context, id: Int) = update(context, id) { it.reset() }
    fun addMinute(context: Context, id: Int) = update(context, id) { it.addMinute(now(), wall()) }
    fun expire(context: Context, id: Int) = update(context, id) { if (it.remaining(now()) <= 500) it.expire() else it }
    fun delete(context: Context, id: Int) = mutate(context) { l -> l.filterNot { it.id == id } }
    fun rename(context: Context, id: Int, label: String) = update(context, id) { it.copy(label = label) }

    /** Re-arms alarms (after boot the elapsed clock restarted, so ends are rebuilt from wall time). */
    fun restore(context: Context, afterReboot: Boolean) {
        mutate(context) { l -> if (afterReboot) l.map { it.afterReboot(now(), wall()) } else l }
    }

    private fun pi(context: Context, id: Int, flags: Int) = PendingIntent.getBroadcast(
        context, 10_000 + id,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE).putExtra(TimerReceiver.EXTRA_ID, id),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Exact, Doze-proof expiry alarm per running timer. */
    private fun arm(context: Context, list: List<Timer>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val ids = list.map { it.id }.toSet()
        for (t in list) {
            if (t.state == Timer.State.RUNNING) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, t.endElapsed, pi(context, t.id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
            } else {
                pi(context, t.id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
            }
        }
        // Timers deleted since the last call.
        for (stale in knownIds - ids) pi(context, stale, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        knownIds = ids
    }

    private var knownIds: Set<Int> = emptySet()
}
