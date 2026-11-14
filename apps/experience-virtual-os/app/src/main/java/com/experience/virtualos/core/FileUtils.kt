package com.experience.virtualos.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/** Small, dependency-free file helpers shared by the runtime and device layers. */
object FileUtils {

    fun sha256(file: File): String = FileInputStream(file).use { sha256(it) }

    fun sha256(input: InputStream): String = digest("SHA-256", input)

    fun sha1(file: File): String = FileInputStream(file).use { digest("SHA-1", it) }

    private fun digest(algorithm: String, input: InputStream): String {
        val md = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            md.update(buffer, 0, n)
        }
        return md.digest().toHex()
    }

    fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return out.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Writes [text] to [target] atomically: data goes to a sibling temp file,
     * is fsynced, then renamed over the target. Readers see either the old or
     * the new content, never a partial file.
     */
    fun writeAtomically(target: File, text: String) = writeAtomically(target, text.toByteArray(Charsets.UTF_8))

    fun writeAtomically(target: File, bytes: ByteArray) {
        val parent = target.absoluteFile.parentFile ?: throw IOException("No parent for $target")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create $parent")
        val temp = File(parent, ".${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Cannot replace $target")
        }
    }

    /** Recursively deletes [file] without following symbolic links. */
    fun deleteRecursively(file: File?) {
        if (file == null) return
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            file.delete()
        }
    }

    /** Total size of regular files below [file], not following symlinks. */
    fun sizeOf(file: File): Long {
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return 0
        if (Files.isSymbolicLink(path)) return 0
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return file.listFiles()?.sumOf { sizeOf(it) } ?: 0
        }
        return file.length()
    }

    fun copy(source: File, destination: File, onProgress: ((Long) -> Unit)? = null) {
        destination.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { out ->
                val buffer = ByteArray(1 shl 16)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    total += n
                    onProgress?.invoke(total)
                }
                out.fd.sync()
            }
        }
    }

    fun readTail(file: File, maxBytes: Int): String {
        if (!file.isFile) return ""
        java.io.RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val start = maxOf(0L, length - maxBytes)
            raf.seek(start)
            val data = ByteArray((length - start).toInt())
            raf.readFully(data)
            return String(data, Charsets.UTF_8)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return if (value >= 100) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
    }
}
