package com.experience.dialer.data

import android.provider.CallLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One row of CallLog.Calls. [type] is a CallLog.Calls.*_TYPE constant. */
data class CallEntry(
    val id: Long,
    val number: String,
    val presentation: Int,
    val name: String?,
    val numberLabel: String?,
    val type: Int,
    val dateMillis: Long,
    val durationSeconds: Long,
    val photoUri: String?,
    val lookupUri: String?,
    val accountId: String?,
    val isRead: Boolean,
)

/** Consecutive calls with the same number on the same day, newest first. */
data class CallGroup(val entries: List<CallEntry>) {
    val latest: CallEntry get() = entries.first()
    val ids: List<Long> get() = entries.map { it.id }
    val count: Int get() = entries.size
    val types: List<Int> get() = entries.map { it.type }.distinct()
}

enum class CallKind(val label: String) {
    INCOMING("Incoming"), OUTGOING("Outgoing"), MISSED("Missed"), VOICEMAIL("Voicemail"),
    REJECTED("Declined"), BLOCKED("Blocked"), ANSWERED_ELSEWHERE("Answered on another device"), UNKNOWN("Call");

    companion object {
        fun of(type: Int) = when (type) {
            CallLog.Calls.INCOMING_TYPE -> INCOMING
            CallLog.Calls.OUTGOING_TYPE -> OUTGOING
            CallLog.Calls.MISSED_TYPE -> MISSED
            CallLog.Calls.VOICEMAIL_TYPE -> VOICEMAIL
            CallLog.Calls.REJECTED_TYPE -> REJECTED
            CallLog.Calls.BLOCKED_TYPE -> BLOCKED
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> ANSWERED_ELSEWHERE
            else -> UNKNOWN
        }
    }
}

object NumberMatch {
    /**
     * True when two numbers dial the same subscriber. Exact digit equality, or – for
     * numbers long enough to be national/international variants of each other
     * ("07700 900123" vs "+44 7700 900123") – equality of the trailing digits.
     */
    fun same(a: String, b: String): Boolean {
        val da = a.filter { it in '0'..'9' }
        val db = b.filter { it in '0'..'9' }
        if (da.isEmpty() || db.isEmpty()) return a == b
        if (da == db) return true
        val shortest = minOf(da.length, db.length)
        if (shortest < 7) return false
        val n = minOf(9, shortest)
        return da.takeLast(n) == db.takeLast(n)
    }
}

object CallLogGrouper {
    /** Groups [entries] (newest first) by number and local day. Private numbers group by presentation. */
    fun group(entries: List<CallEntry>, zone: ZoneId = ZoneId.systemDefault()): List<CallGroup> {
        val out = ArrayList<CallGroup>()
        var current = ArrayList<CallEntry>()
        var currentDay: LocalDate? = null
        for (e in entries) {
            val day = Instant.ofEpochMilli(e.dateMillis).atZone(zone).toLocalDate()
            val head = current.firstOrNull()
            val sameKey = head != null && day == currentDay && head.presentation == e.presentation &&
                (if (head.number.isBlank() || e.number.isBlank()) head.number.isBlank() && e.number.isBlank() else NumberMatch.same(head.number, e.number))
            if (!sameKey && current.isNotEmpty()) {
                out += CallGroup(current)
                current = ArrayList()
            }
            current += e
            currentDay = day
        }
        if (current.isNotEmpty()) out += CallGroup(current)
        return out
    }
}

object DurationFormat {
    /** "0:42", "12:03", "1:02:09". */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }

    /** Spoken/longer form for lists: "42 s", "3 min 5 s", "1 h 2 min". */
    fun words(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> if (m > 0) "$h h $m min" else "$h h"
            m > 0 -> if (sec > 0) "$m min $sec s" else "$m min"
            else -> "$sec s"
        }
    }
}
