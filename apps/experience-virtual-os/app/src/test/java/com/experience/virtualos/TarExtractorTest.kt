package com.experience.virtualos

import com.experience.virtualos.core.TarExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class TarExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun extract(tar: TestTar, dest: File = tmp.newFolder()) =
        TarExtractor().extract(ByteArrayInputStream(tar.bytes()), dest) to dest

    @Test fun extractsFilesDirectoriesLinksAndModes() {
        val tar = TestTar()
            .dir("rootfs/")
            .dir("rootfs/bin/")
            .file("rootfs/bin/busybox", "#!busybox", 0b111_101_101)
            .symlink("rootfs/bin/sh", "/bin/busybox")
            .file("rootfs/etc-motd", "hello")
            .hardlink("rootfs/motd-copy", "rootfs/etc-motd")
            .device("rootfs/dev-null")
        val (result, dest) = extract(tar)
        assertEquals(2, result.files)
        assertEquals(1, result.symlinks)
        assertEquals(1, result.hardlinks)
        assertEquals(1, result.skipped)
        val busybox = File(dest, "rootfs/bin/busybox")
        assertTrue(busybox.canExecute())
        val sh = File(dest, "rootfs/bin/sh").toPath()
        assertTrue(Files.isSymbolicLink(sh))
        assertEquals("/bin/busybox", Files.readSymbolicLink(sh).toString())
        assertEquals("hello", File(dest, "rootfs/motd-copy").readText())
        assertFalse(File(dest, "rootfs/dev-null").exists())
    }

    @Test(expected = TarExtractor.UnsafeArchiveException::class)
    fun rejectsParentTraversal() { extract(TestTar().file("../evil", "x")) }

    @Test(expected = TarExtractor.UnsafeArchiveException::class)
    fun rejectsAbsolutePaths() { extract(TestTar().file("/etc/passwd", "x")) }

    @Test(expected = TarExtractor.UnsafeArchiveException::class)
    fun rejectsTraversalHiddenInMiddle() { extract(TestTar().file("a/../../evil", "x")) }

    @Test fun refusesToWriteThroughSymlink() {
        val outside = tmp.newFolder("outside")
        val tar = TestTar().symlink("escape", outside.absolutePath).file("escape/pwned", "x")
        try {
            extract(tar)
            throw AssertionError("expected UnsafeArchiveException")
        } catch (_: TarExtractor.UnsafeArchiveException) {
        }
        assertFalse(File(outside, "pwned").exists())
    }

    @Test(expected = TarExtractor.UnsafeArchiveException::class)
    fun rejectsHardlinkToOutside() { extract(TestTar().hardlink("x", "../../etc/passwd")) }

    @Test fun supportsGnuLongNamesAndPaxPaths() {
        val long = "rootfs/" + "d".repeat(120) + "/file.txt"
        val (_, dest) = extract(TestTar().gnuLongName(long, "long").paxPath("rootfs/pax/é-name.txt", "pax"))
        assertEquals("long", File(dest, long).readText())
        assertEquals("pax", File(dest, "rootfs/pax/é-name.txt").readText())
    }

    @Test(expected = java.io.IOException::class)
    fun detectsCorruptHeaders() {
        val bytes = TestTar().file("a", "b").bytes()
        bytes[0] = 'z'.code.toByte() // breaks the checksum
        TarExtractor().extract(ByteArrayInputStream(bytes), tmp.newFolder())
    }

    @Test(expected = java.io.EOFException::class)
    fun detectsTruncatedArchive() {
        val bytes = TestTar().file("a", "x".repeat(2000)).bytes().copyOf(900)
        TarExtractor().extract(ByteArrayInputStream(bytes), tmp.newFolder())
    }

    /**
     * Extracts a real runtime archive when one is provided, e.g.
     * EVOS_RUNTIME_ARCHIVE=/path/base.tar.gz ./gradlew test
     */
    @Test fun extractsRealRuntimeArchive() {
        val path = System.getenv("EVOS_RUNTIME_ARCHIVE")
        assumeTrue("EVOS_RUNTIME_ARCHIVE not set", !path.isNullOrBlank() && File(path).isFile)
        val dest = tmp.newFolder("runtime")
        val result = TarExtractor().extractGzip(File(path!!), dest)
        assertTrue(result.files > 100)
        assertTrue(Files.isSymbolicLink(File(dest, "rootfs/bin/sh").toPath()))
        assertTrue(File(dest, "rootfs/usr/bin/qemu-system-aarch64").canExecute())
        assertTrue(File(dest, "rootfs/lib/ld-musl-aarch64.so.1").exists())
        assertTrue(File(dest, "runtime-info.json").readText().contains("qemu"))
    }
}
