package com.example.data.model

import java.io.File

data class ManifestRelease(
    val versionName: String,
    val versionCode: Long,
    val androidVersion: String,
    val securityPatch: String,
    val releaseDate: String,
    val minSdk: Int,
    val supportedAbis: List<String>,
    val requiresTreble: Boolean,
    val variant: String,
    val downloadUrl: String,
    val fileSize: Long,
    val sha256: String,
    val changelog: String,
    val warnings: List<String>,
    val isPublishReady: Boolean = true,
    val targetPartition: String = "system"
)

data class UpdateManifest(
    val schemaVersion: Int,
    val channel: String,
    val release: ManifestRelease,
    val rawJson: String = "",
    val project: String = "Experience UI"
)

data class ManifestValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val deviceCode: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val supportedAbis: List<String>,
    val primaryAbi: String,
    val isTrebleEnabled: Boolean,
    val hasDynamicPartitions: Boolean,
    val vndkVersion: String,
    val freeInternalBytes: Long,
    val totalInternalBytes: Long
)

data class DeviceCompatibility(
    val isCompatible: Boolean,
    val abiSupported: Boolean,
    val sdkSupported: Boolean,
    val trebleSupported: Boolean,
    val issues: List<String>
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data object CheckingStorage : DownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val etaSeconds: Long,
        val isResumed: Boolean
    ) : DownloadState
    data class Paused(
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState
    data class ExtractingZip(
        val bytesExtracted: Long,
        val totalBytes: Long,
        val percent: Int
    ) : DownloadState
    data class VerifyingHash(
        val bytesProcessed: Long,
        val totalBytes: Long,
        val progress: Float
    ) : DownloadState
    data class Verified(
        val file: File,
        val calculatedSha256: String,
        val expectedSha256: String,
        val matches: Boolean,
        val fileSize: Long,
        val isExtractedFromZip: Boolean = false,
        val zipInspection: ZipInspectionResult? = null
    ) : DownloadState
    data class HashMismatch(
        val file: File,
        val calculatedSha256: String,
        val expectedSha256: String
    ) : DownloadState
    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long
    ) : DownloadState
    data class Error(
        val title: String,
        val message: String,
        val canRetry: Boolean,
        val isNetworkFailure: Boolean = false
    ) : DownloadState
}
