package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.device.DeviceInspector
import com.example.data.flasher.DDFlasher
import com.example.data.manifest.ExperienceManifestParser
import com.example.data.model.DeviceInfo
import com.example.data.model.ManifestRelease
import com.example.data.zip.ExperienceZipExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private val REAL_GITHUB_MANIFEST_JSON = """
    {
      "schema_version": 1,
      "project": "Experience UI",
      "stable": {
        "version": "No stable OTA published",
        "security_patch": "Not published",
        "release_notes": "Monthly security release channel is prepared, but no signed compatible package has been published yet.",
        "package_url": "",
        "sha256": ""
      },
      "early_access": {
        "version": "Experience UI 2 — Android 13 — not released",
        "security_patch": "Not published",
        "release_notes": "Early-access placeholder. Do not install until a device-compatible build, signing, and recovery instructions are published.",
        "package_url": "",
        "sha256": ""
      }
    }
    """.trimIndent()

    @Test
    fun `parse real Experience UI GitHub manifest`() {
        val result = ExperienceManifestParser.parse(REAL_GITHUB_MANIFEST_JSON)
        assertTrue(result.isValidJson)
        assertNotNull(result.manifest)

        val manifest = result.manifest!!
        assertEquals(1, manifest.schemaVersion)
        assertEquals("Experience UI", manifest.project)

        val stable = manifest.stable
        assertEquals("No stable OTA published", stable.version)
        assertEquals("Not published", stable.securityPatch)
        assertEquals("", stable.packageUrl)
        assertEquals("", stable.sha256)
        assertFalse("Unpublished channel must not be publish-ready", stable.isPublishReady)

        val earlyAccess = manifest.earlyAccess
        assertTrue(earlyAccess.version.contains("Experience UI 2"))
        assertFalse(earlyAccess.isPublishReady)
    }

    @Test
    fun `parse published Experience UI manifest with full metadata`() {
        val publishedJson = """
        {
          "schema_version": 1,
          "project": "Experience UI",
          "stable": {
            "version": "Experience UI v1.0 — Android 12 ARM64 GSI",
            "security_patch": "2026-10-05",
            "release_notes": "Official release.\nIncludes Experience apps and boot animation.",
            "package_url": "https://github.com/tahmidgaming700/Experience-UI/releases/download/v1.0/ExperienceUI-Android12-arm64.zip",
            "sha256": "58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd",
            "android_base": "12.0",
            "architecture": "arm64-v8a",
            "target_partition": "system",
            "download_size": 1500000000
          }
        }
        """.trimIndent()

        val result = ExperienceManifestParser.parse(publishedJson)
        assertTrue(result.isValidJson)
        val channel = result.manifest!!.stable
        assertTrue(channel.isPublishReady)
        assertTrue(channel.isPackageUrlValid)
        assertTrue(channel.isSha256Valid)
        assertEquals("12.0", channel.androidBase)
        assertEquals("arm64-v8a", channel.architecture)
        assertEquals("system", channel.targetPartition)
        assertEquals(1500000000L, channel.downloadSize)
    }

    @Test
    fun `manifest parser rejects malformed JSON safely`() {
        val badJson = "{ invalid json content ... "
        val result = ExperienceManifestParser.parse(badJson)
        assertFalse(result.isValidJson)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `manifest parser flags insecure HTTP URLs`() {
        val insecureJson = """
        {
          "schema_version": 1,
          "project": "Experience UI",
          "stable": {
            "version": "Insecure Release",
            "security_patch": "2026-10-05",
            "release_notes": "Test notes",
            "package_url": "http://insecure-cdn.com/release.zip",
            "sha256": "58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd"
          }
        }
        """.trimIndent()

        val result = ExperienceManifestParser.parse(insecureJson)
        assertFalse("HTTP URL must not be publish-ready", result.manifest!!.stable.isPublishReady)
        assertTrue(result.warnings.any { it.contains("Insecure URL") })
    }

    @Test
    fun `zip inspection extracts GSI from images directory and verifies hash`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val tempDir = File(context.cacheDir, "test_zip_${System.currentTimeMillis()}").apply { mkdirs() }
            val zipFile = File(tempDir, "test_release.zip")
            val outputDir = File(tempDir, "output").apply { mkdirs() }

            // Create sample GSI payload
            val gsiContent = "Sample Experience UI Android 12 GSI Image Block Content".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
            val expectedSha256 = digest.digest(gsiContent).joinToString("") { "%02x".format(it) }

            // Construct a ZIP containing images/system.img, apks/launcher.apk, bootanimation/bootanimation.zip
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // GSI image entry under images/
                zos.putNextEntry(ZipEntry("images/ExperienceUI-Android12-arm64-experimental.img"))
                zos.write(gsiContent)
                zos.closeEntry()

                // Companion APK entry
                zos.putNextEntry(ZipEntry("apks/Experience-launcher.apk"))
                zos.write("APK_DUMMY_BYTES".toByteArray())
                zos.closeEntry()

                // Boot animation entry
                zos.putNextEntry(ZipEntry("bootanimation/bootanimation.zip"))
                zos.write("BOOT_ANIMATION_BYTES".toByteArray())
                zos.closeEntry()
            }

            // Test extraction and verification
            val result = ExperienceZipExtractor.inspectAndExtractGsi(
                zipFile = zipFile,
                targetOutputDir = outputDir,
                expectedImageSha256 = expectedSha256
            )

            assertTrue("ZIP extraction must succeed", result.isSuccess)
            assertTrue("Extracted GSI SHA-256 must match expected image hash", result.isSha256Verified)
            assertEquals(expectedSha256, result.extractedImageSha256)
            assertEquals(1, result.apkCount)
            assertTrue(result.hasBootAnimation)
            assertNotNull(result.extractedImageFile)
            assertTrue(result.extractedImageFile!!.exists())

            // Cleanup
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `zip inspection rejects corrupted GSI image checksum and deletes bad file`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val tempDir = File(context.cacheDir, "test_corrupt_${System.currentTimeMillis()}").apply { mkdirs() }
            val zipFile = File(tempDir, "corrupt_release.zip")
            val outputDir = File(tempDir, "output").apply { mkdirs() }

            val gsiContent = "Original Clean Content".toByteArray()
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("images/system.img"))
                zos.write(gsiContent)
                zos.closeEntry()
            }

            // Pass an intentionally wrong expected SHA-256
            val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
            val result = ExperienceZipExtractor.inspectAndExtractGsi(
                zipFile = zipFile,
                targetOutputDir = outputDir,
                expectedImageSha256 = wrongHash
            )

            assertFalse("Extraction verification must fail on hash mismatch", result.isSuccess)
            assertFalse(result.isSha256Verified)
            assertTrue(result.errorMessage?.contains("mismatch") == true)

            // Verify corrupted file was not saved to output
            val files = outputDir.listFiles()
            assertTrue("No unverified image file should remain in output directory", files.isNullOrEmpty())

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `device compatibility checks detect unsupported architectures`() {
        val x86Device = DeviceInfo(
            model = "x86 Tablet",
            manufacturer = "Generic",
            deviceCode = "x86_device",
            androidVersion = "12.0",
            sdkInt = 31,
            securityPatch = "2026-10-05",
            supportedAbis = listOf("x86"),
            primaryAbi = "x86",
            isTrebleEnabled = true,
            hasDynamicPartitions = true,
            vndkVersion = "31",
            freeInternalBytes = 50L * 1024 * 1024 * 1024,
            totalInternalBytes = 128L * 1024 * 1024 * 1024
        )

        val arm64Release = ManifestRelease(
            versionName = "Experience UI ARM64 GSI",
            versionCode = 1,
            androidVersion = "12.0",
            securityPatch = "2026-10-05",
            releaseDate = "2026-10-25",
            minSdk = 31,
            supportedAbis = listOf("arm64-v8a"),
            requiresTreble = true,
            variant = "arm64",
            downloadUrl = "https://github.com/tahmidgaming700/Experience-UI/releases/download/v1.0/release.zip",
            fileSize = 1000,
            sha256 = "58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd",
            changelog = "Notes",
            warnings = emptyList()
        )

        val compatibility = DeviceInspector.checkCompatibility(x86Device, arm64Release)
        assertFalse(compatibility.isCompatible)
        assertFalse(compatibility.abiSupported)
        assertTrue(compatibility.issues.any { it.contains("Incompatible CPU Architecture") })
    }

    @Test
    fun `dd flasher environment inspects target partition and reports permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = DDFlasher.inspectEnvironment(context, File("/sdcard/system.img"))

        assertNotNull(report.targetPartition)
        assertTrue(report.targetPartition.contains("system"))
        assertNotNull(report.exactDdCommand)
        assertTrue(report.exactDdCommand.contains("dd if="))
        assertTrue(report.exactDdCommand.contains("bs=4M"))
        assertTrue(report.fastbootCommand.contains("fastboot flash system"))
    }

    @Test
    fun `dd flasher scans partitions and detects candidates`() {
        val partitions = DDFlasher.scanAvailablePartitions()
        assertTrue("Must detect partition candidates", partitions.isNotEmpty())
        assertTrue("Must detect at least one system partition", partitions.any { it.isSystemPartition })
    }

    @Test
    fun `dd flasher execution without root safely aborts with informative logs`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dummyImg = File(context.cacheDir, "test_system.img").apply {
                writeText("TEST_IMAGE_BYTES")
            }

            var logged = false
            val result = DDFlasher.executeDdFlash(
                imageFile = dummyImg,
                targetPartition = "/dev/block/by-name/system",
                blockSize = "4M",
                onLog = { logged = true }
            )

            // In local test environment without real partition, execution must report failed status with logs
            assertTrue(result is com.example.data.model.DDFlasherStatus.Failed)
            val failed = result as com.example.data.model.DDFlasherStatus.Failed
            assertTrue(failed.error.isNotBlank())
            assertTrue(failed.reason.isNotBlank())
            assertTrue(failed.manualDdCommand.contains("bs=4M"))
            assertTrue(logged)
            assertTrue(failed.executionLogs.isNotEmpty())

            dummyImg.delete()
        }
    }
}
