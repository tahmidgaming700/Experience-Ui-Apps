package com.experience.setupwizard.system

import java.util.Locale

enum class NavMode { GESTURE, THREE_BUTTON }

/** A system change that could not be applied; [message] is shown to the user. */
class SetupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Every system-mutating operation the wizard performs. The production
 * implementation ([AndroidSystemSetup]) uses privileged and hidden APIs that only
 * work for the platform-signed priv-app; each call returns a [Result] so a failure
 * is reported in the UI instead of crashing, and tests substitute a fake.
 */
interface SystemSetup {
    fun isProvisioned(): Boolean

    /** Applies the system locale list (LocalePicker.updateLocales). */
    fun applyLocale(locale: Locale): Result<Unit>

    fun fontScale(): Float
    fun setFontScale(scale: Float): Result<Unit>

    /** The display's initial (factory) density, or null when it cannot be read. */
    fun defaultDensity(): Int?
    fun currentDensity(): Int
    /** Forces [dpi]; when [dpi] equals the default the override is cleared. */
    fun setDensity(dpi: Int): Result<Unit>

    fun isSecureFlagOn(key: String): Boolean
    fun setSecureFlag(key: String, on: Boolean): Result<Unit>

    fun isAutoTime(): Boolean
    fun setAutoTime(on: Boolean): Result<Unit>
    fun isAutoTimeZone(): Boolean
    fun setAutoTimeZone(on: Boolean): Result<Unit>
    fun setTimeZone(id: String): Result<Unit>
    fun setTime(millis: Long): Result<Unit>

    fun isLocationEnabled(): Boolean
    fun setLocationEnabled(on: Boolean): Result<Unit>

    fun navMode(): NavMode?
    fun setNavMode(mode: NavMode): Result<Unit>

    /** Prevents pulling down the notification shade while setup runs. */
    fun setStatusBarExpansionDisabled(disabled: Boolean): Result<Unit>

    /** True when [packageName] is installed for this user. */
    fun isInstalled(packageName: String): Boolean

    /**
     * Makes [packageName] the HOME role holder (RoleManager). Succeeds without doing
     * anything when it already holds the role.
     */
    suspend fun makeDefaultHome(packageName: String): Result<Unit>

    /** Settings.Global.DEVICE_PROVISIONED = 1 and Settings.Secure.USER_SETUP_COMPLETE = 1. */
    fun markProvisioned(): Result<Unit>

    /** Disables the wizard's HOME activity so HOME resolves to the launcher. */
    fun disableSetupComponent(): Result<Unit>

    companion object {
        const val SECURE_INVERSION = "accessibility_display_inversion_enabled"
        const val SECURE_HIGH_TEXT_CONTRAST = "high_text_contrast_enabled"
        const val LAUNCHER_PACKAGE = "com.experience.launcher"
    }
}
