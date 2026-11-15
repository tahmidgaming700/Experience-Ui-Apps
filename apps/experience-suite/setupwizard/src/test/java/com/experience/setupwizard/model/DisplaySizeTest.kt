package com.experience.setupwizard.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySizeTest {
    @Test fun densityRoundsToEven() {
        assertEquals(420, DisplaySize.densityFor(420, 1f))
        assertEquals(358, DisplaySize.densityFor(420, 0.85f))
        assertEquals(546, DisplaySize.densityFor(420, 1.3f))
    }

    @Test fun closestIndex() {
        assertEquals(1, DisplaySize.closestIndex(DisplaySize.fontSteps, 1.0f))
        assertEquals(3, DisplaySize.closestIndex(DisplaySize.fontSteps, 2.0f))
        assertEquals(2, DisplaySize.closestIndex(DisplaySize.densitySteps, 1.12f))
    }
}
