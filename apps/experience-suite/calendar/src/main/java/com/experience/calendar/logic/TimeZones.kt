package com.experience.calendar.logic

import java.time.Instant
import java.time.ZoneId

/** Selectable time zones with readable city names derived from IANA ids. */
object TimeZones {
    data class Zone(val id: String, val city: String, val region: String, val offsetSeconds: Int)

    /** "America/Argentina/Buenos_Aires" → "Buenos Aires". */
    fun cityName(id: String): String = id.substringAfterLast('/').replace('_', ' ')

    fun regionName(id: String): String = id.substringBefore('/').replace('_', ' ')

    /** "GMT+05:30" style label. */
    fun offsetLabel(seconds: Int): String {
        if (seconds == 0) return "GMT"
        val sign = if (seconds < 0) "-" else "+"
        val abs = kotlin.math.abs(seconds)
        return "GMT%s%02d:%02d".format(sign, abs / 3600, (abs % 3600) / 60)
    }

    /** Geographic zones only (skips legacy ids like "EST5EDT" and "Etc/GMT+3"), sorted by offset then city. */
    fun all(now: Instant = Instant.now()): List<Zone> = ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
        .map { Zone(it, cityName(it), regionName(it), ZoneId.of(it).rules.getOffset(now).totalSeconds) }
        .sortedWith(compareBy({ it.offsetSeconds }, { it.city }))

    fun search(zones: List<Zone>, query: String): List<Zone> {
        val q = query.trim()
        if (q.isEmpty()) return zones
        return zones.filter { it.city.contains(q, true) || it.id.contains(q.replace(' ', '_'), true) || offsetLabel(it.offsetSeconds).contains(q, true) }
    }
}
