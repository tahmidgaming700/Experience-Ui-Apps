package com.experience.calculator

import com.experience.calculator.engine.NumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FormatterTest {
    @Test fun plainAndScientific() {
        assertEquals("0", NumberFormatter.format(BigDecimal("0.000")))
        assertEquals("12.5", NumberFormatter.format(BigDecimal("12.500")))
        assertEquals("-3", NumberFormatter.format(BigDecimal("-3.0")))
        assertEquals("123456789012345", NumberFormatter.format(BigDecimal("123456789012345")))
        assertEquals("1.23456789012346E15", NumberFormatter.format(BigDecimal("1234567890123456")))
        assertEquals("0.000001", NumberFormatter.format(BigDecimal("0.000001")))
        assertEquals("1E-7", NumberFormatter.format(BigDecimal("0.0000001")))
        assertEquals("-2.5E-9", NumberFormatter.format(BigDecimal("-0.0000000025")))
    }

    @Test fun grouping() {
        assertEquals("1,234,567.891", NumberFormatter.group("1234567.891"))
        assertEquals("12+1,000×3", NumberFormatter.group("12+1000×3"))
        assertEquals("1.5E2000", NumberFormatter.group("1.5E2000"))
        assertEquals("-1,000", NumberFormatter.group("-1000"))
        assertEquals("0.12345", NumberFormatter.group("0.12345"))
    }
}
