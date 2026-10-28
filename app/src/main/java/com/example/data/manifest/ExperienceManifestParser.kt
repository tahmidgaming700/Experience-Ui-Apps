package com.example.data.manifest

import com.example.data.model.ExperienceManifest
import com.example.data.model.ExperienceReleaseChannel
import org.json.JSONObject

data class ManifestParseResult(
    val manifest: ExperienceManifest?,
    val isValidJson: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

object ExperienceManifestParser {

    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

    fun parse(jsonString: String): ManifestParseResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (jsonString.isBlank()) {
            errors.add("Manifest content is empty.")
            return ManifestParseResult(null, false, errors, warnings)
        }

        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            errors.add("Malformed JSON: ${e.localizedMessage}")
            return ManifestParseResult(null, false, errors, warnings)
        }

        val schemaVersion = json.optInt("schema_version", -1)
        if (schemaVersion < 1) {
            warnings.add("Missing or invalid 'schema_version' (expected integer >= 1).")
        }

        val project = json.optString("project", "Experience UI").trim()

        val stableObj = json.optJSONObject("stable")
        val earlyAccessObj = json.optJSONObject("early_access")

        if (stableObj == null && earlyAccessObj == null) {
            errors.add("Manifest must contain at least one release channel ('stable' or 'early_access').")
            return ManifestParseResult(null, true, errors, warnings)
        }

        val stableChannel = parseChannel("stable", stableObj, warnings)
        val earlyAccessChannel = parseChannel("early_access", earlyAccessObj, warnings)

        val manifest = ExperienceManifest(
            schemaVersion = if (schemaVersion > 0) schemaVersion else 1,
            project = project,
            stable = stableChannel,
            earlyAccess = earlyAccessChannel,
            rawJson = jsonString
        )

        return ManifestParseResult(
            manifest = manifest,
            isValidJson = true,
            errors = errors,
            warnings = warnings
        )
    }

    private fun parseChannel(
        key: String,
        obj: JSONObject?,
        warnings: MutableList<String>
    ): ExperienceReleaseChannel {
        if (obj == null) {
            return ExperienceReleaseChannel(
                channelKey = key,
                version = "No $key release block",
                securityPatch = "Not specified",
                releaseNotes = "Channel not present in manifest.",
                packageUrl = "",
                sha256 = ""
            )
        }

        val version = obj.optString("version", "Unknown version").trim()
        val securityPatch = obj.optString("security_patch", "Not published").trim()
        val releaseNotes = obj.optString("release_notes", "No release notes available.").trim()
        val packageUrl = obj.optString("package_url", "").trim()
        val sha256 = obj.optString("sha256", "").trim().lowercase()

        // Optional fields if published
        val androidBase = if (obj.has("android_base")) obj.optString("android_base") else null
        val architecture = if (obj.has("architecture")) obj.optString("architecture") else null
        val targetPartition = if (obj.has("target_partition")) obj.optString("target_partition") else null
        val downloadSize = if (obj.has("download_size")) obj.optLong("download_size") else null

        if (packageUrl.isNotEmpty() && !packageUrl.startsWith("https://", ignoreCase = true)) {
            warnings.add("[$key] Insecure URL: 'package_url' must start with https://.")
        }

        if (sha256.isNotEmpty() && !SHA256_REGEX.matches(sha256)) {
            warnings.add("[$key] Invalid checksum: 'sha256' must be 64 hexadecimal characters.")
        }

        return ExperienceReleaseChannel(
            channelKey = key,
            version = version,
            securityPatch = securityPatch,
            releaseNotes = releaseNotes,
            packageUrl = packageUrl,
            sha256 = sha256,
            androidBase = androidBase,
            architecture = architecture,
            targetPartition = targetPartition,
            downloadSize = downloadSize
        )
    }
}
