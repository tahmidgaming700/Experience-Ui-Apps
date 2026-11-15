package com.experience.calendar.logic

import android.provider.CalendarContract.Events
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Editable event state. Dates are inclusive; for all-day events the end date is the last day. */
data class EventDraft(
    val id: Long = 0,
    val calendarId: Long = 0,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val timeZone: String,
    val preset: RecurrencePreset = RecurrencePreset.NONE,
    val recurrenceEnd: RecurrenceEnd = RecurrenceEnd.Never,
    /** The original RRULE when it is not one of our presets (kept unchanged). */
    val customRrule: String? = null,
    val reminders: List<Int> = listOf(DEFAULT_REMINDER),
) {
    companion object {
        const val DEFAULT_REMINDER = 10

        /** A new one-hour event starting at the next whole hour on [date] (or now's date). */
        fun newAt(date: LocalDate, zone: ZoneId, now: ZonedDateTime = ZonedDateTime.now(zone)): EventDraft {
            val nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val start = if (date == now.toLocalDate()) nextHour.toLocalDateTime() else date.atTime(9, 0)
            val end = start.plusHours(1)
            return EventDraft(
                startDate = start.toLocalDate(), startTime = start.toLocalTime(),
                endDate = end.toLocalDate(), endTime = end.toLocalTime(), timeZone = zone.id,
            )
        }

        /** Builds a draft for an event stored with [dtStart]/[dtEnd] millis. */
        fun fromStored(
            id: Long, calendarId: Long, title: String, description: String, location: String,
            allDay: Boolean, dtStart: Long, dtEnd: Long, timeZone: String?, rrule: String?, reminders: List<Int>,
        ): EventDraft {
            val zone = if (allDay) ZoneOffset.UTC else runCatching { ZoneId.of(timeZone ?: "") }.getOrDefault(ZoneId.systemDefault())
            val s = Instant.ofEpochMilli(dtStart).atZone(zone)
            var e = Instant.ofEpochMilli(maxOf(dtEnd, dtStart)).atZone(zone)
            // All-day end is exclusive midnight in storage; inclusive last day in the editor.
            if (allDay) e = e.minusDays(1).let { if (it.isBefore(s)) s else it }
            val preset = Recurrence.presetOf(rrule, s)
            return EventDraft(
                id = id, calendarId = calendarId, title = title, description = description, location = location,
                allDay = allDay, startDate = s.toLocalDate(), startTime = s.toLocalTime(),
                endDate = e.toLocalDate(), endTime = e.toLocalTime(),
                timeZone = if (allDay) ZoneId.systemDefault().id else zone.id,
                preset = preset, recurrenceEnd = Recurrence.endOf(rrule),
                customRrule = if (preset == RecurrencePreset.CUSTOM) rrule else null,
                reminders = reminders,
            )
        }
    }

    val zone: ZoneId get() = if (allDay) ZoneOffset.UTC else ZoneId.of(timeZone)

    /** Start and (exclusive) end in epoch millis as stored by the Calendar Provider. */
    fun startMillis(): Long =
        if (allDay) startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else ZonedDateTime.of(startDate, startTime, ZoneId.of(timeZone)).toInstant().toEpochMilli()

    fun endMillis(): Long =
        if (allDay) endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else ZonedDateTime.of(endDate, endTime, ZoneId.of(timeZone)).toInstant().toEpochMilli()

    val isValid: Boolean get() = endMillis() > startMillis() || (!allDay && endMillis() == startMillis())

    fun rrule(): String? = if (preset == RecurrencePreset.CUSTOM) customRrule
    else Recurrence.build(preset, ZonedDateTime.of(startDate, startTime, zone), recurrenceEnd, allDay)

    /**
     * Column values for Events insert/update. Recurring events must use DURATION instead
     * of DTEND (CalendarProvider rule); all-day events are stored in UTC.
     */
    fun toValues(): Map<String, Any?> {
        val start = startMillis()
        val end = endMillis()
        val rule = rrule()
        val m = linkedMapOf<String, Any?>(
            Events.CALENDAR_ID to calendarId,
            Events.TITLE to title.trim(),
            Events.DESCRIPTION to description.trim(),
            Events.EVENT_LOCATION to location.trim(),
            Events.ALL_DAY to if (allDay) 1 else 0,
            Events.DTSTART to start,
            Events.EVENT_TIMEZONE to if (allDay) "UTC" else timeZone,
            Events.HAS_ALARM to if (reminders.isEmpty()) 0 else 1,
        )
        if (rule != null) {
            m[Events.RRULE] = rule
            m[Events.DURATION] = if (allDay) "P" + ((end - start) / 86_400_000L).coerceAtLeast(1) + "D" else "P" + ((end - start) / 1000L) + "S"
            m[Events.DTEND] = null
        } else {
            m[Events.RRULE] = null
            m[Events.DURATION] = null
            m[Events.DTEND] = end
        }
        return m
    }
}

/** RFC 5545 DURATION values as written by calendar sync adapters ("P3600S", "PT1H30M", "P1D", "P2W"). */
object Durations {
    private val RE = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

    fun parseMillis(value: String?): Long? {
        val m = RE.matchEntire(value?.trim()?.uppercase() ?: return null) ?: return null
        val g = m.groupValues
        fun n(i: Int) = g[i].toLongOrNull() ?: 0L
        val ms = ((n(2) * 7 + n(3)) * 86_400L + n(4) * 3600L + n(5) * 60L + n(6)) * 1000L
        return if (g[1] == "-") -ms else ms
    }
}
