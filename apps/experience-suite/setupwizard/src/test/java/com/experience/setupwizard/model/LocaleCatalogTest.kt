package com.experience.setupwizard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocaleCatalogTest {
    private val tags = arrayOf("en-US", "en-GB", "en-XA", "ar-XB", "de-DE", "fr", "sr-Latn-RS", "sr-RS", "", "es-419")

    @Test fun languagesAreDistinctNativeAndSkipPseudoLocales() {
        val langs = LocaleCatalog.languages(tags, Locale.ENGLISH)
        val byTag = langs.associateBy { it.tag }
        assertEquals(setOf("en", "de", "fr", "sr-Latn", "sr", "es"), byTag.keys)
        assertEquals("Deutsch", byTag.getValue("de").nativeName)
        assertEquals("German", byTag.getValue("de").localizedName)
        assertEquals("Français", byTag.getValue("fr").nativeName)
    }

    @Test fun regionsForLanguage() {
        val regions = LocaleCatalog.regions(tags, "en").map { it.country }
        assertEquals(setOf("US", "GB"), regions.toSet())
        assertTrue(LocaleCatalog.regions(tags, "fr").isEmpty())
        assertEquals(listOf("RS"), LocaleCatalog.regions(tags, "sr-Latn").map { it.country })
    }

    @Test fun allRegionsNamedInLanguage() {
        val all = LocaleCatalog.allRegions("de")
        assertTrue(all.size > 200)
        assertEquals("Österreich", all.first { it.country == "AT" }.name)
    }

    @Test fun composeKeepsScript() {
        assertEquals("sr-Latn-RS", LocaleCatalog.compose("sr-Latn", "RS").toLanguageTag())
        assertEquals("fr", LocaleCatalog.compose("fr", null).toLanguageTag())
    }

    @Test fun searchMatchesNativeLocalizedAndAccentFree() {
        val langs = LocaleCatalog.languages(tags, Locale.ENGLISH)
        assertEquals(listOf("fr"), LocaleCatalog.filterLanguages(langs, "francais").map { it.tag })
        assertEquals(listOf("de"), LocaleCatalog.filterLanguages(langs, "germ").map { it.tag })
        assertFalse(LocaleCatalog.filterRegions(LocaleCatalog.allRegions("en"), "united").isEmpty())
    }
}
