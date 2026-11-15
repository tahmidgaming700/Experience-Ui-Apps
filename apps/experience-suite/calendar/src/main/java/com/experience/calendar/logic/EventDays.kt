package com.experience.calendar.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Maps occurrences onto local calendar days. */
object EventDays {
    /** Julian day number of 1970-01-01 (Time.EPOCH_JULIAN_DAY). */
    const val EPOCH_JULIAN_DAY = 2440588

    fun julianToDate(julianDay: Int): LocalDate = LocalDate.ofEpochDay((julianDay - EPOCH_JULIAN_DAY).toLong())
    fun dateToJulian(date: LocalDate): Int = (date.toEpochDay() + EPOCH_JULIAN_DAY).toInt()

    /**
     * First and last local day an occurrence covers. All-day events use the provider's
     * Julian days (computed in UTC); timed events ending exactly at midnight do not spill
     * into the next day.
     */
    fun span(allDay: Boolean, begin: Long, end: Long, startDay: Int, endDay: Int, zone: ZoneId): ClosedRange<LocalDate> {
        if (allDay) {
            val s = julianToDate(startDay)
            return s..maxOf(s, julianToDate(endDay))
        }
        val s = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(zone).toLocalDate()
        return s..maxOf(s, e)
    }

    /** Start of [date] in [zone] and start of the following day, as epoch millis. */
    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
