package com.experience.setupwizard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeZoneCatalogTest {
    @Test fun formatsOffsets() {
        assertEquals("GMT+00:00", TimeZoneCatalog.formatOffset(0))
        assertEquals("GMT+05:30", TimeZoneCatalog.formatOffset(19_800_000))
        assertEquals("GMT-03:30", TimeZoneCatalog.formatOffset(-12_600_000))
    }

    @Test fun keepsGeographicZonesSortedByOffset() {
        val zones = TimeZoneCatalog.zones(arrayOf("Europe/Berlin", "America/New_York", "Etc/GMT+3", "UTC", "Asia/Kolkata"), now = 0L)
        assertEquals(listOf("America/New_York", "Europe/Berlin", "Asia/Kolkata"), zones.map { it.id })
        assertEquals("New York", zones.first().city)
    }

    @Test fun filtersByCityAndOffset() {
        val zones = TimeZoneCatalog.zones(arrayOf("Europe/Berlin", "Asia/Kolkata"), now = 0L)
        assertEquals(listOf("Asia/Kolkata"), TimeZoneCatalog.filter(zones, "kolk").map { it.id })
        assertEquals(listOf("Asia/Kolkata"), TimeZoneCatalog.filter(zones, "+05:30").map { it.id })
        assertTrue(TimeZoneCatalog.filter(zones, "").size == 2)
    }
}
