package com.experience.calendar

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.experience.calendar.logic.Durations
import com.experience.calendar.logic.EventDraft
import com.experience.calendar.logic.RecurrenceEnd
import com.experience.calendar.logic.RecurrencePreset
import com.experience.calendar.logic.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class EventDraftTest {
    private val ny = "America/New_York"
    private fun draft(allDay: Boolean = false) = EventDraft(
        calendarId = 3, title = " Standup ", allDay = allDay,
        startDate = LocalDate.of(2026, 11, 17), startTime = LocalTime.of(9, 30),
        endDate = LocalDate.of(2026, 11, 17), endTime = LocalTime.of(10, 0), timeZone = ny,
    )

    @Test fun timedEventValues() {
        val v = draft().toValues()
        val start = ZonedDateTime.of(2026, 11, 17, 9, 30, 0, 0, ZoneId.of(ny)).toInstant().toEpochMilli()
        assertEquals(start, v["dtstart"])
        assertEquals(start + 30 * 60_000L, v["dtend"])
        assertEquals(ny, v["eventTimezone"])
        assertEquals("Standup", v["title"])
        assertEquals(0, v["allDay"])
        assertNull(v["rrule"])
        assertNull(v["duration"])
        assertEquals(1, v["hasAlarm"])
    }

    @Test fun recurringEventsUseDurationNotDtend() {
        val v = draft().copy(preset = RecurrencePreset.WEEKDAYS, recurrenceEnd = RecurrenceEnd.Count(5)).toValues()
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;COUNT=5", v["rrule"])
        assertEquals("P1800S", v["duration"])
        assertTrue(v.containsKey("dtend"))
        assertNull(v["dtend"])
    }

    @Test fun allDayEventsAreUtcMidnightWithExclusiveEnd() {
        val d = draft(allDay = true).copy(endDate = LocalDate.of(2026, 11, 18))
        val v = d.toValues()
        assertEquals("UTC", v["eventTimezone"])
        assertEquals(LocalDate.of(2026, 11, 17).toEpochDay() * 86_400_000L, v["dtstart"])
        assertEquals(LocalDate.of(2026, 11, 19).toEpochDay() * 86_400_000L, v["dtend"])
        val rec = d.copy(preset = RecurrencePreset.YEARLY).toValues()
        assertEquals("P2D", rec["duration"])
    }

    @Test fun roundTripFromStored() {
        val d = draft().copy(preset = RecurrencePreset.MONTHLY_BY_WEEKDAY)
        val back = EventDraft.fromStored(9, 3, "Standup", "", "", false, d.startMillis(), d.endMillis(), ny, d.rrule(), listOf(10))
        assertEquals(d.startDate, back.startDate)
        assertEquals(d.startTime, back.startTime)
        assertEquals(d.endTime, back.endTime)
        assertEquals(RecurrencePreset.MONTHLY_BY_WEEKDAY, back.preset)

        val ad = draft(allDay = true)
        val adBack = EventDraft.fromStored(1, 3, "", "", "", true, ad.startMillis(), ad.endMillis(), "UTC", null, emptyList())
        assertEquals(ad.startDate, adBack.startDate)
        assertEquals(ad.endDate, adBack.endDate)
        assertTrue(adBack.allDay)
    }

    @Test fun validity() {
        assertTrue(draft().isValid)
        assertFalse(draft().copy(endTime = LocalTime.of(9, 0)).isValid)
        assertTrue(draft(allDay = true).isValid)
    }

    @Test fun durations() {
        assertEquals(3_600_000L, Durations.parseMillis("P3600S"))
        assertEquals(5_400_000L, Durations.parseMillis("PT1H30M"))
        assertEquals(86_400_000L, Durations.parseMillis("P1D"))
        assertEquals(14 * 86_400_000L, Durations.parseMillis("P2W"))
        assertNull(Durations.parseMillis("garbage"))
    }

    @Test fun insertIntentExtras() {
        val begin = ZonedDateTime.of(2026, 12, 24, 18, 0, 0, 0, ZoneId.of(ny)).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT).setType("vnd.android.cursor.dir/event")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 7_200_000L)
            .putExtra(CalendarContract.Events.TITLE, "Dinner")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, "Home")
            .putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY")
        val req = LaunchRequest.parse(intent) as LaunchRequest.Insert
        val d = req.apply(EventDraft.newAt(LocalDate.of(2026, 1, 1), ZoneId.of(ny)))
        assertEquals("Dinner", d.title)
        assertEquals("Home", d.location)
        assertEquals(LocalDate.of(2026, 12, 24), d.startDate)
        assertEquals(LocalTime.of(18, 0), d.startTime)
        assertEquals(LocalTime.of(20, 0), d.endTime)
        assertEquals(RecurrencePreset.YEARLY, d.preset)
    }

    @Test fun viewIntents() {
        val v = LaunchRequest.parse(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/events/42"))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 1000L))
        assertEquals(LaunchRequest.ViewEvent(42, 1000L, null), v)
        assertEquals(LaunchRequest.ViewTime(1_790_000_000_000L),
            LaunchRequest.parse(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/1790000000000"))))
        val edit = LaunchRequest.parse(Intent(Intent.ACTION_EDIT, Uri.parse("content://com.android.calendar/events/7")))
        assertEquals(7L, (edit as LaunchRequest.Edit).id)
    }

    @Test fun timeZoneNames() {
        assertEquals("Buenos Aires", TimeZones.cityName("America/Argentina/Buenos_Aires"))
        assertEquals("GMT+05:30", TimeZones.offsetLabel(19800))
        assertEquals("GMT-03:00", TimeZones.offsetLabel(-10800))
        val all = TimeZones.all()
        assertTrue(all.any { it.id == "Europe/London" })
        assertFalse(all.any { it.id.startsWith("Etc/") })
        assertTrue(TimeZones.search(all, "new york").any { it.id == "America/New_York" })
    }
}
