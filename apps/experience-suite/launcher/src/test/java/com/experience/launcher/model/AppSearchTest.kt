package com.experience.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppSearchTest {
    private val apps = listOf("Calculator", "Calendar", "Camera", "Google Calendar", "Recall", "Éclair", "2048")

    @Test fun normalizeStripsAccents() {
        assertEquals("eclair", AppSearch.normalize("  Éclair "))
    }

    @Test fun ranksPrefixThenWordThenSubstring() {
        val r = AppSearch.search(apps, "cal") { it }
        assertEquals(listOf("Calculator", "Calendar", "Google Calendar", "Recall"), r)
    }

    @Test fun accentInsensitive() {
        assertEquals(listOf("Éclair"), AppSearch.search(apps, "ecl") { it })
    }

    @Test fun packageNameFallback() {
        val r = AppSearch.search(listOf("Phone" to "com.android.dialer"), "dialer", packageName = { it.second }, label = { it.first })
        assertEquals(1, r.size)
    }

    @Test fun emptyQueryReturnsAll() {
        assertEquals(apps, AppSearch.search(apps, "  ") { it })
    }

    @Test fun sortedIsCaseAndAccentInsensitive() {
        assertEquals(listOf("apple", "Banana", "éclair", "Zebra"), AppSearch.sorted(listOf("Zebra", "éclair", "Banana", "apple"), Locale.ENGLISH) { it })
    }

    @Test fun sections() {
        assertEquals("E", AppSearch.sectionOf("éclair"))
        assertEquals("#", AppSearch.sectionOf("2048"))
        assertEquals("#", AppSearch.sectionOf(""))
    }
}
