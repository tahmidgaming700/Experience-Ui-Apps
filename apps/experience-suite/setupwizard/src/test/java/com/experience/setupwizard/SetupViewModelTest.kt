package com.experience.setupwizard

import androidx.lifecycle.SavedStateHandle
import com.experience.setupwizard.model.SetupPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SetupViewModelTest {
    private val tags = arrayOf("en-US", "en-GB", "de-DE", "de-AT", "fr-FR")

    private fun vm(system: FakeSystemSetup = FakeSystemSetup(), handle: SavedStateHandle = SavedStateHandle()) =
        SetupViewModel(handle, system, tags)

    @Test fun navigatesAndPersistsPage() {
        val handle = SavedStateHandle()
        val model = vm(handle = handle)
        model.next()
        assertEquals(SetupPage.LANGUAGE, model.page.value)
        // A recreated activity gets a new ViewModel only after process death: state comes back from the handle.
        assertEquals(SetupPage.LANGUAGE, vm(handle = handle).page.value)
        model.back(); model.back()
        assertEquals(SetupPage.WELCOME, model.page.value)
    }

    @Test fun regionPageAppliesLanguageWithRegion() {
        val system = FakeSystemSetup()
        val model = vm(system)
        model.selectLanguage("de")
        assertEquals("DE", model.region.value) // first offered region of the new language
        model.selectRegion("AT")
        model.skip(); model.skip() // to REGION
        assertEquals(SetupPage.REGION, model.page.value)
        model.next()
        assertEquals(Locale.forLanguageTag("de-AT"), system.appliedLocale)
        assertEquals(SetupPage.ACCESSIBILITY, model.page.value)
    }

    @Test fun failuresAreReportedNotThrown() {
        val system = FakeSystemSetup(failing = setOf("font", "location"))
        val model = vm(system)
        model.setFontScale(1.3f)
        assertNotNull(model.message.value)
        assertEquals(1f, model.systemState.value.fontScale)
        model.consumeMessage()
        assertNull(model.message.value)
        model.setLocation(true)
        assertNotNull(model.message.value)
    }

    @Test fun densityStepUsesDefaultDensity() {
        val system = FakeSystemSetup()
        vm(system).setDensityStep(2)
        assertEquals("density:484", system.calls.last())
    }

    @Test fun accountsSurviveInHandle() {
        val handle = SavedStateHandle()
        vm(handle = handle).onAccountAdded("me@example.org (Mail)")
        assertEquals(listOf("me@example.org (Mail)"), vm(handle = handle).accountsAdded.value)
    }
}
