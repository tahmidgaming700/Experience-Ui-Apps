package com.experience.setupwizard.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFlowTest {
    @Test fun nextAndPreviousStopAtEnds() {
        assertEquals(SetupPage.WELCOME, SetupFlow.previous(SetupPage.WELCOME))
        assertEquals(SetupPage.DONE, SetupFlow.next(SetupPage.DONE))
        assertEquals(SetupPage.REGION, SetupFlow.next(SetupPage.LANGUAGE))
    }

    @Test fun progressAndSteps() {
        assertEquals(0f, SetupFlow.progress(SetupPage.WELCOME))
        assertEquals(1f, SetupFlow.progress(SetupPage.DONE))
        assertEquals(9, SetupFlow.stepCount)
        assertEquals(1, SetupFlow.step(SetupPage.LANGUAGE))
        assertEquals(9, SetupFlow.step(SetupPage.NAVIGATION))
    }

    @Test fun fromNameFallsBackToWelcome() {
        assertEquals(SetupPage.WIFI, SetupFlow.fromName("WIFI"))
        assertEquals(SetupPage.WELCOME, SetupFlow.fromName("bogus"))
        assertEquals(SetupPage.WELCOME, SetupFlow.fromName(null))
    }
}
