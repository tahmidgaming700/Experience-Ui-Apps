package com.experience.setupwizard.system

import java.util.Locale

/**
 * Read-only [SystemSetup] for "Setup preview": lets the wizard be walked through on a
 * device that is already set up (e.g. a phone that is not running Experience UI)
 * without changing anything. Every read comes from the real system; every change is
 * accepted and ignored, and the device always reports itself as not yet provisioned.
 */
class PreviewSystemSetup(private val real: SystemSetup) : SystemSetup by real {
    private val ignored = Result.success(Unit)

    override fun isProvisioned() = false
    override fun applyLocale(locale: Locale) = ignored
    override fun setFontScale(scale: Float) = ignored
    override fun setDensity(dpi: Int) = ignored
    override fun setSecureFlag(key: String, on: Boolean) = ignored
    override fun setAutoTime(on: Boolean) = ignored
    override fun setAutoTimeZone(on: Boolean) = ignored
    override fun setTimeZone(id: String) = ignored
    override fun setTime(millis: Long) = ignored
    override fun setLocationEnabled(on: Boolean) = ignored
    override fun setNavMode(mode: NavMode) = ignored
    override fun setStatusBarExpansionDisabled(disabled: Boolean) = ignored
    override suspend fun makeDefaultHome(packageName: String) = ignored
    override fun markProvisioned() = ignored
    override fun disableSetupComponent() = ignored
}
