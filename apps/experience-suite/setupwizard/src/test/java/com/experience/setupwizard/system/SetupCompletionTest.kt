package com.experience.setupwizard.system

import com.experience.setupwizard.FakeSystemSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCompletionTest {
    @Test fun runsAllStepsInOrder() = runBlocking {
        val system = FakeSystemSetup()
        val failures = SetupCompletion(system).run()
        assertTrue(failures.isEmpty())
        assertEquals(listOf("provisioned", "home:com.experience.launcher", "statusbar:false", "disable"), system.calls)
    }

    @Test fun skipsHomeRoleWhenLauncherMissing() = runBlocking {
        val system = FakeSystemSetup(launcherInstalled = false)
        SetupCompletion(system).run()
        assertEquals(listOf("provisioned", "statusbar:false", "disable"), system.calls)
    }

    @Test fun continuesAfterFailuresAndReportsThem() = runBlocking {
        val system = FakeSystemSetup(failing = setOf("provisioned", "home:com.experience.launcher"))
        val failures = SetupCompletion(system).run()
        assertEquals(2, failures.size)
        assertEquals("disable", system.calls.last())
    }
}
