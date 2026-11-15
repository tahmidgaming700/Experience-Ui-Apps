package com.experience.gallery.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TimelineTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun item(id: Long, iso: String, bucket: String = "cam", mime: String = "image/jpeg", fav: Boolean = false) = MediaItem(
        id = id, uri = Uri.parse("content://media/external/images/media/$id"), name = "IMG_$id.jpg", mime = mime,
        timestamp = java.time.Instant.parse(iso).toEpochMilli(), size = 10, width = 4, height = 3, durationMs = 0,
        bucketId = bucket, bucketName = bucket.replaceFirstChar { it.uppercase() }, path = null, favorite = fav,
    )

    @Test fun groupsByDayNewestFirst() {
        val items = listOf(
            item(1, "2024-03-01T08:00:00Z"), item(2, "2024-03-02T23:59:00Z"),
            item(3, "2024-03-02T00:01:00Z"), item(4, "2024-02-28T12:00:00Z"),
        )
        val sections = Timeline.groupByDay(items, utc)
        assertEquals(listOf(LocalDate.of(2024, 3, 2), LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 28)), sections.map { it.day })
        assertEquals(listOf(2L, 3L), sections[0].items.map { it.id })
    }

    @Test fun dayBoundaryFollowsTimeZone() {
        val late = item(1, "2024-03-01T23:30:00Z")
        assertEquals(LocalDate.of(2024, 3, 1), Timeline.groupByDay(listOf(late), utc)[0].day)
        assertEquals(LocalDate.of(2024, 3, 2), Timeline.groupByDay(listOf(late), ZoneOffset.ofHours(2))[0].day)
    }

    @Test fun albumsAggregateBuckets() {
        val items = listOf(
            item(1, "2024-03-01T08:00:00Z", "cam"), item(2, "2024-03-05T08:00:00Z", "shots"),
            item(3, "2024-03-03T08:00:00Z", "cam"), item(4, "2024-01-01T08:00:00Z", "cam"),
        )
        val albums = Timeline.albums(items)
        assertEquals(listOf("shots", "cam"), albums.map { it.bucketId })
        assertEquals(3, albums[1].count)
        assertEquals(3L, albums[1].cover.id) // newest item is the cover
        assertEquals("Cam", albums[1].name)
    }

    @Test fun dayLabels() {
        val today = LocalDate.of(2024, 6, 15)
        assertEquals("Today", Timeline.dayLabel(today, today, Locale.UK))
        assertEquals("Yesterday", Timeline.dayLabel(today.minusDays(1), today, Locale.UK))
        assertEquals("Monday, 3 June", Timeline.dayLabel(LocalDate.of(2024, 6, 3), today, Locale.UK))
        assertTrue(Timeline.dayLabel(LocalDate.of(2022, 6, 3), today, Locale.UK).contains("2022"))
    }

    @Test fun columnsAdaptToWidth() {
        assertEquals(3, Timeline.columnsFor(360))
        assertEquals(3, Timeline.columnsFor(200))
        assertEquals(6, Timeline.columnsFor(720))
        assertEquals(10, Timeline.columnsFor(2400))
    }

    @Test fun durations() {
        assertEquals("0:05", Timeline.duration(5_400))
        assertEquals("2:03", Timeline.duration(123_000))
        assertEquals("1:00:00", Timeline.duration(3_600_000))
    }

    @Test fun pickerFilters() {
        val photo = item(1, "2024-01-01T00:00:00Z")
        val video = item(2, "2024-01-01T00:00:00Z", mime = "video/mp4")
        assertTrue(Timeline.pickerAccepts(photo, listOf("image/*")))
        assertFalse(Timeline.pickerAccepts(video, listOf("image/*")))
        assertTrue(Timeline.pickerAccepts(video, listOf("image/*", "video/*")))
        assertTrue(Timeline.pickerAccepts(video, emptyList()))
        assertTrue(Timeline.pickerAccepts(photo, listOf("*/*")))
        assertTrue(Timeline.pickerAccepts(photo, listOf("vnd.android.cursor.dir/image")))
        assertFalse(Timeline.pickerAccepts(photo, listOf("vnd.android.cursor.dir/video")))
        assertTrue(Timeline.pickerAccepts(photo, listOf("image/jpeg")))
        assertFalse(Timeline.pickerAccepts(photo, listOf("image/png")))
    }
}
