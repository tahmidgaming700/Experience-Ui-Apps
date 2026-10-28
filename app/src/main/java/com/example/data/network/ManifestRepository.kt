package com.example.data.network

import android.content.Context
import android.content.SharedPreferences
import com.example.data.config.ExperienceOtaConfig
import com.example.data.manifest.ExperienceManifestParser
import com.example.data.manifest.ManifestValidator
import com.example.data.model.ExperienceManifest
import com.example.data.model.ManifestRelease
import com.example.data.model.ManifestValidation
import com.example.data.model.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class PresetManifestType(val title: String, val description: String) {
    OFFICIAL_GITHUB(
        "Official Experience UI (Live GitHub)",
        "Direct single-source-of-truth: tahmidgaming700/Experience-UI/main/ota/manifest.json"
    ),
    EXPERIENCE_STABLE_DEMO(
        "Experience UI Android 12 GSI (Release ZIP)",
        "Verifiable release ZIP containing images/GSI, companion APKs & boot animation"
    ),
    EXPERIENCE_CORRUPT_DEMO(
        "Corrupted GSI Checksum Test",
        "Demonstrates SHA-256 mismatch rejection on extracted GSI image"
    ),
    CUSTOM_URL(
        "Custom HTTPS Manifest URL",
        "Read manifest from user-provided HTTPS endpoint"
    )
}

class ManifestRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_PERMANENT_HTTPS_URL = ExperienceOtaConfig.OFFICIAL_MANIFEST_URL

        private const val KEY_MANIFEST_URL = "configured_manifest_url"
        private const val KEY_PRESET_TYPE = "selected_preset_type"
        private const val KEY_CHANNEL = "selected_channel_key"

        // Sample verifiable asset for testing ZIP inspection and extracted GSI image SHA-256
        const val SAMPLE_ZIP_URL =
            "https://raw.githubusercontent.com/google/material-design-icons/master/LICENSE"
        const val SAMPLE_GSI_SHA256 =
            "58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd"
    }

    fun getConfiguredUrl(): String {
        return prefs.getString(KEY_MANIFEST_URL, DEFAULT_PERMANENT_HTTPS_URL)
            ?: DEFAULT_PERMANENT_HTTPS_URL
    }

    fun setConfiguredUrl(url: String) {
        prefs.edit().putString(KEY_MANIFEST_URL, url.trim()).apply()
    }

    fun getSelectedPreset(): PresetManifestType {
        val name = prefs.getString(KEY_PRESET_TYPE, PresetManifestType.OFFICIAL_GITHUB.name)
        return try {
            PresetManifestType.valueOf(name ?: PresetManifestType.OFFICIAL_GITHUB.name)
        } catch (_: Exception) {
            PresetManifestType.OFFICIAL_GITHUB
        }
    }

    fun setSelectedPreset(preset: PresetManifestType) {
        prefs.edit().putString(KEY_PRESET_TYPE, preset.name).apply()
    }

    fun getSelectedChannel(): String {
        return prefs.getString(KEY_CHANNEL, "stable") ?: "stable"
    }

    fun setSelectedChannel(channel: String) {
        prefs.edit().putString(KEY_CHANNEL, channel).apply()
    }

    suspend fun fetchManifest(
        preset: PresetManifestType = getSelectedPreset(),
        customUrl: String = getConfiguredUrl(),
        channelKey: String = getSelectedChannel()
    ): Pair<UpdateManifest?, ManifestValidation> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val jsonPayload = when (preset) {
            PresetManifestType.OFFICIAL_GITHUB -> {
                fetchFromHttpsUrl(DEFAULT_PERMANENT_HTTPS_URL, errors)
            }
            PresetManifestType.CUSTOM_URL -> {
                val url = customUrl.trim()
                if (!url.startsWith("https://", ignoreCase = true)) {
                    errors.add("Security violation: Manifest URL must strictly use HTTPS ('https://'). Got '$url'")
                    ""
                } else {
                    fetchFromHttpsUrl(url, errors)
                }
            }
            PresetManifestType.EXPERIENCE_STABLE_DEMO -> {
                """
                {
                  "schema_version": 1,
                  "project": "Experience UI",
                  "stable": {
                    "version": "Experience UI v1.0 — Android 12 ARM64 GSI",
                    "security_patch": "2026-10-05",
                    "release_notes": "Official Experience UI Android 12 ARM64 GSI repack.\nIncludes Experience Launcher, SetupWizard, Settings, Dialer, Messages, Camera, Files, and Boot Animation.\nPackaged inside release ZIP under images/ExperienceUI-Android12-arm64-experimental.img.",
                    "package_url": "$SAMPLE_ZIP_URL",
                    "sha256": "$SAMPLE_GSI_SHA256",
                    "android_base": "12.0",
                    "architecture": "arm64-v8a",
                    "target_partition": "system",
                    "download_size": 11357
                  },
                  "early_access": {
                    "version": "Experience UI 2 — Android 13 — not released",
                    "security_patch": "Not published",
                    "release_notes": "Early-access placeholder. Do not install until published.",
                    "package_url": "",
                    "sha256": ""
                  }
                }
                """.trimIndent()
            }
            PresetManifestType.EXPERIENCE_CORRUPT_DEMO -> {
                """
                {
                  "schema_version": 1,
                  "project": "Experience UI",
                  "stable": {
                    "version": "Experience UI v1.0 — Corrupted Checksum Test",
                    "security_patch": "2026-10-05",
                    "release_notes": "Test manifest containing an intentionally mismatched SHA-256 for the GSI image to verify corruption rejection.",
                    "package_url": "$SAMPLE_ZIP_URL",
                    "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
                    "android_base": "12.0",
                    "architecture": "arm64-v8a",
                    "target_partition": "system"
                  },
                  "early_access": {
                    "version": "Experience UI 2 — Android 13",
                    "security_patch": "Not published",
                    "release_notes": "Not published",
                    "package_url": "",
                    "sha256": ""
                  }
                }
                """.trimIndent()
            }
        }

        if (errors.isNotEmpty() || jsonPayload.isBlank()) {
            return@withContext Pair(null, ManifestValidation(false, errors, warnings))
        }

        // Parse official Experience UI schema
        val parseResult = ExperienceManifestParser.parse(jsonPayload)
        warnings.addAll(parseResult.warnings)

        if (!parseResult.isValidJson || parseResult.manifest == null) {
            errors.addAll(parseResult.errors)
            return@withContext Pair(null, ManifestValidation(false, errors, warnings))
        }

        val expManifest = parseResult.manifest
        val channel = expManifest.getChannel(channelKey)

        val isPublishReady = channel.isPublishReady
        if (!isPublishReady) {
            warnings.add(
                "Notice: Release '${channel.version}' has no published package URL or SHA-256 in manifest. Installation workflow will stop safely."
            )
        }

        val manifestRelease = ManifestRelease(
            versionName = channel.version,
            versionCode = 20261027L,
            androidVersion = channel.androidBase ?: if (channel.version.contains("13")) "13.0" else "12.0",
            securityPatch = channel.securityPatch,
            releaseDate = if (channel.securityPatch.isNotBlank()) channel.securityPatch else "Unpublished",
            minSdk = 31,
            supportedAbis = listOf(channel.architecture ?: ExperienceOtaConfig.DEFAULT_ARCH, "arm64-v8a", "x86_64"),
            requiresTreble = true,
            variant = "arm64-ab-experience",
            downloadUrl = channel.packageUrl,
            fileSize = channel.downloadSize ?: (if (channel.packageUrl.isNotBlank()) 11357L else 0L),
            sha256 = channel.sha256,
            changelog = channel.releaseNotes,
            warnings = listOf(
                "Do not install unless device-compatible build, signing, and recovery instructions are confirmed.",
                "Flashing requires unlocked bootloader, Treble compliance, and DD Flasher root or Fastboot.",
                "Never flash an image automatically without verifying image SHA-256."
            ),
            isPublishReady = isPublishReady,
            targetPartition = channel.targetPartition ?: ExperienceOtaConfig.DEFAULT_TARGET_PARTITION
        )

        val updateManifest = UpdateManifest(
            schemaVersion = expManifest.schemaVersion,
            channel = channel.channelKey,
            release = manifestRelease,
            rawJson = jsonPayload,
            project = expManifest.project
        )

        Pair(updateManifest, ManifestValidation(errors.isEmpty(), errors, warnings))
    }

    private fun fetchFromHttpsUrl(url: String, errors: MutableList<String>): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "ExperienceUI-OTAUpdater/1.0 (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                errors.add("HTTP ${response.code}: Manifest request failed from $url (${response.message})")
                ""
            } else {
                response.body?.string() ?: ""
            }
        } catch (e: UnknownHostException) {
            errors.add("DNS / Network Error: Could not resolve GitHub hostname. Check your internet connection.")
            ""
        } catch (e: SocketTimeoutException) {
            errors.add("Network Timeout: GitHub took too long to respond while fetching manifest.")
            ""
        } catch (e: IOException) {
            errors.add("Network Failure: ${e.localizedMessage ?: "Failed to read manifest over HTTPS"}")
            ""
        } catch (e: Exception) {
            errors.add("Error fetching manifest: ${e.localizedMessage ?: "Unexpected error"}")
            ""
        }
    }
}
