package com.experience.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GridSpecTest {
    @Test fun drawerColumnsClamp() {
        assertEquals(4, GridSpec.drawerColumns(320))
        assertEquals(4, GridSpec.drawerColumns(411))
        assertEquals(6, GridSpec.drawerColumns(600))
        assertEquals(8, GridSpec.drawerColumns(1280))
    }

    @Test fun homeRowsClamp() {
        assertEquals(3, GridSpec.homeRows(100))
        assertEquals(5, GridSpec.homeRows(540))
        assertEquals(7, GridSpec.homeRows(2000))
    }

    @Test fun effectiveColumnsDependOnWidth() {
        assertEquals(5, GridSpec.effectiveHomeColumns(7, 400))
        assertEquals(7, GridSpec.effectiveHomeColumns(7, 800))
        assertEquals(4, GridSpec.effectiveHomeColumns(3, 800))
    }

    @Test fun pagesAlwaysAtLeastOne() {
        assertEquals(listOf(emptyList<Int>()), GridSpec.pages(emptyList<Int>(), 20))
        assertEquals(listOf(listOf(1, 2), listOf(3)), GridSpec.pages(listOf(1, 2, 3), 2))
    }
}
