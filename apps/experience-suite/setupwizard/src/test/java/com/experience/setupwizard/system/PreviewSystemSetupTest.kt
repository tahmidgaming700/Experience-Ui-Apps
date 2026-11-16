package com.experience.setupwizard.system

import com.experience.setupwizard.FakeSystemSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PreviewSystemSetupTest {
    @Test
    fun previewNeverChangesTheDevice() = runBlocking {
        val real = FakeSystemSetup()
        val preview = PreviewSystemSetup(real)
        assertFalse(preview.isProvisioned())
        assertTrue(preview.markProvisioned().isSuccess)
        assertTrue(preview.disableSetupComponent().isSuccess)
        assertTrue(preview.makeDefaultHome("com.experience.launcher").isSuccess)
        assertTrue(preview.applyLocale(Locale.GERMANY).isSuccess)
        assertTrue(preview.setFontScale(1.3f).isSuccess)
        assertTrue(preview.setNavMode(NavMode.GESTURE).isSuccess)
        assertTrue("fake must record no changes: ${real.calls}", real.calls.isEmpty())
    }
}
