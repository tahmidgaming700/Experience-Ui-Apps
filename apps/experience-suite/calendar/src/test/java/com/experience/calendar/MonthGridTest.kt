package com.experience.calendar

import com.experience.calendar.logic.EventDays
import com.experience.calendar.logic.MonthGrid
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthGridTest {
    @Test fun leadingDaysDependOnFirstDayOfWeek() {
        // 1 November 2026 is a Sunday.
        val nov = YearMonth.of(2026, 11)
        assertEquals(0, MonthGrid.leadingDays(nov, DayOfWeek.SUNDAY))
        assertEquals(6, MonthGrid.leadingDays(nov, DayOfWeek.MONDAY))
        assertEquals(1, MonthGrid.leadingDays(nov, DayOfWeek.SATURDAY))
        assertEquals(LocalDate.of(2026, 10, 26), MonthGrid.firstVisibleDay(nov, DayOfWeek.MONDAY))
    }

    @Test fun weekCounts() {
        // February 2026 starts on Sunday and has 28 days: exactly 4 weeks when weeks start on Sunday.
        assertEquals(4, MonthGrid.weekCount(YearMonth.of(2026, 2), DayOfWeek.SUNDAY))
        assertEquals(5, MonthGrid.weekCount(YearMonth.of(2026, 2), DayOfWeek.MONDAY))
        // August 2026 starts on Saturday with 31 days: 6 weeks with Sunday start.
        assertEquals(6, MonthGrid.weekCount(YearMonth.of(2026, 8), DayOfWeek.SUNDAY))
    }

    @Test fun gridCoversWholeMonthInOrder() {
        for (m in 1..12) {
            val ym = YearMonth.of(2028, m) // leap year
            for (dow in DayOfWeek.entries) {
                val days = MonthGrid.days(ym, dow)
                assertEquals(42, days.size)
                assertEquals(dow, days.first().dayOfWeek)
                assertEquals(ym.lengthOfMonth(), days.count { YearMonth.from(it) == ym })
                days.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
                val compact = MonthGrid.days(ym, dow, fixedSixWeeks = false)
                assertEquals(MonthGrid.weekCount(ym, dow) * 7, compact.size)
                assertEquals(ym.atEndOfMonth().isAfter(compact.last()), false)
            }
        }
    }

    @Test fun leapFebruary() {
        val days = MonthGrid.days(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        assertEquals(29, days.count { it.monthValue == 2 })
    }

    @Test fun weekdayHeaders() {
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            MonthGrid.weekdays(DayOfWeek.SUNDAY))
    }

    @Test fun pagerMapping() {
        val anchor = YearMonth.of(2026, 11)
        assertEquals(YearMonth.of(2027, 1), MonthGrid.monthForPage(anchor, 100, 102))
        assertEquals(YearMonth.of(2025, 11), MonthGrid.monthForPage(anchor, 100, 88))
        assertEquals(88, MonthGrid.pageForMonth(anchor, 100, YearMonth.of(2025, 11)))
    }

    @Test fun julianDays() {
        assertEquals(LocalDate.of(1970, 1, 1), EventDays.julianToDate(2440588))
        val d = LocalDate.of(2026, 11, 15)
        assertEquals(d, EventDays.julianToDate(EventDays.dateToJulian(d)))
    }

    @Test fun timedEventEndingAtMidnightStaysOnItsDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val begin = LocalDate.of(2026, 11, 15).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        val span = EventDays.span(false, begin, end, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 15), span.start)
        assertEquals(LocalDate.of(2026, 11, 15), span.endInclusive)
        // Overnight event spans two days.
        val span2 = EventDays.span(false, begin, end + 3_600_000L, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 16), span2.endInclusive)
    }

    @Test fun allDayUsesProviderJulianDays() {
        val jd = EventDays.dateToJulian(LocalDate.of(2026, 3, 29))
        val span = EventDays.span(true, 0, 0, jd, jd + 2, ZoneId.of("America/New_York"))
        assertEquals(LocalDate.of(2026, 3, 29), span.start)
        assertEquals(LocalDate.of(2026, 3, 31), span.endInclusive)
    }

    @Test fun dayBoundsAcrossDst() {
        // 29 March 2026: Europe/Berlin springs forward, so the day is 23 hours long.
        val (s, e) = EventDays.dayBounds(LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Berlin"))
        assertEquals(23 * 3_600_000L, e - s)
    }
}
