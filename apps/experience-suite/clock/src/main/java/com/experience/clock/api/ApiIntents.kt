package com.experience.clock.api

import com.experience.clock.alarm.Alarm
import java.time.DayOfWeek
import java.util.Calendar

/** Pure conversions for AlarmClock API extras. */
object ApiIntents {
    /** AlarmClock.EXTRA_DAYS uses java.util.Calendar constants (SUNDAY = 1 … SATURDAY = 7). */
    fun daysFromCalendar(days: List<Int>?): Int = days.orEmpty().fold(0) { acc, d ->
        val dow = when (d) {
            Calendar.MONDAY -> DayOfWeek.MONDAY
            Calendar.TUESDAY -> DayOfWeek.TUESDAY
            Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            Calendar.THURSDAY -> DayOfWeek.THURSDAY
            Calendar.FRIDAY -> DayOfWeek.FRIDAY
            Calendar.SATURDAY -> DayOfWeek.SATURDAY
            Calendar.SUNDAY -> DayOfWeek.SUNDAY
            else -> null
        }
        if (dow == null) acc else acc or Alarm.dayBit(dow)
    }

    /** Hour from EXTRA_HOUR (0–23) with the optional EXTRA_IS_PM flag (for 12-hour input). */
    fun hour24(hour: Int, isPm: Boolean?): Int = when {
        isPm == null -> hour
        isPm && hour < 12 -> hour + 12
        !isPm && hour == 12 -> 0
        else -> hour
    }.coerceIn(0, 23)

    /** Clamps EXTRA_LENGTH (seconds) to the documented 1..86400 range, in millis. */
    fun timerLengthMs(seconds: Int): Long? = if (seconds in 1..86_400) seconds * 1000L else null
}
