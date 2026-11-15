package com.experience.files.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(parent: File, name: String, content: String = name): File =
        File(parent, name).apply { parentFile?.mkdirs(); writeText(content) }

    private fun tree(root: File): Map<String, String> =
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readText() }

    @Test fun uniqueNameAddsCounterBeforeExtension() {
        val d = tmp.newFolder("d")
        assertEquals("a.txt", FileOps.uniqueName(d, "a.txt"))
        file(d, "a.txt")
        assertEquals("a (1).txt", FileOps.uniqueName(d, "a.txt"))
        file(d, "a (1).txt")
        assertEquals("a (2).txt", FileOps.uniqueName(d, "a.txt"))
        File(d, "folder.v2").mkdir()
        assertEquals("folder.v2 (1)", FileOps.uniqueName(d, "folder.v2")) // folders keep the dot
        file(d, ".nomedia")
        assertEquals(".nomedia (1)", FileOps.uniqueName(d, ".nomedia")) // dot-files have no extension
        file(d, "archive.tar.gz")
        assertEquals("archive.tar (1).gz", FileOps.uniqueName(d, "archive.tar.gz"))
    }

    @Test fun validateName() {
        assertNull(FileOps.validateName("Report 2024.pdf"))
        assertNotNull(FileOps.validateName(""))
        assertNotNull(FileOps.validateName("  "))
        assertNotNull(FileOps.validateName("a/b"))
        assertNotNull(FileOps.validateName(".."))
        assertNotNull(FileOps.validateName("x".repeat(256)))
    }

    @Test fun createAndRename() {
        val d = tmp.newFolder("d")
        val f = FileOps.createFolder(d, "New")
        assertTrue(f.isDirectory)
        try { FileOps.createFolder(d, "New"); fail() } catch (e: FileOpException) { assertTrue(e.message!!.contains("exists")) }
        file(d, "x.txt")
        try { FileOps.rename(File(d, "x.txt"), "New"); fail() } catch (_: FileOpException) {}
        val r = FileOps.rename(File(d, "x.txt"), "y.txt")
        assertEquals("y.txt", r.name)
        assertTrue(r.exists()); assertFalse(File(d, "x.txt").exists())
    }

    @Test fun recursiveCopyPreservesTreeAndRenamesOnConflict() = runBlocking {
        val src = tmp.newFolder("src")
        file(src, "a.txt", "A"); file(src, "sub/b.txt", "B"); file(src, "sub/deep/c.bin", "C".repeat(200_000))
        val dest = tmp.newFolder("dest")
        var last = 0L
        val out = FileOps.copy(listOf(src), dest) { done, _ -> assertTrue(done >= last); last = done }
        assertEquals(listOf(File(dest, "src")), out)
        assertEquals(tree(src), tree(File(dest, "src")))
        assertEquals(FileOps.sizeOf(src), last)
        // Copying again produces "src (1)"; the original is untouched.
        val again = FileOps.copy(listOf(src), dest)
        assertEquals("src (1)", again.single().name)
        assertEquals(tree(src), tree(again.single()))
        // Copying a file into its own folder duplicates it.
        val dup = FileOps.copy(listOf(File(src, "a.txt")), src)
        assertEquals("a (1).txt", dup.single().name)
    }

    @Test fun cannotCopyOrMoveFolderIntoItself() = runBlocking {
        val src = tmp.newFolder("src"); file(src, "sub/x.txt")
        try { FileOps.copy(listOf(src), File(src, "sub")); fail() } catch (e: FileOpException) { assertTrue(e.message!!.contains("itself")) }
        try { FileOps.move(listOf(src), File(src, "sub")); fail() } catch (e: FileOpException) { assertTrue(e.message!!.contains("itself")) }
        assertTrue(File(src, "sub/x.txt").exists())
    }

    @Test fun moveRelocatesAndHandlesConflicts() = runBlocking {
        val a = tmp.newFolder("a"); val b = tmp.newFolder("b")
        file(a, "doc.txt", "new"); file(b, "doc.txt", "old"); file(a, "dir/inner.txt", "i")
        val moved = FileOps.move(listOf(File(a, "doc.txt"), File(a, "dir")), b)
        assertEquals(listOf("doc (1).txt", "dir"), moved.map { it.name })
        assertEquals("old", File(b, "doc.txt").readText())
        assertEquals("new", File(b, "doc (1).txt").readText())
        assertEquals("i", File(b, "dir/inner.txt").readText())
        assertEquals(0, a.list()!!.size)
        // Moving into the folder it is already in is a no-op.
        val same = FileOps.move(listOf(File(b, "doc.txt")), b)
        assertEquals(File(b, "doc.txt"), same.single())
        assertEquals("old", File(b, "doc.txt").readText())
    }

    @Test fun recursiveDelete() = runBlocking {
        val d = tmp.newFolder("d"); file(d, "x/y/z.txt"); file(d, "x/w.txt"); file(d, "keep.txt")
        FileOps.delete(listOf(File(d, "x")))
        assertFalse(File(d, "x").exists())
        assertTrue(File(d, "keep.txt").exists())
    }

    @Test fun sizeAndCounts() {
        val d = tmp.newFolder("d"); file(d, "a", "12345"); file(d, "s/b", "123"); File(d, "empty").mkdir()
        assertEquals(8L, FileOps.sizeOf(d))
        assertEquals(2 to 2, FileOps.countEntries(d))
        assertEquals(5L, FileOps.sizeOf(File(d, "a")))
    }

    @Test fun copyCancellationRemovesPartialFile() = runBlocking {
        val src = tmp.newFolder("src"); file(src, "big.bin", "x".repeat(4_000_000))
        val dest = tmp.newFolder("dest")
        val job = async {
            FileOps.copy(listOf(File(src, "big.bin")), dest) { done, _ -> if (done > 100_000) throw CancellationException("user") }
        }
        try { job.await(); fail() } catch (_: CancellationException) {}
        yield()
        assertFalse(File(dest, "big.bin").exists())
    }

    @Test fun extractZipIntoUniqueFolderAndRejectsZipSlip() = runBlocking {
        val d = tmp.newFolder("d")
        val zip = File(d, "photos.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("one.txt")); z.write("1".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("nested/")); z.closeEntry()
            z.putNextEntry(ZipEntry("nested/two.txt")); z.write("22".toByteArray()); z.closeEntry()
        }
        File(d, "photos").mkdir() // forces "photos (1)"
        val out = FileOps.extractZip(zip)
        assertEquals("photos (1)", out.name)
        assertEquals(mapOf("one.txt" to "1", "nested/two.txt" to "22"), tree(out))
        assertEquals(3L, FileOps.zipUncompressedSize(zip))

        val evil = File(d, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("../escaped.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        try { FileOps.extractZip(evil); fail() } catch (e: FileOpException) { assertTrue(e.message!!.contains("outside")) }
        assertFalse(File(d, "escaped.txt").exists())
        assertFalse(File(d, "evil").exists()) // partial output cleaned up

        val notZip = file(d, "fake.zip", "not a zip")
        try { FileOps.extractZip(notZip); fail() } catch (_: java.io.IOException) {}
    }

    @Test fun sortingKeepsFoldersFirst() {
        val items = listOf(
            FileItem("b.txt", "/b.txt", false, 300, 3),
            FileItem("A.jpg", "/A.jpg", false, 100, 1),
            FileItem("zeta", "/zeta", true, 0, 2, childCount = 1),
            FileItem("Alpha", "/Alpha", true, 0, 5, childCount = 9),
            FileItem("c.apk", "/c.apk", false, 200, 4),
        )
        fun names(k: SortKey, asc: Boolean) = FileSorting.sort(items, k, asc).map { it.name }
        assertEquals(listOf("Alpha", "zeta", "A.jpg", "b.txt", "c.apk"), names(SortKey.NAME, true))
        assertEquals(listOf("zeta", "Alpha", "c.apk", "b.txt", "A.jpg"), names(SortKey.NAME, false))
        assertEquals(listOf("zeta", "Alpha", "A.jpg", "b.txt", "c.apk"), names(SortKey.DATE, true))
        assertEquals(listOf("Alpha", "zeta", "b.txt", "c.apk", "A.jpg"), names(SortKey.SIZE, false))
        assertEquals(listOf("Alpha", "zeta", "c.apk", "A.jpg", "b.txt"), names(SortKey.TYPE, true))
    }

    @Test fun isInside() {
        val d = tmp.newFolder("d")
        assertTrue(FileOps.isInside(File(d, "x/y"), d))
        assertTrue(FileOps.isInside(d, d))
        assertFalse(FileOps.isInside(File(d.parentFile, d.name + "2"), d)) // prefix is not containment
    }
}
