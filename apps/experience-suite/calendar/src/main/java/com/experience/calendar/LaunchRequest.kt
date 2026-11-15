package com.experience.calendar

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import com.experience.calendar.logic.EventDraft
import com.experience.calendar.logic.Recurrence
import com.experience.calendar.logic.RecurrencePreset
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** What an incoming intent asks for; applied once calendar permissions are granted. */
sealed interface LaunchRequest {
    data class ViewEvent(val id: Long, val begin: Long?, val end: Long?) : LaunchRequest
    data class ViewTime(val millis: Long) : LaunchRequest
    data class Insert(val apply: (EventDraft) -> EventDraft) : LaunchRequest
    data class Edit(val id: Long, val apply: (EventDraft) -> EventDraft) : LaunchRequest

    companion object {
        private const val AUTHORITY = CalendarContract.AUTHORITY

        /** Parses VIEW/INSERT/EDIT intents as sent by other apps (the Etar/AOSP contract). */
        fun parse(intent: Intent?): LaunchRequest? {
            intent ?: return null
            val data: Uri? = intent.data
            val segments = data?.pathSegments.orEmpty()
            val isCalendarUri = data?.scheme == "content" && data.authority == AUTHORITY
            return when (intent.action) {
                Intent.ACTION_VIEW -> when {
                    isCalendarUri && segments.size >= 2 && segments[0] == "events" ->
                        segments[1].toLongOrNull()?.let { id ->
                            ViewEvent(id, intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME), intent.longExtra(CalendarContract.EXTRA_EVENT_END_TIME))
                        }
                    isCalendarUri && segments.size >= 2 && segments[0] == "time" -> segments[1].toLongOrNull()?.let { ViewTime(it) }
                    else -> null
                }
                Intent.ACTION_INSERT -> Insert { extrasToDraft(intent, it) }
                Intent.ACTION_EDIT -> {
                    val id = if (isCalendarUri && segments.size >= 2 && segments[0] == "events") segments[1].toLongOrNull() else null
                    if (id != null) Edit(id) { extrasToDraft(intent, it) } else Insert { extrasToDraft(intent, it) }
                }
                else -> null
            }
        }

        private fun Intent.longExtra(key: String): Long? = if (hasExtra(key)) getLongExtra(key, 0L).takeIf { it > 0 } else null

        /** Applies CalendarContract extras (begin/end/all-day/title/…) on top of [base]. */
        fun extrasToDraft(intent: Intent, base: EventDraft): EventDraft {
            var d = base
            intent.getStringExtra(Events.TITLE)?.let { d = d.copy(title = it) }
            intent.getStringExtra(Events.DESCRIPTION)?.let { d = d.copy(description = it) }
            intent.getStringExtra(Events.EVENT_LOCATION)?.let { d = d.copy(location = it) }
            intent.getStringExtra(Events.EVENT_TIMEZONE)?.let { tz -> if (runCatching { ZoneId.of(tz) }.isSuccess) d = d.copy(timeZone = tz) }
            if (intent.hasExtra(Events.CALENDAR_ID)) d = d.copy(calendarId = intent.getLongExtra(Events.CALENDAR_ID, d.calendarId))
            val allDay = if (intent.hasExtra(CalendarContract.EXTRA_EVENT_ALL_DAY)) intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false) else d.allDay
            val zone = ZoneId.of(d.timeZone)
            val begin = intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME)
            val end = intent.longExtra(CalendarContract.EXTRA_EVENT_END_TIME)
            if (begin != null) {
                // All-day callers usually pass UTC midnight; use UTC dates for them.
                val s = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone)
                val e = Instant.ofEpochMilli(end?.takeIf { it > begin } ?: (begin + if (allDay) 86_400_000L else 3_600_000L))
                    .atZone(if (allDay) ZoneOffset.UTC else zone)
                val lastDay = if (allDay) e.minusNanos(1).toLocalDate().coerceAtLeast(s.toLocalDate()) else e.toLocalDate()
                d = d.copy(startDate = s.toLocalDate(), startTime = s.toLocalTime(), endDate = lastDay, endTime = e.toLocalTime())
            }
            d = d.copy(allDay = allDay)
            intent.getStringExtra(Events.RRULE)?.let { rule ->
                val start = ZonedDateTime.of(d.startDate, d.startTime, d.zone)
                val preset = Recurrence.presetOf(rule, start)
                d = d.copy(preset = preset, recurrenceEnd = Recurrence.endOf(rule), customRrule = if (preset == RecurrencePreset.CUSTOM) rule else null)
            }
            return d
        }
    }
}
