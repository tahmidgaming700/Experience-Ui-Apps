package com.example.data.model

import java.io.File

data class PartitionInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long? = null,
    val isSystemPartition: Boolean = false,
    val isWritable: Boolean = false
) {
    val displayLabel: String
        get() = "$name ($path)${if (sizeBytes != null) " - ${"%.1f".format(sizeBytes / (1024.0 * 1024.0))} MB" else ""}"
}

enum class AppNavTab(val title: String, val iconLabel: String) {
    OTA_UPDATER("OTA Updates", "Updates"),
    DD_FLASHER("DD Flasher", "Flasher"),
    DEVICE_PARTITIONS("Hardware & Safety", "Device")
}

data class ExperienceReleaseChannel(
    val channelKey: String,
    val version: String,
    val securityPatch: String,
    val releaseNotes: String,
    val packageUrl: String,
    val sha256: String,
    val androidBase: String? = null,
    val architecture: String? = null,
    val targetPartition: String? = null,
    val downloadSize: Long? = null
) {
    val isPackageUrlValid: Boolean
        get() = packageUrl.trim().startsWith("https://", ignoreCase = true)

    val isSha256Valid: Boolean
        get() = sha256.trim().matches(Regex("^[a-fA-F0-9]{64}$"))

    val isPublishReady: Boolean
        get() = isPackageUrlValid && isSha256Valid
}

data class ExperienceManifest(
    val schemaVersion: Int,
    val project: String,
    val stable: ExperienceReleaseChannel,
    val earlyAccess: ExperienceReleaseChannel,
    val rawJson: String = ""
) {
    fun getChannel(key: String): ExperienceReleaseChannel {
        return if (key.equals("early_access", ignoreCase = true)) earlyAccess else stable
    }
}

data class ZipInspectionResult(
    val isSuccess: Boolean,
    val zipFile: File,
    val imageEntryPath: String? = null,
    val extractedImageFile: File? = null,
    val extractedImageSha256: String? = null,
    val expectedSha256: String? = null,
    val isSha256Verified: Boolean = false,
    val apkCount: Int = 0,
    val hasBootAnimation: Boolean = false,
    val errorMessage: String? = null
)

sealed interface DDFlasherStatus {
    data object Idle : DDFlasherStatus
    data object CheckingPrivilege : DDFlasherStatus
    data class PrivilegeReport(
        val hasRoot: Boolean,
        val targetPartition: String,
        val partitionSize: Long?,
        val selinuxEnforcing: Boolean,
        val warning: String,
        val exactDdCommand: String,
        val fastbootCommand: String
    ) : DDFlasherStatus
    data class Flashing(
        val progressPercent: Int,
        val bytesWritten: Long,
        val speed: String,
        val lastLogLine: String = ""
    ) : DDFlasherStatus
    data class Success(
        val message: String,
        val targetPartition: String,
        val imagePath: String,
        val executionLogs: List<String> = emptyList()
    ) : DDFlasherStatus
    data class Failed(
        val error: String,
        val reason: String,
        val manualDdCommand: String,
        val executionLogs: List<String> = emptyList()
    ) : DDFlasherStatus
    data object Cancelled : DDFlasherStatus
}
