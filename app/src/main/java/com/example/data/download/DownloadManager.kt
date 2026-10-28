package com.example.data.download

import android.content.Context
import com.example.data.device.DeviceInspector
import com.example.data.model.DownloadState
import com.example.data.model.ManifestRelease
import com.example.data.zip.ExperienceZipExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    val updatesDir: File by lazy {
        val dir = File(context.filesDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val extractedDir: File by lazy {
        val dir = File(updatesDir, "extracted")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun getDownloadedFile(release: ManifestRelease): File {
        val safeName = release.versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val isZip = release.downloadUrl.endsWith(".zip", ignoreCase = true) || release.downloadUrl.contains("zip")
        val extension = if (isZip) "zip" else "img"
        return File(updatesDir, "${safeName}.$extension")
    }

    private fun getPartialFile(release: ManifestRelease): File {
        val safeName = release.versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return File(updatesDir, "${safeName}.part")
    }

    fun getVerifiedGsiImage(): File? {
        val files = extractedDir.listFiles { _, name -> name.startsWith("verified_") && name.endsWith(".img") }
        if (!files.isNullOrEmpty()) {
            return files.first()
        }
        val directImages = updatesDir.listFiles { _, name -> name.endsWith(".img") }
        return directImages?.firstOrNull()
    }

    suspend fun startDownload(release: ManifestRelease) {
        withContext(Dispatchers.IO) {
            isPaused.set(false)
            isCancelled.set(false)

            // Requirement 6: Stop installation workflow if no valid image URL or checksum
            if (!release.isPublishReady || release.downloadUrl.isBlank() || release.sha256.isBlank()) {
                _downloadState.value = DownloadState.Error(
                    title = "Installation Workflow Stopped",
                    message = "The manifest contains no valid package URL or image SHA-256 for '${release.versionName}'. Never silently skip checksum verification.",
                    canRetry = false,
                    isNetworkFailure = false
                )
                return@withContext
            }

            val finalFile = getDownloadedFile(release)
            val partFile = getPartialFile(release)

            // Check if verified image already exists in extracted directory
            val existingVerifiedImage = getVerifiedGsiImage()
            if (existingVerifiedImage != null && existingVerifiedImage.exists() && existingVerifiedImage.length() > 0) {
                // If it already matches the hash, report verified directly
                val calc = computeSha256(existingVerifiedImage)
                if (calc.equals(release.sha256.trim(), ignoreCase = true)) {
                    _downloadState.value = DownloadState.Verified(
                        file = existingVerifiedImage,
                        calculatedSha256 = calc,
                        expectedSha256 = release.sha256.trim().lowercase(),
                        matches = true,
                        fileSize = existingVerifiedImage.length(),
                        isExtractedFromZip = true
                    )
                    return@withContext
                }
            }

            _downloadState.value = DownloadState.CheckingStorage

            val existingBytes = if (partFile.exists()) partFile.length() else 0L
            val bytesNeeded = if (release.fileSize > 0) {
                (release.fileSize - existingBytes).coerceAtLeast(0L)
            } else {
                100L * 1024 * 1024
            }
            val storageHeadroom = 50L * 1024 * 1024
            val availableStorage = DeviceInspector.getAvailableStorageBytes(updatesDir)

            if (availableStorage < (bytesNeeded + storageHeadroom)) {
                _downloadState.value = DownloadState.InsufficientStorage(
                    requiredBytes = bytesNeeded + storageHeadroom,
                    availableBytes = availableStorage
                )
                return@withContext
            }

            val requestBuilder = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", "ExperienceUI-OTAUpdater/1.0 (Android)")

            val isResumed = existingBytes > 0
            if (isResumed) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            try {
                _downloadState.value = DownloadState.Downloading(
                    bytesDownloaded = existingBytes,
                    totalBytes = release.fileSize,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    isResumed = isResumed
                )

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    if (isResumed && response.code == 416) {
                        partFile.delete()
                        startDownload(release)
                        return@withContext
                    }
                    _downloadState.value = DownloadState.Error(
                        title = "HTTP Error ${response.code}",
                        message = "Server returned code ${response.code}: ${response.message}",
                        canRetry = true,
                        isNetworkFailure = false
                    )
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    _downloadState.value = DownloadState.Error(
                        title = "Empty Response",
                        message = "The server returned an empty download payload.",
                        canRetry = true,
                        isNetworkFailure = false
                    )
                    return@withContext
                }

                val appendToFile = response.code == 206
                var currentDownloaded = if (appendToFile) existingBytes else 0L
                val totalBytes = if (release.fileSize > 0) release.fileSize else (currentDownloaded + body.contentLength())

                val outputStream = FileOutputStream(partFile, appendToFile)
                val inputStream = body.byteStream()
                val buffer = ByteArray(32 * 1024)

                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = 0L

                outputStream.use { out ->
                    inputStream.use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled.get()) {
                                _downloadState.value = DownloadState.Idle
                                partFile.delete()
                                return@withContext
                            }

                            if (isPaused.get()) {
                                _downloadState.value = DownloadState.Paused(
                                    bytesDownloaded = currentDownloaded,
                                    totalBytes = totalBytes
                                )
                                return@withContext
                            }

                            out.write(buffer, 0, read)
                            currentDownloaded += read
                            bytesSinceLastCalc += read

                            val now = System.currentTimeMillis()
                            val timeDelta = now - lastSpeedCalcTime
                            if (timeDelta >= 500) {
                                currentSpeed = (bytesSinceLastCalc * 1000L) / timeDelta
                                val remainingBytes = (totalBytes - currentDownloaded).coerceAtLeast(0L)
                                val etaSec = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                                _downloadState.value = DownloadState.Downloading(
                                    bytesDownloaded = currentDownloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = currentSpeed,
                                    etaSeconds = etaSec,
                                    isResumed = isResumed
                                )

                                lastSpeedCalcTime = now
                                bytesSinceLastCalc = 0L
                            }
                        }
                        out.flush()
                    }
                }

                // Move downloaded file to final path
                if (finalFile.exists()) finalFile.delete()
                partFile.renameTo(finalFile)

                // Inspect downloaded file: Is it a ZIP archive?
                val isZipArchive = isZipFile(finalFile)

                if (isZipArchive) {
                    _downloadState.value = DownloadState.ExtractingZip(
                        bytesExtracted = 0L,
                        totalBytes = finalFile.length(),
                        percent = 0
                    )

                    val extractionResult = ExperienceZipExtractor.inspectAndExtractGsi(
                        zipFile = finalFile,
                        targetOutputDir = extractedDir,
                        expectedImageSha256 = release.sha256,
                        onProgress = { bytes, total, pct ->
                            _downloadState.value = DownloadState.ExtractingZip(bytes, total, pct)
                        }
                    )

                    if (extractionResult.isSuccess && extractionResult.extractedImageFile != null) {
                        _downloadState.value = DownloadState.Verified(
                            file = extractionResult.extractedImageFile,
                            calculatedSha256 = extractionResult.extractedImageSha256 ?: "",
                            expectedSha256 = release.sha256.trim().lowercase(),
                            matches = extractionResult.isSha256Verified,
                            fileSize = extractionResult.extractedImageFile.length(),
                            isExtractedFromZip = true,
                            zipInspection = extractionResult
                        )
                    } else if (extractionResult.extractedImageSha256 != null && !extractionResult.isSha256Verified) {
                        _downloadState.value = DownloadState.HashMismatch(
                            file = finalFile,
                            calculatedSha256 = extractionResult.extractedImageSha256,
                            expectedSha256 = release.sha256.trim().lowercase()
                        )
                    } else {
                        _downloadState.value = DownloadState.Error(
                            title = "ZIP Inspection Failed",
                            message = extractionResult.errorMessage ?: "Failed to locate and verify GSI image in ZIP.",
                            canRetry = true,
                            isNetworkFailure = false
                        )
                    }
                } else {
                    // Direct file: verify hash
                    verifyFileHash(finalFile, release.sha256)
                }

            } catch (e: UnknownHostException) {
                _downloadState.value = DownloadState.Error(
                    title = "Network Failure",
                    message = "Could not resolve host. Please check your internet connectivity.",
                    canRetry = true,
                    isNetworkFailure = true
                )
            } catch (e: SocketTimeoutException) {
                _downloadState.value = DownloadState.Error(
                    title = "Connection Timed Out",
                    message = "The network connection timed out while transferring data.",
                    canRetry = true,
                    isNetworkFailure = true
                )
            } catch (e: IOException) {
                _downloadState.value = DownloadState.Error(
                    title = "Download Interrupted",
                    message = e.localizedMessage ?: "Transfer was interrupted by a network or I/O error.",
                    canRetry = true,
                    isNetworkFailure = true
                )
            } catch (e: CancellationException) {
                _downloadState.value = DownloadState.Idle
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(
                    title = "Unexpected Error",
                    message = e.localizedMessage ?: "An error occurred during download.",
                    canRetry = true,
                    isNetworkFailure = false
                )
            }
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = raf.readInt()
                magic == 0x504b0304 || magic == 0x504b0506 // PK..
            }
        } catch (_: Exception) {
            file.name.endsWith(".zip", ignoreCase = true)
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.lowercase()
    }

    suspend fun verifyFileHash(file: File, expectedSha256: String) {
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                _downloadState.value = DownloadState.Error(
                    title = "File Missing",
                    message = "Downloaded image file was not found on local storage.",
                    canRetry = true
                )
                return@withContext
            }

            try {
                val totalLength = file.length()
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var bytesReadTotal = 0L

                _downloadState.value = DownloadState.VerifyingHash(0L, totalLength, 0f)

                FileInputStream(file).use { fis ->
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()
                    while (fis.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                        bytesReadTotal += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || bytesReadTotal == totalLength) {
                            val progress = if (totalLength > 0) bytesReadTotal.toFloat() / totalLength else 1f
                            _downloadState.value = DownloadState.VerifyingHash(bytesReadTotal, totalLength, progress)
                            lastUpdate = now
                        }
                    }
                }

                val calculatedBytes = digest.digest()
                val calculatedHex = calculatedBytes.joinToString("") { "%02x".format(it) }.lowercase()
                val normalizedExpected = expectedSha256.trim().lowercase()
                val matches = calculatedHex == normalizedExpected

                if (matches) {
                    _downloadState.value = DownloadState.Verified(
                        file = file,
                        calculatedSha256 = calculatedHex,
                        expectedSha256 = normalizedExpected,
                        matches = true,
                        fileSize = totalLength,
                        isExtractedFromZip = false
                    )
                } else {
                    _downloadState.value = DownloadState.HashMismatch(
                        file = file,
                        calculatedSha256 = calculatedHex,
                        expectedSha256 = normalizedExpected
                    )
                }
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(
                    title = "Checksum Error",
                    message = "Failed to calculate SHA-256: ${e.localizedMessage}",
                    canRetry = true
                )
            }
        }
    }

    fun pauseDownload() {
        isPaused.set(true)
    }

    fun cancelDownload(release: ManifestRelease) {
        isCancelled.set(true)
        val partFile = getPartialFile(release)
        if (partFile.exists()) partFile.delete()
        _downloadState.value = DownloadState.Idle
    }

    fun deleteDownloadedFile(release: ManifestRelease) {
        val finalFile = getDownloadedFile(release)
        val partFile = getPartialFile(release)
        if (finalFile.exists()) finalFile.delete()
        if (partFile.exists()) partFile.delete()

        // Also delete extracted images
        val verifiedImage = getVerifiedGsiImage()
        if (verifiedImage != null && verifiedImage.exists()) {
            verifiedImage.delete()
        }

        _downloadState.value = DownloadState.Idle
    }

    fun checkExistingFile(release: ManifestRelease) {
        val verifiedImage = getVerifiedGsiImage()
        if (verifiedImage != null && verifiedImage.exists() && verifiedImage.length() > 0) {
            _downloadState.value = DownloadState.Verified(
                file = verifiedImage,
                calculatedSha256 = "Ready to verify",
                expectedSha256 = release.sha256,
                matches = false,
                fileSize = verifiedImage.length(),
                isExtractedFromZip = true
            )
            return
        }

        val finalFile = getDownloadedFile(release)
        if (finalFile.exists() && finalFile.length() > 0) {
            _downloadState.value = DownloadState.Verified(
                file = finalFile,
                calculatedSha256 = "Ready to verify",
                expectedSha256 = release.sha256,
                matches = false,
                fileSize = finalFile.length()
            )
        } else {
            val partFile = getPartialFile(release)
            if (partFile.exists() && partFile.length() > 0) {
                _downloadState.value = DownloadState.Paused(
                    bytesDownloaded = partFile.length(),
                    totalBytes = release.fileSize
                )
            } else {
                _downloadState.value = DownloadState.Idle
            }
        }
    }
}
