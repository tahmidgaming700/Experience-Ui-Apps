package com.experience.settings

import android.os.BatteryManager
import com.experience.settings.system.ColorMath
import com.experience.settings.system.OverlayEntry
import com.experience.settings.system.Overlays
import com.experience.settings.system.prettify
import com.experience.settings.ui.pages.BatteryParser
import com.experience.settings.ui.pages.NavMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PureLogicTest {
    @Test fun nearestAccentIsPerceptual() {
        val accents = mapOf(
            "com.experience.theme.accent.blue" to 0xFF1A73E8.toInt(),
            "com.experience.theme.accent.green" to 0xFF1E8E3E.toInt(),
            "com.android.theme.color.orchid" to 0xFFC42CC9.toInt(),
            "com.android.theme.color.amber" to 0xFFFFB300.toInt(),
        )
        assertEquals("com.experience.theme.accent.blue", ColorMath.nearest(0xFF4355B9.toInt(), accents))
        assertEquals("com.experience.theme.accent.green", ColorMath.nearest(0xFF386A20.toInt(), accents))
        assertEquals("com.android.theme.color.orchid", ColorMath.nearest(0xFFB4235A.toInt() + 0x003000A0, accents))
        assertEquals("com.android.theme.color.amber", ColorMath.nearest(0xFFFFA000.toInt(), accents))
        assertNull(ColorMath.nearest(0xFF000000.toInt(), emptyMap<String, Int>()))
        assertEquals(0.0, ColorMath.distance(0xFF123456.toInt(), 0xFF123456.toInt()), 1e-9)
        assertTrue(ColorMath.distance(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) > 99)
        assertEquals("#4355B9", ColorMath.hex(0xFF4355B9.toInt()))
    }

    @Test fun labOfWhiteAndBlack() {
        val w = ColorMath.toLab(0xFFFFFFFF.toInt())
        assertEquals(100.0, w.l, 0.01); assertEquals(0.0, w.a, 0.05); assertEquals(0.0, w.b, 0.05)
        assertEquals(0.0, ColorMath.toLab(0xFF000000.toInt()).l, 0.01)
    }

    @Test fun searchRanksTitleMatchesFirst() {
        val r = SettingsSearch.search("bright")
        assertEquals("Brightness", r.first().title)
        assertEquals(Page.DISPLAY, r.first().page)
        assertEquals(Page.CONNECTED, SettingsSearch.search("bluetooth").first().page)
        assertEquals(Page.UPDATES, SettingsSearch.search("ota").first().page)
        assertEquals(Page.STYLE, SettingsSearch.search("Accent").first().page)
        assertTrue(SettingsSearch.search("   ").isEmpty())
        assertTrue(SettingsSearch.search("zzzz-nothing").isEmpty())
        // Every page is reachable through search by its own title.
        Page.entries.forEach { p -> assertTrue(p.title, SettingsSearch.search(p.title).any { it.page == p }) }
    }

    @Test fun navigationModeFromOverlays() {
        fun e(pkg: String, on: Boolean) = OverlayEntry(pkg, Overlays.CATEGORY_NAVBAR, on, pkg)
        assertNull(NavMode.current(emptyList()))
        assertEquals(NavMode.GESTURE, NavMode.current(listOf(e(Overlays.NAV_GESTURAL, true), e(Overlays.NAV_THREE_BUTTON, false))))
        assertEquals(NavMode.TWO_BUTTON, NavMode.current(listOf(e(Overlays.NAV_TWO_BUTTON, true), e(Overlays.NAV_GESTURAL, false))))
        assertEquals(NavMode.THREE_BUTTON, NavMode.current(listOf(e(Overlays.NAV_GESTURAL, false), e(Overlays.NAV_THREE_BUTTON, false))))
    }

    @Test fun overlayLabels() {
        assertEquals("Ocean blue", prettify("com.experience.theme.accent.ocean_blue"))
        assertEquals("Orchid", prettify("com.android.theme.color.orchid"))
    }

    @Test fun batteryParsing() {
        val b = BatteryParser.parse(45, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB, 312, BatteryManager.BATTERY_HEALTH_GOOD, 4012, "Li-ion")
        assertEquals(45, b.percent); assertEquals("Charging", b.status); assertEquals("USB", b.plugged)
        assertEquals(31.2f, b.temperatureC!!, 0.001f); assertEquals("Good", b.health); assertEquals(4012, b.voltageMv); assertTrue(b.charging)
        val unknown = BatteryParser.parse(-1, 0, -1, 0, Int.MIN_VALUE, -1, -1, "")
        assertEquals(-1, unknown.percent); assertNull(unknown.plugged); assertNull(unknown.temperatureC); assertNull(unknown.technology)
        assertEquals(50, BatteryParser.parse(1, 2, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 250, 0, 0, null).percent)
    }
}
