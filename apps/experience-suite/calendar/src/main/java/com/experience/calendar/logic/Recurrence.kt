package com.experience.calendar.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Repeat presets offered in the editor; [CUSTOM] keeps an RRULE we did not create. */
enum class RecurrencePreset { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY_BY_DATE, MONTHLY_BY_WEEKDAY, YEARLY, CUSTOM }

/** How a repeating event ends. */
sealed interface RecurrenceEnd {
    data object Never : RecurrenceEnd
    data class Count(val times: Int) : RecurrenceEnd
    /** Last date (inclusive) in the event's local calendar. */
    data class Until(val date: LocalDate) : RecurrenceEnd
}

/**
 * Builds and recognises RFC 5545 RRULE strings for the presets, relative to the event's
 * first occurrence [start] (which supplies the weekday / day of month / month).
 */
object Recurrence {
    private val BYDAY = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )
    private val UNTIL_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val UNTIL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Ordinal of [date]'s weekday in its month: 1..4, or -1 when it is the last one (e.g. last Friday). */
    fun weekdayOrdinal(date: LocalDate): Int {
        val n = (date.dayOfMonth - 1) / 7 + 1
        return if (date.plusWeeks(1).month != date.month) -1 else n
    }

    /**
     * @param start first occurrence (in the event time zone)
     * @param allDay all-day events take a DATE value for UNTIL; timed events a UTC date-time
     */
    fun build(preset: RecurrencePreset, start: ZonedDateTime, end: RecurrenceEnd = RecurrenceEnd.Never, allDay: Boolean = false): String? {
        val d = start.toLocalDate()
        val base = when (preset) {
            RecurrencePreset.NONE, RecurrencePreset.CUSTOM -> return null
            RecurrencePreset.DAILY -> "FREQ=DAILY"
            RecurrencePreset.WEEKDAYS -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            RecurrencePreset.WEEKLY -> "FREQ=WEEKLY;BYDAY=" + BYDAY.getValue(d.dayOfWeek)
            RecurrencePreset.MONTHLY_BY_DATE -> "FREQ=MONTHLY;BYMONTHDAY=" + d.dayOfMonth
            RecurrencePreset.MONTHLY_BY_WEEKDAY -> "FREQ=MONTHLY;BYDAY=" + weekdayOrdinal(d) + BYDAY.getValue(d.dayOfWeek)
            RecurrencePreset.YEARLY -> "FREQ=YEARLY;BYMONTH=" + d.monthValue + ";BYMONTHDAY=" + d.dayOfMonth
        }
        val suffix = when (end) {
            RecurrenceEnd.Never -> ""
            is RecurrenceEnd.Count -> ";COUNT=" + end.times.coerceAtLeast(1)
            is RecurrenceEnd.Until -> ";UNTIL=" + if (allDay) {
                end.date.format(UNTIL_DATE)
            } else {
                // Last instant of the until-day in the event zone, expressed in UTC.
                end.date.plusDays(1).atStartOfDay(start.zone).minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC).format(UNTIL_UTC)
            }
        }
        return base + suffix
    }

    fun parts(rrule: String): Map<String, String> = rrule.removePrefix("RRULE:").split(';')
        .mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }
        .toMap()

    /** Recognises one of our presets (ignoring COUNT/UNTIL/WKST); anything else is CUSTOM. */
    fun presetOf(rrule: String?, start: ZonedDateTime): RecurrencePreset {
        if (rrule.isNullOrBlank()) return RecurrencePreset.NONE
        val p = parts(rrule) - setOf("COUNT", "UNTIL", "WKST")
        val probe = RecurrencePreset.entries.filter { it != RecurrencePreset.NONE && it != RecurrencePreset.CUSTOM }
        // Interval 1 is the default; any other interval is custom.
        if ((p["INTERVAL"] ?: "1") != "1") return RecurrencePreset.CUSTOM
        val normalized = p - "INTERVAL"
        for (preset in probe) {
            val built = parts(build(preset, start)!!)
            if (built == normalized) return preset
        }
        // Plain FREQ=WEEKLY / MONTHLY / YEARLY without BY* rules repeat on the start's day too.
        return when (normalized) {
            mapOf("FREQ" to "WEEKLY") -> RecurrencePreset.WEEKLY
            mapOf("FREQ" to "MONTHLY") -> RecurrencePreset.MONTHLY_BY_DATE
            mapOf("FREQ" to "YEARLY") -> RecurrencePreset.YEARLY
            else -> RecurrencePreset.CUSTOM
        }
    }

    fun endOf(rrule: String?): RecurrenceEnd {
        if (rrule.isNullOrBlank()) return RecurrenceEnd.Never
        val p = parts(rrule)
        p["COUNT"]?.toIntOrNull()?.let { return RecurrenceEnd.Count(it) }
        p["UNTIL"]?.let { u ->
            runCatching { return RecurrenceEnd.Until(LocalDate.parse(u.take(8), UNTIL_DATE)) }
        }
        return RecurrenceEnd.Never
    }
}
