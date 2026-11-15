package com.experience.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmartDialTest {
    @Test fun lettersMapToKeys() {
        assertEquals("5646", SmartDial.toDigits("John"))
        assertEquals("76484", SmartDial.toDigits("Smith"))
        assertEquals("9999", SmartDial.toDigits("wxyz"))
        assertEquals("7777", SmartDial.toDigits("PQRS"))
    }

    @Test fun accentsAreFolded() {
        assertEquals("963", SmartDial.toDigits("Zoë"))
        assertEquals("3", SmartDial.toDigits("É"))
    }

    @Test fun matchesWordStart() {
        val m = SmartDial.matchName("John Smith", "5646")
        assertNotNull(m)
        assertEquals(SmartDial.Kind.NAME_WORD, m!!.kind)
        assertEquals(0..3, m.nameRange)
        assertEquals(5..9, SmartDial.matchName("John Smith", "76484")!!.nameRange)
    }

    @Test fun matchesAcrossWordBoundary() {
        assertNotNull(SmartDial.matchName("John Smith", "564676"))
    }

    @Test fun doesNotMatchMiddleOfWord() {
        assertNull(SmartDial.matchName("John Smith", "46"))
    }

    @Test fun matchesInitials() {
        val m = SmartDial.matchName("John Smith", "57")
        assertEquals(SmartDial.Kind.NAME_INITIALS, m!!.kind)
        assertEquals(SmartDial.Kind.NAME_INITIALS, SmartDial.matchName("Mary Ann Lee", "625")!!.kind)
        assertNull(SmartDial.matchName("Mary Ann Lee", "65"))
    }

    @Test fun matchesNumbers() {
        assertEquals(SmartDial.Kind.NUMBER_PREFIX, SmartDial.matchNumber("0151 234 5678", "0151")!!.kind)
        assertEquals(SmartDial.Kind.NUMBER_PREFIX, SmartDial.matchNumber("+44 151 234", "44")!!.kind)
        assertEquals(SmartDial.Kind.NUMBER_CONTAINS, SmartDial.matchNumber("+1 (555) 123-4567", "1234")!!.kind)
        assertNull(SmartDial.matchNumber("555-0000", "999"))
    }

    @Test fun ignoresNonDigitsInQuery() {
        assertNull(SmartDial.matchName("Anna", "*#"))
        assertNotNull(SmartDial.matchName("Anna", "+2662"))
    }

    @Test fun filterRanksNumberPrefixFirst() {
        data class C(val name: String, val number: String)
        val items = listOf(C("Bob", "555 2000"), C("Alice", "2000 1234"), C("Carl", "777"))
        val result = SmartDial.filter(items, "2", { it.name }, { it.number })
        // Alice: number prefix; Bob: name (B=2) and number contains → name word; Carl: C=2 name word.
        assertEquals(listOf("Alice", "Bob", "Carl"), result.map { it.first.name })
        assertEquals(emptyList<Any>(), SmartDial.filter(items, "", { it.name }, { it.number }))
    }
}
