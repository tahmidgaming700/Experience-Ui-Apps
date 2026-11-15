package com.experience.clock

import com.experience.clock.alarm.Alarm
import com.experience.clock.alarm.NextAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextAlarmTest {
    private val ny = ZoneId.of("America/New_York")
    // Sunday 15 November 2026, 10:00 New York.
    private val sunday10 = ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, ny)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = ny) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test fun oneTimeAlarmLaterTodayOrTomorrow() {
        assertEquals(at(2026, 11, 15, 11, 30), NextAlarm.next(Alarm(hour = 11, minute = 30), sunday10))
        assertEquals(at(2026, 11, 16, 9, 0), NextAlarm.next(Alarm(hour = 9, minute = 0), sunday10))
        // Exactly now rings tomorrow, not immediately again.
        assertEquals(at(2026, 11, 16, 10, 0), NextAlarm.next(Alarm(hour = 10, minute = 0), sunday10))
    }

    @Test fun disabledAlarmHasNoNext() {
        assertNull(NextAlarm.next(Alarm(hour = 7, minute = 0, enabled = false), sunday10))
    }

    @Test fun repeatDays() {
        val weekdays = Alarm(hour = 7, minute = 0, days = Alarm.WEEKDAYS)
        assertEquals(at(2026, 11, 16, 7, 0), NextAlarm.next(weekdays, sunday10)) // Monday
        val fridayEvening = at(2026, 11, 20, 8, 0)
        assertEquals(at(2026, 11, 23, 7, 0), NextAlarm.next(weekdays, fridayEvening)) // skips the weekend
        val sundayOnly = Alarm(hour = 9, minute = 0, days = Alarm.daysOf(DayOfWeek.SUNDAY))
        assertEquals(at(2026, 11, 22, 9, 0), NextAlarm.next(sundayOnly, sunday10)) // a full week ahead
        val sundayLater = Alarm(hour = 22, minute = 0, days = Alarm.daysOf(DayOfWeek.SUNDAY))
        assertEquals(at(2026, 11, 15, 22, 0), NextAlarm.next(sundayLater, sunday10))
    }

    @Test fun springForwardGapRingsAfterTheGap() {
        // 8 March 2026: New York clocks jump from 02:00 to 03:00.
        val before = at(2026, 3, 7, 23, 0)
        val next = NextAlarm.next(Alarm(hour = 2, minute = 30), before)!!
        assertEquals(at(2026, 3, 8, 3, 30), next)
        assertEquals(-4 * 3600, next.offset.totalSeconds)
    }

    @Test fun fallBackOverlapRingsOnceAtFirstOccurrence() {
        // 1 November 2026: New York clocks go back from 02:00 to 01:00.
        val before = at(2026, 10, 31, 23, 0)
        val first = NextAlarm.next(Alarm(hour = 1, minute = 30, days = Alarm.EVERY_DAY), before)!!
        assertEquals(-4 * 3600, first.offset.totalSeconds) // EDT, the earlier 01:30
        // Just after it rang, the next one is the following day, not the repeated 01:30 EST.
        val after = NextAlarm.next(Alarm(hour = 1, minute = 30, days = Alarm.EVERY_DAY), first.plusMinutes(1))!!
        assertEquals(2, after.dayOfMonth)
    }

    @Test fun repeatingAlarmAcrossDstKeepsWallTime() {
        val daily = Alarm(hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        val sat = at(2026, 3, 7, 8, 0)
        val sun = NextAlarm.next(daily, sat)!!
        assertEquals(7, sun.hour)
        assertEquals(23 * 3600L, java.time.Duration.between(sat.withHour(7), sun).seconds) // 23-hour day
    }

    @Test fun timeZoneChangeKeepsLocalWallTime() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val nowInstant = sunday10.toInstant()
        val alarm = Alarm(hour = 7, minute = 0)
        val inNy = NextAlarm.next(alarm, nowInstant.atZone(ny))!!
        val inTokyo = NextAlarm.next(alarm, nowInstant.atZone(tokyo))!!
        assertEquals(7, inNy.hour)
        assertEquals(7, inTokyo.hour)
        assertEquals(tokyo, inTokyo.zone)
        // It is already Monday 00:00 in Tokyo, so 07:00 Monday Tokyo time.
        assertEquals(16, inTokyo.dayOfMonth)
    }

    @Test fun snoozeTakesPrecedence() {
        val snoozeUntil = sunday10.plusMinutes(9).toInstant().toEpochMilli()
        val a = Alarm(hour = 7, minute = 0, days = Alarm.EVERY_DAY, snoozedUntil = snoozeUntil)
        assertEquals(snoozeUntil, NextAlarm.next(a, sunday10)!!.toInstant().toEpochMilli())
        // An expired snooze is ignored.
        assertEquals(at(2026, 11, 16, 7, 0), NextAlarm.next(a, sunday10.plusMinutes(10)))
    }

    @Test fun skipNextOccurrence() {
        val daily = Alarm(hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        val skip = at(2026, 11, 16, 7, 0).toInstant().toEpochMilli()
        assertEquals(at(2026, 11, 17, 7, 0), NextAlarm.next(daily.copy(skipUntil = skip), sunday10))
    }

    @Test fun soonestOfSeveral() {
        val a = Alarm(id = 1, hour = 9, minute = 0)
        val b = Alarm(id = 2, hour = 10, minute = 30)
        val c = Alarm(id = 3, hour = 8, minute = 0, enabled = false)
        assertEquals(2L, NextAlarm.soonest(listOf(a, b, c), sunday10)!!.first.id)
        assertNull(NextAlarm.soonest(listOf(c), sunday10))
    }
}
