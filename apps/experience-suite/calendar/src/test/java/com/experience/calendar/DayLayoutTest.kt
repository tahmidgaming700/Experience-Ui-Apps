package com.experience.calendar

import com.experience.calendar.logic.DayLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class DayLayoutTest {
    private data class E(val name: String, val s: Long, val e: Long)

    private fun run(vararg ev: E) = DayLayout.layout(ev.toList(), { it.s }, { it.e }).associateBy { it.item.name }

    @Test fun separateEventsUseFullWidth() {
        val r = run(E("a", 0, 10), E("b", 10, 20))
        assertEquals(1, r.getValue("a").columns)
        assertEquals(1, r.getValue("b").columns)
        assertEquals(0, r.getValue("b").column)
    }

    @Test fun overlappingEventsShareWidth() {
        val r = run(E("a", 0, 30), E("b", 10, 20), E("c", 15, 40))
        assertEquals(3, r.getValue("a").columns)
        assertEquals(0, r.getValue("a").column)
        assertEquals(1, r.getValue("b").column)
        assertEquals(2, r.getValue("c").column)
    }

    @Test fun freedColumnsAreReused() {
        val r = run(E("a", 0, 60), E("b", 0, 20), E("c", 30, 50))
        assertEquals(2, r.getValue("c").columns)
        assertEquals(1, r.getValue("c").column) // b's column is free again
    }

    @Test fun clustersAreIndependent() {
        val r = run(E("a", 0, 10), E("b", 5, 15), E("c", 100, 110))
        assertEquals(2, r.getValue("a").columns)
        assertEquals(1, r.getValue("c").columns)
    }
}
