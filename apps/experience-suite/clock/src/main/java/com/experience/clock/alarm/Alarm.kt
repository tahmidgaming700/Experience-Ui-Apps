package com.experience.clock.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A stored alarm. [days] is a bitmask of repeat days (bit 0 = Monday … bit 6 = Sunday);
 * 0 means "once". [ringtone]: null = default alarm sound, "" = silent, else a content URI.
 */
data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val days: Int = 0,
    val label: String = "",
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** Epoch millis of a pending snooze, or null. */
    val snoozedUntil: Long? = null,
    /** Occurrences at or before this instant are skipped ("dismiss upcoming"). */
    val skipUntil: Long? = null,
) {
    val repeats get() = days != 0
    fun repeatsOn(day: DayOfWeek) = days and dayBit(day) != 0

    companion object {
        const val EVERY_DAY = 0x7F
        const val WEEKDAYS = 0x1F
        fun dayBit(day: DayOfWeek) = 1 shl (day.value - 1)
        fun daysOf(vararg d: DayOfWeek) = d.fold(0) { acc, x -> acc or dayBit(x) }
    }
}

/**
 * Next-alarm computation, independent of Android so it can be unit-tested.
 *
 * Times are wall-clock times in the device's current zone: after a time-zone change an
 * alarm set for 07:00 rings at 07:00 local time in the new zone (we simply recompute).
 * DST: a time inside a spring-forward gap rings at the first valid instant after the gap
 * (java.time shifts it forward by the gap length); a time in a fall-back overlap rings once,
 * at its earlier occurrence.
 */
object NextAlarm {
    fun nextOccurrence(hour: Int, minute: Int, days: Int, after: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        val startDate = after.toLocalDate()
        for (i in 0..8) {
            val date = startDate.plusDays(i.toLong())
            if (days != 0 && days and Alarm.dayBit(date.dayOfWeek) == 0) continue
            val candidate = ZonedDateTime.of(date, time, after.zone)
            if (candidate.isAfter(after)) return candidate
        }
        error("unreachable: a repeating alarm always has a day within a week")
    }

    /** When [alarm] should next ring, or null if disabled. */
    fun next(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        alarm.snoozedUntil?.let { if (it > now.toInstant().toEpochMilli()) return Instant.ofEpochMilli(it).atZone(now.zone) }
        val skip = alarm.skipUntil?.let { Instant.ofEpochMilli(it).atZone(now.zone) }
        val base = if (skip != null && skip.isAfter(now)) skip else now
        return nextOccurrence(alarm.hour, alarm.minute, alarm.days, base)
    }

    fun nextMillis(alarm: Alarm, nowMillis: Long, zone: ZoneId): Long? =
        next(alarm, Instant.ofEpochMilli(nowMillis).atZone(zone))?.toInstant()?.toEpochMilli()

    /** The alarm that rings soonest, with its time. */
    fun soonest(alarms: List<Alarm>, now: ZonedDateTime): Pair<Alarm, ZonedDateTime>? =
        alarms.mapNotNull { a -> next(a, now)?.let { a to it } }.minByOrNull { it.second }
}
