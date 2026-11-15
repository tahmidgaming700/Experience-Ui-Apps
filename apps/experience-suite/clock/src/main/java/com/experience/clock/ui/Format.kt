package com.experience.clock.ui

import kotlin.math.abs

/** "5:00", "1:05:00"; countdowns round up so a timer never shows 0:00 while still running. */
fun formatDuration(ms: Long, roundUp: Boolean = true): String {
    val negative = ms < 0
    val a = abs(ms)
    val totalSeconds = if (roundUp && !negative) (a + 999) / 1000 else a / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val body = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    return if (negative) "−$body" else body
}

/** Stopwatch format with hundredths: "01:02.35", "1:01:02.35". */
fun formatStopwatch(ms: Long): String {
    val a = ms.coerceAtLeast(0)
    val h = a / 3_600_000
    val m = (a % 3_600_000) / 60_000
    val s = (a % 60_000) / 1000
    val cs = (a % 1000) / 10
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}

/**
 * Timer entry as on a keypad: digits shift in from the right into hh:mm:ss
 * ("1","3","0" → 00:01:30). At most 6 digits.
 */
object TimerInput {
    const val MAX_DIGITS = 6

    fun push(digits: String, d: String): String = (digits + d).trimStart('0').take(MAX_DIGITS)
    fun pop(digits: String): String = digits.dropLast(1)

    /** (hours, minutes, seconds) as typed; minutes/seconds may exceed 59 ("90" seconds is allowed). */
    fun fields(digits: String): Triple<Int, Int, Int> {
        val p = digits.padStart(MAX_DIGITS, '0')
        return Triple(p.substring(0, 2).toInt(), p.substring(2, 4).toInt(), p.substring(4, 6).toInt())
    }

    fun toMillis(digits: String): Long {
        val (h, m, s) = fields(digits)
        return ((h * 3600L) + (m * 60L) + s) * 1000L
    }
}
