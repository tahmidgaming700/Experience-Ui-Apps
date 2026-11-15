package com.experience.calculator

import com.experience.calculator.engine.ExpressionEditor as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEditorTest {
    @Test fun operatorsReplaceEachOther() {
        assertEquals("2×", E.appendOperator("2+", '×'))
        assertEquals("2×−", E.appendOperator("2×", '−'))
        assertEquals("−", E.appendOperator("", '−'))
        assertEquals("", E.appendOperator("", '×'))
        assertEquals("(−", E.appendOperator("(", '−'))
    }

    @Test fun decimalsAndZeros() {
        assertEquals("0.", E.appendDecimal(""))
        assertEquals("1.5", E.appendDecimal("1.5"))
        assertEquals("2+0.", E.appendDecimal("2+"))
        assertEquals("7", E.appendDigit("0", '7'))
        assertEquals("0.07", E.appendDigit("0.0", '7'))
    }

    @Test fun parenthesesAndBackspace() {
        assertEquals("(2)", E.appendClose("(2"))
        assertEquals("(2+", E.appendClose("(2+"))
        assertEquals("2", E.appendClose("2"))
        assertEquals("2+", E.backspace("2+sin("))
        assertEquals("2+sin⁻¹(3", E.backspace("2+sin⁻¹(3)"))
        assertEquals("", E.backspace("sin⁻¹("))
    }

    @Test fun pasteSanitising() {
        assertEquals("1234×5", E.sanitizePaste("1,234*5"))
        assertEquals("2−3", E.sanitizePaste("2-3"))
    }

    @Test fun previewWorthy() {
        assertFalse(E.hasOperation("123"))
        assertFalse(E.hasOperation("−5"))
        assertTrue(E.hasOperation("1+2"))
        assertTrue(E.hasOperation("√9"))
    }
}
