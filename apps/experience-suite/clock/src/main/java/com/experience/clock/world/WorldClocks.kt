package com.experience.clock.world

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure helpers for world clocks: readable names and differences from local time. */
object WorldClocks {
    data class Zone(val id: String, val city: String, val region: String)

    /** "America/Argentina/Buenos_Aires" → "Buenos Aires"; "America/Port-au-Prince" keeps its hyphens. */
    fun cityName(id: String): String = id.substringAfterLast('/').replace('_', ' ')
    fun regionName(id: String): String = id.substringBefore('/').replace('_', ' ')

    /** Geographic IANA zones (no "Etc/…", "SystemV/…" or legacy abbreviations), sorted by city. */
    fun allZones(): List<Zone> = ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
        .map { Zone(it, cityName(it), regionName(it)) }
        .sortedBy { it.city }

    fun search(zones: List<Zone>, query: String): List<Zone> {
        val q = query.trim()
        if (q.isEmpty()) return zones
        return zones.filter { it.city.contains(q, ignoreCase = true) || it.region.contains(q, ignoreCase = true) || it.id.contains(q.replace(' ', '_'), ignoreCase = true) }
    }

    /** Offset of [zone] relative to [local] at [now], in minutes (positive = ahead). */
    fun differenceMinutes(zone: ZoneId, local: ZoneId, now: Instant): Int =
        (zone.rules.getOffset(now).totalSeconds - local.rules.getOffset(now).totalSeconds) / 60

    /** -1 = yesterday, 0 = today, 1 = tomorrow there, relative to the local date. */
    fun dayDifference(zone: ZoneId, local: ZoneId, now: Instant): Int =
        ChronoUnit.DAYS.between(now.atZone(local).toLocalDate(), now.atZone(zone).toLocalDate()).toInt()

    /** Compact signed difference: "+3", "−5:30", "+0:45"; "0" for the same time. */
    fun formatDifference(minutes: Int): String {
        if (minutes == 0) return "0"
        val sign = if (minutes > 0) "+" else "−"
        val abs = kotlin.math.abs(minutes)
        val h = abs / 60
        val m = abs % 60
        return if (m == 0) "$sign$h" else "$sign$h:%02d".format(m)
    }
}
