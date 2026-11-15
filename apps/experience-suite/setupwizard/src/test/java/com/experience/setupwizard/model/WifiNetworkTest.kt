package com.experience.setupwizard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworkTest {
    @Test fun parsesSecurity() {
        assertEquals(WifiSecurity.PSK, WifiSecurity.fromCapabilities("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.PSK_SAE, WifiSecurity.fromCapabilities("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.SAE, WifiSecurity.fromCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.EAP, WifiSecurity.fromCapabilities("[WPA2-EAP/SHA1-CCMP][ESS]"))
        assertEquals(WifiSecurity.OWE, WifiSecurity.fromCapabilities("[RSN-OWE-CCMP][ESS]"))
        assertEquals(WifiSecurity.WEP, WifiSecurity.fromCapabilities("[WEP][ESS]"))
        assertEquals(WifiSecurity.OPEN, WifiSecurity.fromCapabilities("[ESS]"))
        assertEquals(WifiSecurity.OPEN, WifiSecurity.fromCapabilities(null))
    }

    @Test fun levels() {
        assertEquals(0, WifiNetworks.level(-110))
        assertEquals(4, WifiNetworks.level(-40))
        assertEquals(2, WifiNetworks.level(-75))
    }

    @Test fun dedupeKeepsStrongestAndPutsConnectedFirst() {
        val list = listOf(
            WifiNetwork("Home", -80, WifiSecurity.PSK),
            WifiNetwork("Home", -50, WifiSecurity.PSK),
            WifiNetwork("Cafe", -45, WifiSecurity.OPEN),
            WifiNetwork("", -30, WifiSecurity.OPEN),
            WifiNetwork("Office", -70, WifiSecurity.SAE),
        )
        val result = WifiNetworks.dedupe(list, connectedSsid = "Office")
        assertEquals(listOf("Office", "Cafe", "Home"), result.map { it.ssid })
        assertEquals(-50, result.last().rssi)
    }

    @Test fun passwordRules() {
        assertFalse(WifiNetworks.isValidPassword(WifiSecurity.PSK, "short"))
        assertTrue(WifiNetworks.isValidPassword(WifiSecurity.PSK, "longenough"))
        assertTrue(WifiNetworks.isValidPassword(WifiSecurity.PSK, "a".repeat(64)))
        assertFalse(WifiNetworks.isValidPassword(WifiSecurity.PSK, "z".repeat(64)))
        assertTrue(WifiNetworks.isValidPassword(WifiSecurity.OPEN, ""))
        assertFalse(WifiNetworks.isValidPassword(WifiSecurity.EAP, "whatever123"))
    }

    @Test fun unquote() {
        assertEquals("Home", WifiNetworks.unquote("\"Home\""))
        assertNull(WifiNetworks.unquote("<unknown ssid>"))
    }
}
