package com.experience.dialer.data

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CallLogGrouperTest {
    private val zone: ZoneId = ZoneOffset.UTC

    private fun entry(id: Long, number: String, at: LocalDateTime, type: Int = CallLog.Calls.INCOMING_TYPE, presentation: Int = 1) = CallEntry(
        id = id, number = number, presentation = presentation, name = null, numberLabel = null, type = type,
        dateMillis = at.toInstant(ZoneOffset.UTC).toEpochMilli(), durationSeconds = 10, photoUri = null,
        lookupUri = null, accountId = null, isRead = true,
    )

    @Test fun groupsConsecutiveSameNumberSameDay() {
        val d = LocalDateTime.of(2026, 3, 10, 18, 0)
        val groups = CallLogGrouper.group(
            listOf(
                entry(1, "+44 7700 900123", d),
                entry(2, "07700 900123", d.minusHours(1), CallLog.Calls.MISSED_TYPE),
                entry(3, "555-1234", d.minusHours(2)),
                entry(4, "07700900123", d.minusHours(3)),
            ),
            zone,
        )
        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L)), groups.map { it.ids })
        assertEquals(2, groups[0].types.size)
    }

    @Test fun splitsAcrossDays() {
        val d = LocalDateTime.of(2026, 3, 10, 0, 30)
        val groups = CallLogGrouper.group(listOf(entry(1, "123456789", d), entry(2, "123456789", d.minusHours(1))), zone)
        assertEquals(2, groups.size)
    }

    @Test fun privateNumbersGroupTogether() {
        val d = LocalDateTime.of(2026, 3, 10, 12, 0)
        val groups = CallLogGrouper.group(listOf(entry(1, "", d, presentation = 2), entry(2, "", d.minusMinutes(5), presentation = 2), entry(3, "1", d.minusMinutes(6))), zone)
        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), groups.map { it.ids })
    }

    @Test fun numberMatching() {
        assertTrue(NumberMatch.same("+1 (650) 555-0100", "6505550100"))
        assertTrue(NumberMatch.same("07700 900123", "+447700900123"))
        assertFalse(NumberMatch.same("112", "911"))
        assertFalse(NumberMatch.same("5550100", "5550101"))
        assertTrue(NumberMatch.same("*100#", "*100#"))
    }

    @Test fun callKinds() {
        assertEquals(CallKind.MISSED, CallKind.of(CallLog.Calls.MISSED_TYPE))
        assertEquals(CallKind.BLOCKED, CallKind.of(CallLog.Calls.BLOCKED_TYPE))
        assertEquals(CallKind.REJECTED, CallKind.of(CallLog.Calls.REJECTED_TYPE))
        assertEquals(CallKind.UNKNOWN, CallKind.of(99))
    }

    @Test fun durations() {
        assertEquals("0:42", DurationFormat.clock(42))
        assertEquals("12:03", DurationFormat.clock(723))
        assertEquals("1:02:09", DurationFormat.clock(3729))
        assertEquals("42 s", DurationFormat.words(42))
        assertEquals("3 min 5 s", DurationFormat.words(185))
        assertEquals("1 h 2 min", DurationFormat.words(3720))
    }
}
