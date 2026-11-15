package com.experience.dialer.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialCodesTest {
    @Test fun secretCodes() {
        assertEquals("4636", SpecialCodes.secretCodeOf("*#*#4636#*#*"))
        assertNull(SpecialCodes.secretCodeOf("*#*#4636#*#"))
        assertNull(SpecialCodes.secretCodeOf("4636"))
    }

    @Test fun deviceIdCode() {
        assertTrue(SpecialCodes.isDeviceIdCode("*#06#"))
        assertFalse(SpecialCodes.isDeviceIdCode("*#06"))
    }
}
