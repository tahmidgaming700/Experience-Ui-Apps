package com.experience.settings.update

import org.json.JSONException
import org.json.JSONObject

/** One release channel entry of the OTA manifest. */
data class ChannelBuild(
    val version: String,
    val buildNumber: String,
    val display: String,
    val date: String,
    val variant: String,
    /** Download URL of the compressed GSI image, or null when no build is published yet. */
    val url: String?,
    val size: Long,
    /** Lower-case hex SHA-256 of the downloaded file. */
    val sha256: String?,
    val changelog: String,
    val bootTested: Boolean,
) {
    /** File name used for the download (last URL path segment). */
    val fileName: String? get() = url?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

data class UpdateManifest(
    val schemaVersion: Int,
    val product: String,
    val androidVersion: String,
    val sdk: Int,
    val channels: Map<String, ChannelBuild>,
)

class ManifestException(message: String) : Exception(message)

sealed interface UpdateStatus {
    data class UpToDate(val build: ChannelBuild) : UpdateStatus
    data class Available(val build: ChannelBuild) : UpdateStatus
    /** The channel exists but has no downloadable image (url null). */
    data class NotPublished(val build: ChannelBuild) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * Parsing and decision logic of the Experience "foundation" updater. Pure (only
 * org.json), unit-tested under Robolectric.
 */
object UpdateLogic {
    const val SUPPORTED_SCHEMA = 1
    const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/tahmidgaming700/Experience-Ui-Apps/main/ota/experience-ui-11.json"
    val CHANNELS = listOf("stable", "beta")
    private val SHA256 = Regex("^[0-9a-fA-F]{64}$")

    fun parse(json: String): UpdateManifest {
        val root = try { JSONObject(json) } catch (e: JSONException) { throw ManifestException("The update manifest is not valid JSON") }
        val schema = root.optInt("schema_version", -1)
        if (schema != SUPPORTED_SCHEMA) throw ManifestException("Unsupported manifest schema ($schema); this updater understands version $SUPPORTED_SCHEMA")
        val channelsObj = root.optJSONObject("channels") ?: throw ManifestException("The update manifest has no channels")
        val channels = linkedMapOf<String, ChannelBuild>()
        for (name in channelsObj.keys()) {
            val c = channelsObj.optJSONObject(name) ?: throw ManifestException("Channel \"$name\" is malformed")
            channels[name] = parseChannel(name, c)
        }
        return UpdateManifest(
            schemaVersion = schema,
            product = root.optString("product"),
            androidVersion = root.optString("android_version"),
            sdk = root.optInt("sdk", -1),
            channels = channels,
        )
    }

    private fun parseChannel(name: String, c: JSONObject): ChannelBuild {
        fun req(key: String): String = c.optString(key, "").takeIf { it.isNotBlank() && !c.isNull(key) }
            ?: throw ManifestException("Channel \"$name\" is missing \"$key\"")
        val url = if (c.isNull("url")) null else c.optString("url", "").ifBlank { null }
        val sha = if (c.isNull("sha256")) null else c.optString("sha256", "").ifBlank { null }
        if (url != null) {
            if (!url.startsWith("https://")) throw ManifestException("Channel \"$name\" download must use HTTPS")
            if (sha == null || !SHA256.matches(sha)) throw ManifestException("Channel \"$name\" has no valid SHA-256 checksum")
        }
        return ChannelBuild(
            version = req("version"),
            buildNumber = req("build_number"),
            display = c.optString("display", "").ifBlank { "Experience UI ${req("version")}" },
            date = c.optString("date", ""),
            variant = c.optString("variant", ""),
            url = url,
            size = c.optLong("size", 0L),
            sha256 = sha?.lowercase(),
            changelog = c.optString("changelog", ""),
            bootTested = c.optBoolean("boot_tested", false),
        )
    }

    /** Build numbers are designed to sort lexically (e.g. 20250301.1 < 20250402.0). */
    fun isNewer(candidate: String, installed: String): Boolean = candidate > installed

    fun evaluate(manifest: UpdateManifest, channel: String, installedBuild: String, deviceSdk: Int): UpdateStatus {
        if (manifest.sdk != -1 && manifest.sdk != deviceSdk) {
            return UpdateStatus.Error("This manifest is for Android API ${manifest.sdk}; this device runs API $deviceSdk")
        }
        val build = manifest.channels[channel] ?: return UpdateStatus.Error("The \"$channel\" channel isn't published")
        return when {
            build.url == null -> UpdateStatus.NotPublished(build)
            isNewer(build.buildNumber, installedBuild) -> UpdateStatus.Available(build)
            else -> UpdateStatus.UpToDate(build)
        }
    }

    /** Fastbootd flashing steps for a GSI image (the app never flashes itself). */
    fun installSteps(compressedName: String): List<String> {
        val img = compressedName.removeSuffix(".xz")
        val steps = mutableListOf<String>()
        steps += "Copy $compressedName to a computer with Android platform-tools installed and back up your data."
        if (compressedName.endsWith(".xz")) steps += "Decompress it: xz -d $compressedName (produces $img)."
        steps += "Reboot this device into fastbootd: adb reboot fastboot"
        steps += "Flash the system image: fastboot flash system $img"
        steps += "If you are coming from another ROM or a different base build, wipe data: fastboot -w (this erases all user data)."
        steps += "Reboot: fastboot reboot"
        return steps
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
