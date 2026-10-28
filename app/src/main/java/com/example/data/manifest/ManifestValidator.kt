package com.example.data.manifest

import com.example.data.model.ManifestRelease
import com.example.data.model.ManifestValidation
import com.example.data.model.UpdateManifest
import org.json.JSONObject

object ManifestValidator {

    private val VALID_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "riscv64")
    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

    fun parseAndValidate(jsonString: String): Pair<UpdateManifest?, ManifestValidation> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (jsonString.isBlank()) {
            errors.add("Manifest payload is empty.")
            return Pair(null, ManifestValidation(false, errors, warnings))
        }

        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            errors.add("Invalid JSON syntax: ${e.localizedMessage}")
            return Pair(null, ManifestValidation(false, errors, warnings))
        }

        // Schema version check
        val schemaVersion = json.optInt("schemaVersion", -1)
        if (schemaVersion < 1) {
            errors.add("Missing or invalid 'schemaVersion' (must be an integer >= 1).")
        }

        val channel = json.optString("channel", "").trim()
        if (channel.isEmpty()) {
            warnings.add("Release channel not specified; defaulting to 'stable'.")
        }

        val releaseObj = json.optJSONObject("release")
        if (releaseObj == null) {
            errors.add("Missing required 'release' object block.")
            return Pair(null, ManifestValidation(false, errors, warnings))
        }

        val versionName = releaseObj.optString("versionName", "").trim()
        if (versionName.isEmpty()) {
            errors.add("Missing required field 'release.versionName'.")
        }

        val versionCode = releaseObj.optLong("versionCode", -1L)
        if (versionCode <= 0) {
            errors.add("Missing or invalid 'release.versionCode' (must be a positive integer).")
        }

        val androidVersion = releaseObj.optString("androidVersion", "").trim()
        if (androidVersion.isEmpty()) {
            errors.add("Missing required field 'release.androidVersion'.")
        }

        val securityPatch = releaseObj.optString("securityPatch", "").trim()
        if (securityPatch.isEmpty()) {
            warnings.add("Missing 'release.securityPatch'.")
        }

        val releaseDate = releaseObj.optString("releaseDate", "").trim()
        val minSdk = releaseObj.optInt("minSdk", 28)
        val requiresTreble = releaseObj.optBoolean("requiresTreble", true)
        val variant = releaseObj.optString("variant", "system-generic")

        // Supported ABIs
        val abisArray = releaseObj.optJSONArray("supportedAbis")
        val supportedAbis = mutableListOf<String>()
        if (abisArray == null || abisArray.length() == 0) {
            errors.add("Missing or empty 'release.supportedAbis' array.")
        } else {
            for (i in 0 until abisArray.length()) {
                val abi = abisArray.optString(i, "").trim().lowercase()
                if (abi.isNotEmpty()) {
                    supportedAbis.add(abi)
                    if (!VALID_ABIS.contains(abi)) {
                        warnings.add("Uncommon ABI specified: '$abi'.")
                    }
                }
            }
        }

        // Download URL check
        val downloadUrl = releaseObj.optString("downloadUrl", "").trim()
        if (downloadUrl.isEmpty()) {
            errors.add("Missing required field 'release.downloadUrl'.")
        } else if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
            errors.add("Insecure download URL: '$downloadUrl'. HTTPS is strictly required for manifest security.")
        }

        // File size check
        val fileSize = releaseObj.optLong("fileSize", -1L)
        if (fileSize <= 0) {
            errors.add("Missing or non-positive 'release.fileSize' (found $fileSize bytes).")
        }

        // SHA-256 Checksum check
        val sha256 = releaseObj.optString("sha256", "").trim().lowercase()
        if (sha256.isEmpty()) {
            errors.add("Missing required 'release.sha256' cryptographic checksum.")
        } else if (!SHA256_REGEX.matches(sha256)) {
            errors.add("Invalid SHA-256 format: Expected a 64-character hexadecimal digest, got '${sha256.take(16)}...' (${sha256.length} chars).")
        }

        val changelog = releaseObj.optString("changelog", "No release notes provided.")
        val warningsArray = releaseObj.optJSONArray("warnings")
        val warningsList = mutableListOf<String>()
        if (warningsArray != null) {
            for (i in 0 until warningsArray.length()) {
                val w = warningsArray.optString(i, "").trim()
                if (w.isNotEmpty()) warningsList.add(w)
            }
        }

        val isValid = errors.isEmpty()
        val manifest = if (isValid) {
            UpdateManifest(
                schemaVersion = schemaVersion,
                channel = if (channel.isNotEmpty()) channel else "stable",
                release = ManifestRelease(
                    versionName = versionName,
                    versionCode = versionCode,
                    androidVersion = androidVersion,
                    securityPatch = if (securityPatch.isNotEmpty()) securityPatch else "Unknown",
                    releaseDate = if (releaseDate.isNotEmpty()) releaseDate else "Unknown",
                    minSdk = minSdk,
                    supportedAbis = supportedAbis,
                    requiresTreble = requiresTreble,
                    variant = variant,
                    downloadUrl = downloadUrl,
                    fileSize = fileSize,
                    sha256 = sha256,
                    changelog = changelog,
                    warnings = warningsList
                ),
                rawJson = jsonString
            )
        } else {
            null
        }

        return Pair(manifest, ManifestValidation(isValid, errors, warnings))
    }
}
