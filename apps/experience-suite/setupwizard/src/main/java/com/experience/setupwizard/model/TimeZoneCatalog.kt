package com.experience.setupwizard.model

import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class ZoneOption(val id: String, val city: String, val area: String, val offsetMillis: Int, val offsetLabel: String)

/** Time-zone list for the date & time page. */
object TimeZoneCatalog {
    private val areas = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

    /** "GMT+05:30" style label for a UTC offset. */
    fun formatOffset(offsetMillis: Int): String {
        val minutes = abs(offsetMillis) / 60_000
        val sign = if (offsetMillis < 0) "-" else "+"
        return String.format(Locale.ROOT, "GMT%s%02d:%02d", sign, minutes / 60, minutes % 60)
    }

    /** Geographic zones (Area/City), sorted by current offset then city. */
    fun zones(ids: Array<String> = TimeZone.getAvailableIDs(), now: Long = System.currentTimeMillis()): List<ZoneOption> =
        ids.asSequence()
            .filter { id -> id.substringBefore('/') in areas && '/' in id && !id.startsWith("Etc/") }
            .map { id ->
                val offset = TimeZone.getTimeZone(id).getOffset(now)
                ZoneOption(
                    id = id,
                    city = id.substringAfterLast('/').replace('_', ' '),
                    area = id.substringBefore('/'),
                    offsetMillis = offset,
                    offsetLabel = formatOffset(offset),
                )
            }
            .sortedWith(compareBy<ZoneOption>({ it.offsetMillis }, { it.city }))
            .toList()

    fun filter(items: List<ZoneOption>, query: String): List<ZoneOption> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return items
        return items.filter {
            it.city.lowercase(Locale.ROOT).contains(q) || it.id.lowercase(Locale.ROOT).contains(q) || it.offsetLabel.lowercase(Locale.ROOT).contains(q)
        }
    }
}
