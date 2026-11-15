package com.experience.setupwizard

import com.experience.setupwizard.system.NavMode
import com.experience.setupwizard.system.SetupException
import com.experience.setupwizard.system.SystemSetup
import java.util.Locale

/** Records calls; operations listed in [failing] fail like they do off-ROM. */
class FakeSystemSetup(var failing: Set<String> = emptySet(), var launcherInstalled: Boolean = true) : SystemSetup {
    val calls = mutableListOf<String>()
    var appliedLocale: Locale? = null
    var provisioned = false

    private fun op(name: String, block: () -> Unit = {}): Result<Unit> {
        calls += name
        return if (name in failing) Result.failure(SetupException("Couldn't $name")) else { block(); Result.success(Unit) }
    }

    override fun isProvisioned() = provisioned
    override fun applyLocale(locale: Locale) = op("locale") { appliedLocale = locale }
    override fun fontScale() = 1f
    override fun setFontScale(scale: Float) = op("font")
    override fun defaultDensity() = 420
    override fun currentDensity() = 420
    override fun setDensity(dpi: Int) = op("density:$dpi")
    override fun isSecureFlagOn(key: String) = false
    override fun setSecureFlag(key: String, on: Boolean) = op("secure:$key")
    override fun isAutoTime() = true
    override fun setAutoTime(on: Boolean) = op("autotime")
    override fun isAutoTimeZone() = true
    override fun setAutoTimeZone(on: Boolean) = op("autozone")
    override fun setTimeZone(id: String) = op("zone")
    override fun setTime(millis: Long) = op("time")
    override fun isLocationEnabled() = false
    override fun setLocationEnabled(on: Boolean) = op("location")
    override fun navMode(): NavMode? = null
    override fun setNavMode(mode: NavMode) = op("nav")
    override fun setStatusBarExpansionDisabled(disabled: Boolean) = op("statusbar:$disabled")
    override fun isInstalled(packageName: String) = launcherInstalled
    override suspend fun makeDefaultHome(packageName: String) = op("home:$packageName")
    override fun markProvisioned() = op("provisioned") { provisioned = true }
    override fun disableSetupComponent() = op("disable")
}
