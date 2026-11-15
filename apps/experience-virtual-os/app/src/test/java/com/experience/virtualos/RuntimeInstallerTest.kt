package com.experience.virtualos

import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.runtime.Downloader
import com.experience.virtualos.core.runtime.HostConstraints
import com.experience.virtualos.core.runtime.InstallStage
import com.experience.virtualos.core.runtime.ManifestException
import com.experience.virtualos.core.runtime.ManifestValidator
import com.experience.virtualos.core.runtime.RuntimeInstallException
import com.experience.virtualos.core.runtime.RuntimeInstaller
import com.experience.virtualos.core.runtime.RuntimeLayout
import com.experience.virtualos.core.runtime.RuntimeManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RuntimeInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val host = HostConstraints(listOf("arm64-v8a", "armeabi-v7a"), 28, 6)

    private class FakeDownloader(var files: Map<String, ByteArray>, var manifestText: String? = null) : Downloader {
        var downloads = 0
        var failAfterBytes: Int? = null
        override fun fetchText(url: String, maxBytes: Int) = manifestText ?: throw IOException("Not found (HTTP 404): $url")
        override fun download(url: String, destination: File, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean) {
            downloads++
            val data = files[url] ?: throw IOException("HTTP 404")
            val start = if (destination.isFile) destination.length().toInt() else 0
            val end = failAfterBytes?.let { minOf(it, data.size) } ?: data.size
            destination.parentFile?.mkdirs()
            java.io.FileOutputStream(destination, start > 0).use { it.write(data, start, maxOf(0, end - start)) }
            onProgress(end.toLong(), data.size.toLong())
            if (end < data.size) throw IOException("connection reset")
        }
    }

    private fun runtimeArchive(version: String, includeQemu: Boolean = true): ByteArray {
        val tar = TestTar()
            .file("runtime-info.json", """{"qemu":"test-$version"}""")
            .dir("rootfs/").dir("rootfs/bin/").dir("rootfs/usr/").dir("rootfs/usr/bin/")
            .file("rootfs/bin/busybox", "bb", 0b111_101_101)
            .symlink("rootfs/bin/sh", "/bin/busybox")
            .file("rootfs/usr/bin/qemu-img", "img", 0b111_101_101)
        if (includeQemu) tar.file("rootfs/usr/bin/qemu-system-aarch64", "qemu $version", 0b111_101_101)
        val f = tmp.newFile()
        tar.writeGzip(f)
        return f.readBytes()
    }

    private fun manifest(version: String, bytes: ByteArray, sha: String = FileUtils.sha256(bytes.inputStream())) = RuntimeManifest(
        schemaVersion = 2,
        kind = RuntimeManifest.KIND,
        runtimeVersion = version,
        releaseTag = "runtime-v$version",
        abi = "arm64-v8a",
        minHostApi = 28,
        minAppVersionCode = 6,
        archive = RuntimeManifest.Archive("base.tar.gz", "https://github.com/o/r/releases/download/runtime-v$version/base.tar.gz", "tar+gzip", bytes.size.toLong(), sha, 10_000),
        entrypoints = mapOf("shell" to "rootfs/bin/sh", "qemuSystemAarch64" to "rootfs/usr/bin/qemu-system-aarch64", "qemuImg" to "rootfs/usr/bin/qemu-img"),
        requiredPaths = listOf("runtime-info.json", "rootfs/bin/sh", "rootfs/usr/bin/qemu-system-aarch64", "rootfs/usr/bin/qemu-img"),
    )

    private fun installer(dl: Downloader, layout: RuntimeLayout = RuntimeLayout(tmp.newFolder("rt")), free: Long = Long.MAX_VALUE, smoke: (File, RuntimeManifest) -> Unit = { _, _ -> }) =
        RuntimeInstaller(layout, dl, host, freeSpace = { free }, smokeTest = smoke)

    @Test fun installsVerifiesAndActivates() {
        val bytes = runtimeArchive("1.0.0")
        val m = manifest("1.0.0", bytes)
        val dl = FakeDownloader(mapOf(m.archive.url to bytes))
        val inst = installer(dl)
        val stages = mutableListOf<InstallStage>()
        val result = inst.install(m, { stages += it })
        assertEquals("1.0.0", result.runtimeVersion)
        assertEquals("1.0.0", inst.current()?.runtimeVersion)
        val dir = inst.currentDirectory()!!
        assertTrue(File(dir, "rootfs/usr/bin/qemu-system-aarch64").canExecute())
        assertTrue(inst.verify().isEmpty())
        assertTrue(stages.any { it is InstallStage.Extracting })
        assertTrue(stages.last() is InstallStage.Activating)
        assertFalse("archive is removed after install", File(tmp.root, "rt/downloads/${m.archive.sha256}.tar.gz").exists())
    }

    @Test fun rejectsChecksumMismatchAndKeepsWorkingRuntime() {
        val good = runtimeArchive("1.0.0")
        val mGood = manifest("1.0.0", good)
        val layout = RuntimeLayout(tmp.newFolder("rt"))
        val dl = FakeDownloader(mapOf(mGood.archive.url to good))
        installer(dl, layout).install(mGood, {})

        val newer = runtimeArchive("1.1.0")
        val corrupted = newer.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val mNew = manifest("1.1.0", newer)
        dl.files = mapOf(mNew.archive.url to corrupted)
        val inst = installer(dl, layout)
        try {
            inst.install(mNew, {})
            fail("expected checksum failure")
        } catch (e: RuntimeInstallException) {
            assertTrue(e.message!!.contains("SHA-256"))
        }
        assertEquals("1.0.0", inst.current()?.runtimeVersion)
        assertTrue(inst.verify().isEmpty())
        assertFalse(File(layout.downloads, "${mNew.archive.sha256}.part").exists())
    }

    @Test fun rejectsIncompleteArchiveWithoutActivating() {
        val bytes = runtimeArchive("1.0.0", includeQemu = false)
        val m = manifest("1.0.0", bytes)
        val layout = RuntimeLayout(tmp.newFolder("rt"))
        val inst = installer(FakeDownloader(mapOf(m.archive.url to bytes)), layout)
        try {
            inst.install(m, {})
            fail("expected failure")
        } catch (e: RuntimeInstallException) {
            assertTrue(e.message!!.contains("qemu-system-aarch64"))
        }
        assertNull(inst.current())
        assertTrue(layout.staging.listFiles().isNullOrEmpty())
    }

    @Test fun failedSmokeTestDoesNotActivate() {
        val bytes = runtimeArchive("1.0.0")
        val m = manifest("1.0.0", bytes)
        val inst = installer(FakeDownloader(mapOf(m.archive.url to bytes))) { _, _ -> throw IOException("loader crashed") }
        try {
            inst.install(m, {})
            fail("expected failure")
        } catch (e: RuntimeInstallException) {
            assertTrue(e.message!!.contains("loader crashed"))
        }
        assertNull(inst.current())
    }

    @Test fun resumesInterruptedDownload() {
        val bytes = runtimeArchive("1.0.0")
        val m = manifest("1.0.0", bytes)
        val dl = FakeDownloader(mapOf(m.archive.url to bytes)).apply { failAfterBytes = bytes.size / 2 }
        val layout = RuntimeLayout(tmp.newFolder("rt"))
        val inst = installer(dl, layout)
        try { inst.install(m, {}); fail("expected interruption") } catch (e: RuntimeInstallException) {
            assertTrue(e.message!!.contains("resume"))
        }
        assertEquals((bytes.size / 2).toLong(), File(layout.downloads, "${m.archive.sha256}.part").length())
        dl.failAfterBytes = null
        inst.install(m, {})
        assertEquals("1.0.0", inst.current()?.runtimeVersion)
    }

    @Test fun updateReplacesOldVersion() {
        val layout = RuntimeLayout(tmp.newFolder("rt"))
        val a = runtimeArchive("1.0.0"); val ma = manifest("1.0.0", a)
        val b = runtimeArchive("1.1.0"); val mb = manifest("1.1.0", b)
        val dl = FakeDownloader(mapOf(ma.archive.url to a, mb.archive.url to b))
        val inst = installer(dl, layout)
        inst.install(ma, {})
        assertTrue(inst.isUpdate(mb))
        assertFalse(inst.isUpdate(ma))
        inst.install(mb, {})
        assertEquals("1.1.0", inst.current()?.runtimeVersion)
        assertFalse(layout.versionDir("1.0.0").exists())
        assertEquals("qemu 1.1.0", File(layout.versionDir("1.1.0"), "rootfs/usr/bin/qemu-system-aarch64").readText())
    }

    @Test fun repairReinstallsSameVersion() {
        val layout = RuntimeLayout(tmp.newFolder("rt"))
        val a = runtimeArchive("1.0.0"); val ma = manifest("1.0.0", a)
        val inst = installer(FakeDownloader(mapOf(ma.archive.url to a)), layout)
        inst.install(ma, {})
        File(layout.versionDir("1.0.0"), "rootfs/usr/bin/qemu-system-aarch64").delete()
        assertFalse(inst.verify().isEmpty())
        assertTrue(inst.isUpdate(ma))
        inst.install(ma, {})
        assertTrue(inst.verify().isEmpty())
    }

    @Test fun refusesWhenStorageIsShort() {
        val bytes = runtimeArchive("1.0.0")
        val m = manifest("1.0.0", bytes)
        val dl = FakeDownloader(mapOf(m.archive.url to bytes))
        try {
            installer(dl, free = 100).install(m, {})
            fail("expected storage failure")
        } catch (e: RuntimeInstallException) {
            assertTrue(e.message!!.contains("Not enough free storage"))
        }
        assertEquals(0, dl.downloads)
    }

    @Test fun missingReleaseIsReportedHonestly() {
        val inst = installer(FakeDownloader(emptyMap(), manifestText = null))
        try {
            inst.fetchManifest("https://github.com/o/r/releases/latest/download/manifest.json")
            fail("expected failure")
        } catch (e: RuntimeInstallException) {
            assertTrue(e.message!!.contains("No runtime release has been published"))
        }
    }

    @Test fun installsFromLocalFile() {
        val bytes = runtimeArchive("2.0.0")
        val m = manifest("2.0.0", bytes)
        val file = tmp.newFile("base.tar.gz").apply { writeBytes(bytes) }
        val inst = installer(FakeDownloader(emptyMap()))
        inst.installFromFile(file, m) {}
        assertEquals("local-file", inst.current()?.source)
        inst.uninstall()
        assertNull(inst.current())
    }

    @Test fun parsesGeneratedManifestJson() {
        val text = """
            {"schemaVersion":2,"kind":"experience-virtual-os-runtime","runtimeVersion":"0.6.0","releaseTag":"runtime-v0.6.0",
             "abi":"arm64-v8a","minHostApi":28,"minAppVersionCode":6,
             "archive":{"fileName":"base.tar.gz","url":"https://github.com/o/r/releases/download/runtime-v0.6.0/base.tar.gz",
               "format":"tar+gzip","sizeBytes":33392063,"sha256":"${"a".repeat(64)}","unpackedSizeBytes":239349604,"entries":1005},
             "components":{"qemu":"11.0.3-r0"},
             "entrypoints":{"shell":"rootfs/bin/sh","qemuSystemAarch64":"rootfs/usr/bin/qemu-system-aarch64","qemuImg":"rootfs/usr/bin/qemu-img"},
             "requiredPaths":["rootfs/bin/sh"],"capabilities":{"androidGuest":"experimental"},"notes":"x","futureField":1}
        """.trimIndent()
        val m = ManifestValidator.validate(RuntimeManifest.parse(text), host)
        assertEquals("0.6.0", m.runtimeVersion)
        assertNotNull(m.components["qemu"])
    }

    @Test fun validatorRejectsBadManifests() {
        val good = manifest("1.0.0", byteArrayOf(1, 2, 3))
        val cases = mapOf(
            "schema" to good.copy(schemaVersion = 1),
            "abi" to good.copy(abi = "x86_64"),
            "api" to good.copy(minHostApi = 34),
            "app" to good.copy(minAppVersionCode = 99),
            "http" to good.copy(archive = good.archive.copy(url = "http://github.com/x")),
            "host" to good.copy(archive = good.archive.copy(url = "https://evil.example/base.tar.gz")),
            "sha" to good.copy(archive = good.archive.copy(sha256 = "PLACEHOLDER")),
            "path" to good.copy(requiredPaths = listOf("../etc/passwd")),
            "version" to good.copy(runtimeVersion = "latest"),
        )
        for ((name, m) in cases) {
            try {
                ManifestValidator.validate(m, host)
                fail("$name should be rejected")
            } catch (_: ManifestException) {
            }
        }
    }
}
