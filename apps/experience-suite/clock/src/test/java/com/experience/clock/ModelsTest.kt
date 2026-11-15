package com.experience.clock

import com.experience.clock.api.ApiIntents
import com.experience.clock.alarm.Alarm
import com.experience.clock.stopwatch.Stopwatch
import com.experience.clock.timer.Timer
import com.experience.clock.ui.TimerInput
import com.experience.clock.ui.formatDuration
import com.experience.clock.ui.formatStopwatch
import com.experience.clock.world.WorldClocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

class ModelsTest {
    @Test fun timerLifecycle() {
        var t = Timer(1, 60_000)
        t = t.start(nowElapsed = 1_000, nowWall = 100_000)
        assertEquals(Timer.State.RUNNING, t.state)
        assertEquals(30_000, t.remaining(31_000))
        t = t.pause(31_000)
        assertEquals(30_000, t.remaining(99_999))
        t = t.addMinute(40_000, 0)
        assertEquals(90_000, t.remaining(0))
        t = t.start(50_000, 200_000)
        assertEquals(140_000, t.endElapsed)
        t = t.expire()
        assertEquals(Timer.State.EXPIRED, t.state)
        assertEquals(-5_000, t.remaining(145_000))
        t = t.addMinute(145_000, 300_000)
        assertEquals(Timer.State.RUNNING, t.state)
        assertEquals(60_000, t.remaining(145_000))
        t = t.reset()
        assertEquals(60_000, t.remaining(0))
        assertEquals(Timer.State.RESET, t.state)
    }

    @Test fun timerSurvivesRebootViaWallClock() {
        val t = Timer(1, 600_000).start(nowElapsed = 50_000, nowWall = 1_000_000)
        val after = t.afterReboot(nowElapsed = 10_000, nowWall = 1_100_000)
        assertEquals(500_000, after.remaining(10_000))
        val expired = t.afterReboot(nowElapsed = 10_000, nowWall = 2_000_000)
        assertEquals(Timer.State.EXPIRED, expired.state)
    }

    @Test fun stopwatchLapsBestAndWorst() {
        var s = Stopwatch().start(0)
        s = s.lap(10_000)   // lap 1: 10 s
        s = s.lap(14_000)   // lap 2: 4 s  (fastest)
        s = s.pause(20_000)
        s = s.start(100_000)
        s = s.lap(115_000)  // lap 3: 21 s (6 s before pause + 15 s after)
        assertEquals(listOf(10_000L, 4_000L, 21_000L), s.lapDurations())
        assertEquals(1 to 2, s.bestAndWorst())
        assertEquals(35_000, s.elapsed(115_000))
        assertEquals(Stopwatch(), s.reset())
        assertTrue(s.reset().isReset)
        assertNull(Stopwatch().start(0).lap(5).bestAndWorst())
        assertEquals(s, s.pause(115_000).let { s }) // pause is idempotent for the original
        assertFalse(s.pause(115_000).running)
        assertEquals(s.pause(115_000), s.pause(115_000).pause(200_000))
    }

    @Test fun stopwatchAfterReboot() {
        val s = Stopwatch().start(500_000).pause(600_000).start(700_000)
        val r = s.afterReboot(now = 1_000)
        assertFalse(r.running)
        assertEquals(100_000, r.elapsed(5_000))
    }

    @Test fun formatting() {
        assertEquals("5:00", formatDuration(300_000))
        assertEquals("0:01", formatDuration(1))       // rounds up while counting down
        assertEquals("1:05:00", formatDuration(3_900_000))
        assertEquals("−0:05", formatDuration(-5_400))
        assertEquals("01:02.35", formatStopwatch(62_350))
        assertEquals("1:00:00.00", formatStopwatch(3_600_000))
    }

    @Test fun timerKeypad() {
        var d = ""
        listOf("1", "3", "0").forEach { d = TimerInput.push(d, it) }
        assertEquals(Triple(0, 1, 30), TimerInput.fields(d))
        assertEquals(90_000, TimerInput.toMillis(d))
        assertEquals("", TimerInput.push("", "0"))
        assertEquals("1300", TimerInput.push("13", "00"))
        assertEquals("123456", TimerInput.push("123456", "7"))
        assertEquals("13", TimerInput.pop("130"))
        assertEquals(12 * 3600_000L + 34 * 60_000L + 56_000L, TimerInput.toMillis("123456"))
    }

    @Test fun worldClockNamesAndDifferences() {
        assertEquals("Buenos Aires", WorldClocks.cityName("America/Argentina/Buenos_Aires"))
        assertEquals("Port-au-Prince", WorldClocks.cityName("America/Port-au-Prince"))
        val now = Instant.parse("2026-11-15T15:00:00Z")
        val ny = ZoneId.of("America/New_York")
        assertEquals(-300, WorldClocks.differenceMinutes(ny, ZoneId.of("UTC"), now))
        assertEquals(630, WorldClocks.differenceMinutes(ZoneId.of("Asia/Kolkata"), ny, now))
        assertEquals("−5", WorldClocks.formatDifference(-300))
        assertEquals("+10:30", WorldClocks.formatDifference(630))
        assertEquals("+0:45", WorldClocks.formatDifference(45))
        assertEquals("0", WorldClocks.formatDifference(0))
        // 15:00 UTC = 10:00 New York, already 04:00 next day in Auckland (UTC+13).
        assertEquals(1, WorldClocks.dayDifference(ZoneId.of("Pacific/Auckland"), ny, now))
        assertEquals(0, WorldClocks.dayDifference(ZoneId.of("Europe/London"), ny, now))
        val zones = WorldClocks.allZones()
        assertTrue(zones.none { it.id.startsWith("Etc/") })
        assertTrue(WorldClocks.search(zones, "new york").any { it.id == "America/New_York" })
        assertTrue(WorldClocks.search(zones, "Australia").all { it.region == "Australia" || it.city.contains("Australia") || it.id.contains("Australia") })
    }

    @Test fun apiExtras() {
        val days = ApiIntents.daysFromCalendar(listOf(Calendar.MONDAY, Calendar.FRIDAY, Calendar.SUNDAY))
        assertEquals(Alarm.daysOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY), days)
        assertEquals(0, ApiIntents.daysFromCalendar(null))
        assertEquals(Alarm.EVERY_DAY, ApiIntents.daysFromCalendar((1..7).toList()))
        assertEquals(19, ApiIntents.hour24(7, true))
        assertEquals(0, ApiIntents.hour24(12, false))
        assertEquals(12, ApiIntents.hour24(12, true))
        assertEquals(7, ApiIntents.hour24(7, null))
        assertEquals(90_000L, ApiIntents.timerLengthMs(90))
        assertNull(ApiIntents.timerLengthMs(0))
        assertNull(ApiIntents.timerLengthMs(86_401))
    }
}
