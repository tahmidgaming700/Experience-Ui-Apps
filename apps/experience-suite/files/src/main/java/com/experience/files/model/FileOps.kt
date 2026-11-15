package com.experience.files.model

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Snapshot of one directory entry. Pure data, so sorting is testable without Android. */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    /** Number of entries for directories (-1 if unreadable), 0 for files. */
    val childCount: Int = 0,
) {
    val file: File get() = File(path)
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
    val isHidden: Boolean get() = name.startsWith(".")

    companion object {
        fun of(f: File) = FileItem(
            name = f.name.ifEmpty { f.path },
            path = f.absolutePath,
            isDirectory = f.isDirectory,
            size = if (f.isDirectory) 0 else f.length(),
            lastModified = f.lastModified(),
            childCount = if (f.isDirectory) f.list()?.size ?: -1 else 0,
        )
    }
}

enum class SortKey(val label: String) { NAME("Name"), DATE("Date modified"), SIZE("Size"), TYPE("Type") }

object FileSorting {
    /** Sorts with folders first (in either direction); ties break on name. */
    fun sort(items: List<FileItem>, key: SortKey, ascending: Boolean): List<FileItem> {
        val byName = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val primary: Comparator<FileItem> = when (key) {
            SortKey.NAME -> byName
            SortKey.DATE -> compareBy<FileItem> { it.lastModified }.then(byName)
            SortKey.SIZE -> compareBy<FileItem> { if (it.isDirectory) it.childCount.toLong() else it.size }.then(byName)
            SortKey.TYPE -> compareBy<FileItem> { it.extension }.then(byName)
        }
        val directed = if (ascending) primary else primary.reversed()
        return items.sortedWith(compareBy<FileItem> { !it.isDirectory }.then(directed))
    }
}

/** Progress callback: bytes done so far, and the name of the file being processed. */
typealias ProgressListener = (doneBytes: Long, current: String) -> Unit

class FileOpException(message: String) : IOException(message)

/**
 * File-system operations used by Experience Files. All long operations are
 * suspend functions that check for coroutine cancellation between chunks, so the
 * user's Cancel button stops them promptly; a partially written file is removed.
 */
object FileOps {
    private const val BUFFER = 64 * 1024

    /** Returns an error message for an invalid file name, or null if it is acceptable. */
    fun validateName(name: String): String? = when {
        name.isBlank() -> "Name can't be empty"
        name == "." || name == ".." -> "That name is reserved"
        name.contains('/') || name.contains('\u0000') -> "Name can't contain /"
        name.toByteArray().size > 255 -> "Name is too long"
        else -> null
    }

    /**
     * A name that does not exist in [dir]: "report.pdf", then "report (1).pdf",
     * "report (2).pdf"… Folders and dot-files keep the whole name as the base.
     */
    fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val isFileWithExt = dot > 0 && !File(dir, name).isDirectory
        val base = if (isFileWithExt) name.substring(0, dot) else name
        val ext = if (isFileWithExt) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    fun createFolder(parent: File, name: String): File {
        validateName(name)?.let { throw FileOpException(it) }
        val dir = File(parent, name)
        if (dir.exists()) throw FileOpException("\"$name\" already exists")
        if (!dir.mkdir()) throw FileOpException("Couldn't create \"$name\"")
        return dir
    }

    fun rename(file: File, newName: String): File {
        validateName(newName)?.let { throw FileOpException(it) }
        if (newName == file.name) return file
        val target = File(file.parentFile, newName)
        // Allow a case-only rename on case-insensitive volumes (FAT/exFAT SD cards).
        if (target.exists() && !newName.equals(file.name, ignoreCase = true)) throw FileOpException("\"$newName\" already exists")
        if (!file.renameTo(target)) throw FileOpException("Couldn't rename \"${file.name}\"")
        return target
    }

    /** True if [child] is [parent] or lies somewhere below it. */
    fun isInside(child: File, parent: File): Boolean {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        return c == p || c.startsWith(p.trimEnd('/') + "/")
    }

    /** Total bytes of files (recursively for folders). Does not follow symlinked folders. */
    fun sizeOf(file: File): Long {
        if (!file.isDirectory) return file.length()
        return file.walkTopDown().onEnter { !isSymlink(it) || it == file }.filter { it.isFile }.sumOf { it.length() }
    }

    /** Files and folders below [file] (excluding itself). */
    fun countEntries(file: File): Pair<Int, Int> {
        if (!file.isDirectory) return 1 to 0
        var files = 0; var dirs = 0
        file.walkTopDown().onEnter { !isSymlink(it) || it == file }.drop(1).forEach { if (it.isDirectory) dirs++ else files++ }
        return files to dirs
    }

    fun totalSize(files: List<File>): Long = files.sumOf { sizeOf(it) }

    private fun isSymlink(f: File) = runCatching { java.nio.file.Files.isSymbolicLink(f.toPath()) }.getOrDefault(false)

    /** Copies [sources] into [destDir], renaming on conflict. Returns the created roots. */
    suspend fun copy(sources: List<File>, destDir: File, progress: ProgressListener = { _, _ -> }): List<File> {
        var done = 0L
        val created = mutableListOf<File>()
        for (src in sources) {
            if (src.isDirectory && isInside(destDir, src)) throw FileOpException("Can't copy \"${src.name}\" into itself")
            val target = File(destDir, uniqueName(destDir, src.name))
            done = copyRecursive(src, target, done, progress)
            created += target
        }
        return created
    }

    private suspend fun copyRecursive(src: File, target: File, doneIn: Long, progress: ProgressListener): Long {
        currentCoroutineContext().ensureActive()
        var done = doneIn
        if (src.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw FileOpException("Couldn't create \"${target.name}\"")
            for (child in src.listFiles().orEmpty().sortedBy { it.name }) {
                done = copyRecursive(child, File(target, child.name), done, progress)
            }
            target.setLastModified(src.lastModified())
            return done
        }
        progress(done, src.name)
        try {
            src.inputStream().use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress(done, src.name)
                    }
                    out.fd.sync()
                }
            }
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw if (e is FileOpException) e else FileOpException("Couldn't copy \"${src.name}\": ${e.message}")
        }
        target.setLastModified(src.lastModified())
        return done
    }

    /**
     * Moves [sources] into [destDir]. A rename is tried first (instant on the same
     * volume); otherwise the item is copied and the original deleted. Items already
     * in [destDir] are left alone. Returns the new locations.
     */
    suspend fun move(sources: List<File>, destDir: File, progress: ProgressListener = { _, _ -> }): List<File> {
        val result = mutableListOf<File>()
        var done = 0L
        for (src in sources) {
            currentCoroutineContext().ensureActive()
            if (src.parentFile?.canonicalPath == destDir.canonicalPath) { result += src; continue }
            if (src.isDirectory && isInside(destDir, src)) throw FileOpException("Can't move \"${src.name}\" into itself")
            val target = File(destDir, uniqueName(destDir, src.name))
            val size = sizeOf(src)
            if (src.renameTo(target)) {
                done += size
                progress(done, src.name)
            } else {
                done = copyRecursive(src, target, done, progress)
                deleteRecursive(src)
            }
            result += target
        }
        return result
    }

    /** Deletes files and folders recursively. Throws if anything could not be removed. */
    suspend fun delete(files: List<File>, progress: (Int, String) -> Unit = { _, _ -> }) {
        files.forEachIndexed { i, f ->
            progress(i, f.name)
            deleteRecursive(f)
        }
    }

    private suspend fun deleteRecursive(f: File) {
        currentCoroutineContext().ensureActive()
        if (f.isDirectory && !isSymlink(f)) f.listFiles()?.forEach { deleteRecursive(it) }
        if (!f.delete() && f.exists()) throw FileOpException("Couldn't delete \"${f.name}\"")
    }

    /**
     * Extracts [zip] into a new folder next to it (named after the archive, made
     * unique). Entries that would escape that folder ("zip slip") are rejected.
     */
    suspend fun extractZip(zip: File, progress: ProgressListener = { _, _ -> }): File {
        val parent = zip.parentFile ?: throw FileOpException("Archive has no parent folder")
        val dest = File(parent, uniqueName(parent, zip.name.substringBeforeLast('.').ifEmpty { zip.name }))
        if (!dest.mkdirs()) throw FileOpException("Couldn't create \"${dest.name}\"")
        val destPath = dest.canonicalPath + File.separator
        var done = 0L
        var entries = 0
        try {
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zin.nextEntry ?: break
                    entries++
                    val out = File(dest, entry.name)
                    if (!(out.canonicalPath + if (entry.isDirectory) File.separator else "").startsWith(destPath)) {
                        throw FileOpException("Archive entry \"${entry.name}\" points outside the folder")
                    }
                    if (entry.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { o ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = zin.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            progress(done, entry.name)
                        }
                    }
                    if (entry.time > 0) out.setLastModified(entry.time)
                }
            }
            // ZipInputStream reads non-zip data as an empty archive.
            if (entries == 0 && zip.length() > 0) throw java.util.zip.ZipException("no entries")
        } catch (e: java.util.zip.ZipException) {
            dest.deleteRecursively()
            throw FileOpException("\"${zip.name}\" is not a valid zip archive")
        } catch (e: Throwable) {
            dest.deleteRecursively()
            throw e
        }
        return dest
    }

    /** Sum of uncompressed sizes recorded in the archive (for progress). */
    fun zipUncompressedSize(zip: File): Long = runCatching {
        java.util.zip.ZipFile(zip).use { z -> z.entries().asSequence().sumOf { maxOf(0L, it.size) } }
    }.getOrDefault(0L)
}
