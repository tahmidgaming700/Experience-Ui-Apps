package com.example.data.zip

import com.example.data.model.ZipInspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ExperienceZipExtractor {

    suspend fun inspectAndExtractGsi(
        zipFile: File,
        targetOutputDir: File,
        expectedImageSha256: String,
        onProgress: (bytesExtracted: Long, totalEstimatedBytes: Long, percent: Int) -> Unit = { _, _, _ -> }
    ): ZipInspectionResult = withContext(Dispatchers.IO) {
        if (!zipFile.exists() || zipFile.length() == 0L) {
            return@withContext ZipInspectionResult(
                isSuccess = false,
                zipFile = zipFile,
                errorMessage = "Downloaded release ZIP is missing or empty."
            )
        }

        if (!targetOutputDir.exists()) {
            targetOutputDir.mkdirs()
        }

        var imageEntry: ZipEntry? = null
        var apkCount = 0
        var hasBootAnimation = false

        try {
            ZipFile(zipFile).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.replace('\\', '/')

                    // Look for GSI image inside images/ or image/ directory
                    if ((name.startsWith("images/", ignoreCase = true) || name.startsWith("image/", ignoreCase = true))
                        && name.endsWith(".img", ignoreCase = true) && !entry.isDirectory
                    ) {
                        imageEntry = entry
                    }

                    if (name.startsWith("apks/", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)) {
                        apkCount++
                    }

                    if (name.contains("bootanimation.zip", ignoreCase = true)) {
                        hasBootAnimation = true
                    }
                }

                val targetEntry = imageEntry
                if (targetEntry == null) {
                    return@withContext ZipInspectionResult(
                        isSuccess = false,
                        zipFile = zipFile,
                        apkCount = apkCount,
                        hasBootAnimation = hasBootAnimation,
                        errorMessage = "No GSI image file (.img) found under 'images/' or 'image/' in release ZIP archive."
                    )
                }

                val imageFileName = File(targetEntry.name).name
                val tempOutputFile = File(targetOutputDir, "temp_${System.currentTimeMillis()}_$imageFileName")
                val finalVerifiedFile = File(targetOutputDir, "verified_$imageFileName")

                val entrySize = if (targetEntry.size > 0) targetEntry.size else 100L * 1024 * 1024
                var extractedBytes = 0L

                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)

                zf.getInputStream(targetEntry).use { input ->
                    FileOutputStream(tempOutputFile).use { output ->
                        var read: Int
                        var lastReportTime = System.currentTimeMillis()
                        while (input.read(buffer).also { read = it } != -1) {
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            extractedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime >= 150) {
                                val percent = if (entrySize > 0) {
                                    ((extractedBytes * 100L) / entrySize).coerceIn(0, 100).toInt()
                                } else 0
                                onProgress(extractedBytes, entrySize, percent)
                                lastReportTime = now
                            }
                        }
                        output.flush()
                    }
                }

                // Compute extracted image SHA-256
                val calculatedHex = digest.digest().joinToString("") { "%02x".format(it) }.lowercase()
                val normalizedExpected = expectedImageSha256.trim().lowercase()

                if (normalizedExpected.isNotBlank() && calculatedHex != normalizedExpected) {
                    // Checksum mismatch on extracted GSI: delete the unverified temp file, preserve existing verified image!
                    tempOutputFile.delete()
                    return@withContext ZipInspectionResult(
                        isSuccess = false,
                        zipFile = zipFile,
                        imageEntryPath = targetEntry.name,
                        extractedImageSha256 = calculatedHex,
                        expectedSha256 = normalizedExpected,
                        isSha256Verified = false,
                        apkCount = apkCount,
                        hasBootAnimation = hasBootAnimation,
                        errorMessage = "Extracted GSI image SHA-256 mismatch!\nExpected: $normalizedExpected\nCalculated: $calculatedHex\nThe corrupted file was deleted. Do NOT flash."
                    )
                }

                // Checksum matches or initial verification succeeds: atomically promote to final verified file
                if (finalVerifiedFile.exists()) finalVerifiedFile.delete()
                tempOutputFile.renameTo(finalVerifiedFile)

                onProgress(extractedBytes, entrySize, 100)

                return@withContext ZipInspectionResult(
                    isSuccess = true,
                    zipFile = zipFile,
                    imageEntryPath = targetEntry.name,
                    extractedImageFile = finalVerifiedFile,
                    extractedImageSha256 = calculatedHex,
                    expectedSha256 = normalizedExpected,
                    isSha256Verified = normalizedExpected.isNotBlank() && calculatedHex == normalizedExpected,
                    apkCount = apkCount,
                    hasBootAnimation = hasBootAnimation,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            return@withContext ZipInspectionResult(
                isSuccess = false,
                zipFile = zipFile,
                errorMessage = "ZIP extraction failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}
