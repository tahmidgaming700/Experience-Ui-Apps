package com.example.data.config

/**
 * Central configuration for Experience UI OTA Updater.
 * Single source of truth for the official GitHub OTA manifest URL.
 */
object ExperienceOtaConfig {
    /**
     * Mandatory Experience UI GitHub OTA manifest URL.
     * Raw content URL from the official repository branch.
     */
    const val OFFICIAL_MANIFEST_URL: String =
        "https://raw.githubusercontent.com/tahmidgaming700/Experience-UI/main/ota/manifest.json"

    const val PROJECT_NAME: String = "Experience UI"
    const val DEFAULT_ARCH: String = "arm64-v8a"
    const val DEFAULT_TARGET_PARTITION: String = "system"
}
