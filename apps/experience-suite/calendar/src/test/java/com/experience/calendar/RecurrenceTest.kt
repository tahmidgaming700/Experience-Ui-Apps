package com.experience.calendar

import com.experience.calendar.logic.Recurrence
import com.experience.calendar.logic.RecurrenceEnd
import com.experience.calendar.logic.RecurrencePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceTest {
    private val zone = ZoneId.of("America/New_York")
    // Tuesday 17 November 2026, 09:30 (third Tuesday of the month).
    private val start = ZonedDateTime.of(2026, 11, 17, 9, 30, 0, 0, zone)

    @Test fun presets() {
        assertNull(Recurrence.build(RecurrencePreset.NONE, start))
        assertEquals("FREQ=DAILY", Recurrence.build(RecurrencePreset.DAILY, start))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", Recurrence.build(RecurrencePreset.WEEKDAYS, start))
        assertEquals("FREQ=WEEKLY;BYDAY=TU", Recurrence.build(RecurrencePreset.WEEKLY, start))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=17", Recurrence.build(RecurrencePreset.MONTHLY_BY_DATE, start))
        assertEquals("FREQ=MONTHLY;BYDAY=3TU", Recurrence.build(RecurrencePreset.MONTHLY_BY_WEEKDAY, start))
        assertEquals("FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=17", Recurrence.build(RecurrencePreset.YEARLY, start))
    }

    @Test fun lastWeekdayOfMonthUsesMinusOne() {
        // 24 November 2026 is the fourth Tuesday but not the last (Nov has 5 Tuesdays? 3,10,17,24 → 24 is last).
        assertEquals(-1, Recurrence.weekdayOrdinal(LocalDate.of(2026, 11, 24)))
        assertEquals(4, Recurrence.weekdayOrdinal(LocalDate.of(2026, 12, 22)))   // Dec has 1,8,15,22,29
        assertEquals(-1, Recurrence.weekdayOrdinal(LocalDate.of(2026, 12, 29)))
        assertEquals(1, Recurrence.weekdayOrdinal(LocalDate.of(2026, 12, 1)))
        val s = ZonedDateTime.of(2026, 12, 29, 10, 0, 0, 0, zone)
        assertEquals("FREQ=MONTHLY;BYDAY=-1TU", Recurrence.build(RecurrencePreset.MONTHLY_BY_WEEKDAY, s))
    }

    @Test fun endConditions() {
        assertEquals("FREQ=DAILY;COUNT=10", Recurrence.build(RecurrencePreset.DAILY, start, RecurrenceEnd.Count(10)))
        // Until 1 Dec 2026 (inclusive) in New York = 2 Dec 04:59:59 UTC (EST, UTC-5).
        assertEquals("FREQ=DAILY;UNTIL=20261202T045959Z",
            Recurrence.build(RecurrencePreset.DAILY, start, RecurrenceEnd.Until(LocalDate.of(2026, 12, 1))))
        assertEquals("FREQ=WEEKLY;BYDAY=TU;UNTIL=20261201",
            Recurrence.build(RecurrencePreset.WEEKLY, start, RecurrenceEnd.Until(LocalDate.of(2026, 12, 1)), allDay = true))
    }

    @Test fun recognisesPresetsAndEnds() {
        for (p in RecurrencePreset.entries.filter { it != RecurrencePreset.NONE && it != RecurrencePreset.CUSTOM }) {
            val rule = Recurrence.build(p, start, RecurrenceEnd.Count(3))
            assertEquals(p, Recurrence.presetOf(rule, start))
            assertEquals(RecurrenceEnd.Count(3), Recurrence.endOf(rule))
        }
        assertEquals(RecurrencePreset.NONE, Recurrence.presetOf(null, start))
        assertEquals(RecurrencePreset.WEEKLY, Recurrence.presetOf("FREQ=WEEKLY;WKST=SU;BYDAY=TU", start))
        assertEquals(RecurrencePreset.WEEKLY, Recurrence.presetOf("FREQ=WEEKLY", start))
        assertEquals(RecurrencePreset.CUSTOM, Recurrence.presetOf("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU", start))
        assertEquals(RecurrencePreset.CUSTOM, Recurrence.presetOf("FREQ=WEEKLY;BYDAY=MO,WE", start))
        assertEquals(RecurrenceEnd.Until(LocalDate.of(2026, 12, 2)), Recurrence.endOf("FREQ=DAILY;UNTIL=20261202T045959Z"))
        assertEquals(RecurrenceEnd.Never, Recurrence.endOf("FREQ=DAILY"))
    }
}
