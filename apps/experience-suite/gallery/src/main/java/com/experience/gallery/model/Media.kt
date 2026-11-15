package com.experience.gallery.model

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    /** Capture time when known (DATE_TAKEN), otherwise last modification, in ms. */
    val timestamp: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    /** Video duration in ms (0 for images). */
    val durationMs: Long,
    val bucketId: String,
    val bucketName: String,
    val path: String?,
    val favorite: Boolean,
    /** False for content opened from another app (not a MediaStore row we can delete/favourite). */
    val inMediaStore: Boolean = true,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

data class DaySection(val day: LocalDate, val items: List<MediaItem>)

data class Album(val bucketId: String, val name: String, val cover: MediaItem, val count: Int, val latest: Long)

/** Pure grouping/formatting helpers (unit-tested). */
object Timeline {
    /** Groups items (already newest-first) into calendar days in [zone], newest day first. */
    fun groupByDay(items: List<MediaItem>, zone: ZoneId = ZoneId.systemDefault()): List<DaySection> =
        items.sortedByDescending { it.timestamp }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .map { (day, list) -> DaySection(day, list) }

    /** Buckets as albums, most recently updated first; [cover] is each bucket's newest item. */
    fun albums(items: List<MediaItem>): List<Album> =
        items.groupBy { it.bucketId }.map { (id, list) ->
            val newest = list.maxBy { it.timestamp }
            Album(id, newest.bucketName.ifBlank { "Other" }, newest, list.size, newest.timestamp)
        }.sortedByDescending { it.latest }

    /** "Today", "Yesterday", "Monday, 3 March", or with the year for other years. */
    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(day)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(day)
    }

    /** Grid columns for a width: at least 3 on phones, ~120dp cells on larger screens. */
    fun columnsFor(widthDp: Int): Int = (widthDp / 120).coerceIn(3, 10)

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Which media the picker may return, from the caller's MIME type(s). */
    fun pickerAccepts(item: MediaItem, filters: List<String>): Boolean = filters.isEmpty() || filters.any { f ->
        f == "*/*" || f == item.mime || (f.endsWith("/*") && item.mime.startsWith(f.dropLast(1))) ||
            (f == "vnd.android.cursor.dir/image" && !item.isVideo) || (f == "vnd.android.cursor.dir/video" && item.isVideo)
    }
}
