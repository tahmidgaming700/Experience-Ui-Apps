package com.experience.design.system

import android.annotation.SuppressLint
import android.os.Build

/**
 * Reads Experience UI build identity from system properties.
 *
 * android.os.SystemProperties is a hidden API; Experience apps are platform-signed
 * system apps on Experience UI, and on any other system the lookup falls back to
 * public [Build] fields, so the apps still run (for example during development on
 * an emulator).
 */
object SystemInfo {
    @SuppressLint("PrivateApi")
    fun prop(key: String, default: String = ""): String = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        c.getMethod("get", String::class.java, String::class.java).invoke(null, key, default) as String
    }.getOrDefault(default)

    val isExperienceUi: Boolean get() = prop("ro.experience.version").isNotEmpty()
    val experienceVersion: String get() = prop("ro.experience.version", "—")
    val experienceDisplayVersion: String get() = prop("ro.experience.display.version", "Not running on Experience UI")
    val experienceBuildNumber: String get() = prop("ro.experience.build.number", Build.DISPLAY)
    val experienceReleaseChannel: String get() = prop("ro.experience.release.channel", "development")
    val experienceBuildDate: String get() = prop("ro.experience.build.date", "")
    val baseRom: String get() = prop("ro.experience.base", "")
    val androidVersion: String get() = Build.VERSION.RELEASE
    val sdkInt: Int get() = Build.VERSION.SDK_INT
    val securityPatch: String get() = Build.VERSION.SECURITY_PATCH
    val fingerprint: String get() = Build.FINGERPRINT
    val kernel: String get() = System.getProperty("os.version") ?: ""
    val vendorSecurityPatch: String get() = prop("ro.vendor.build.security_patch", "")
    val isGsi: Boolean get() = prop("ro.experience.gsi") == "1"
}
