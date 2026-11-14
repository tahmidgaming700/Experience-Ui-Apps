package com.experience.virtualos.core

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/**
 * Safe extractor for (gzip-compressed) tar archives containing a Linux root
 * filesystem.
 *
 * Supported: regular files, directories, symbolic links, hard links, GNU long
 * names (`L`/`K`) and POSIX pax headers (`path`, `linkpath`, `size`). Device
 * nodes and FIFOs are skipped (they cannot be created without privileges and
 * PRoot binds the host /dev instead).
 *
 * Safety rules:
 *  * entry paths must be relative and may not contain `..` after normalisation;
 *  * nothing is ever written through an existing symbolic link (every parent
 *    component below the destination is checked with NOFOLLOW_LINKS);
 *  * hard-link targets must be earlier regular-file entries of the archive;
 *  * symbolic-link *targets* are stored verbatim, because absolute targets such
 *    as `/bin/busybox` are resolved inside the guest by PRoot, not on the host.
 *
 * Android forbids hard links in app storage on many devices, so hard links
 * fall back to copying the target file.
 */
class TarExtractor(
    private val onEntry: (name: String, bytesRead: Long) -> Unit = { _, _ -> },
) {
    class UnsafeArchiveException(message: String) : IOException(message)

    data class Result(val files: Int, val directories: Int, val symlinks: Int, val hardlinks: Int, val skipped: Int, val bytes: Long)

    fun extractGzip(archive: File, destination: File): Result =
        GZIPInputStream(BufferedInputStream(FileInputStream(archive), 1 shl 16), 1 shl 16).use { extract(it, destination) }

    fun extract(input: InputStream, destination: File): Result {
        if (!destination.isDirectory && !destination.mkdirs()) throw IOException("Cannot create $destination")
        val root = destination.toPath().toAbsolutePath().normalize()
        val counting = CountingInputStream(input)
        val header = ByteArray(BLOCK)
        var files = 0; var dirs = 0; var symlinks = 0; var hardlinks = 0; var skipped = 0
        var longName: String? = null
        var longLink: String? = null
        var pax: Map<String, String> = emptyMap()
        val deferredModes = ArrayList<Pair<Path, Int>>()

        while (true) {
            if (!readBlock(counting, header)) break
            if (header.all { it.toInt() == 0 }) break // end-of-archive marker
            verifyChecksum(header)
            val type = header[156].toInt().toChar()
            var size = parseOctal(header, 124, 12)
            pax["size"]?.let { size = it.toLong() }
            val mode = parseOctal(header, 100, 8).toInt()

            when (type) {
                'L' -> { longName = readString(counting, size); continue }
                'K' -> { longLink = readString(counting, size); continue }
                'x' -> { pax = parsePax(readString(counting, size)); continue }
                'g' -> { skip(counting, size); continue }
            }

            val rawName = pax["path"] ?: longName ?: ustarName(header)
            val rawLink = pax["linkpath"] ?: longLink ?: cString(header, 157, 100)
            longName = null; longLink = null; pax = emptyMap()

            val relative = safeRelative(rawName)
            if (relative == null) { // the archive root itself ("./")
                skip(counting, size); continue
            }
            val target = root.resolve(relative).normalize()
            if (!target.startsWith(root) || target == root) throw UnsafeArchiveException("Entry escapes destination: $rawName")
            ensureNoSymlinkParents(root, target, rawName)

            when (type) {
                '5' -> {
                    if (Files.isSymbolicLink(target)) throw UnsafeArchiveException("Directory entry replaces a symlink: $rawName")
                    Files.createDirectories(target)
                    deferredModes += target to mode
                    dirs++
                    skip(counting, size)
                }
                '0', '\u0000', '7' -> {
                    Files.createDirectories(target.parent)
                    removeExisting(target)
                    FileOutputStream(target.toFile()).use { out -> copyExact(counting, out, size) }
                    skipPadding(counting, size)
                    applyMode(target, mode)
                    files++
                }
                '2' -> {
                    Files.createDirectories(target.parent)
                    removeExisting(target)
                    Files.createSymbolicLink(target, Paths.get(rawLink))
                    symlinks++
                    skip(counting, size)
                }
                '1' -> {
                    val linkRelative = safeRelative(rawLink) ?: throw UnsafeArchiveException("Bad hard link target: $rawLink")
                    val source = root.resolve(linkRelative).normalize()
                    if (!source.startsWith(root)) throw UnsafeArchiveException("Hard link escapes destination: $rawLink")
                    ensureNoSymlinkParents(root, source, rawLink)
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                        throw UnsafeArchiveException("Hard link target is not an extracted file: $rawLink")
                    }
                    Files.createDirectories(target.parent)
                    removeExisting(target)
                    try {
                        Files.createLink(target, source)
                    } catch (e: Exception) {
                        // Hard links are commonly denied for Android apps; copy instead.
                        Files.copy(source, target)
                        applyMode(target, modeOf(source))
                    }
                    hardlinks++
                    skip(counting, size)
                }
                else -> { // char/block devices, FIFOs, contiguous files etc.
                    skipped++
                    skip(counting, size)
                }
            }
            onEntry(relative, counting.count)
        }
        // Apply directory permissions last so read-only directories do not
        // block extraction of their children.
        for ((dir, mode) in deferredModes.asReversed()) applyMode(dir, mode or 0b111_000_000)
        return Result(files, dirs, symlinks, hardlinks, skipped, counting.count)
    }

    private fun ensureNoSymlinkParents(root: Path, target: Path, name: String) {
        var current = target.parent
        while (current != null && current != root && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) throw UnsafeArchiveException("Entry would be written through a symlink: $name")
            current = current.parent
        }
    }

    private fun removeExisting(path: Path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw FileAlreadyExistsException(path.toString(), null, "a directory already exists here")
        }
        Files.deleteIfExists(path)
    }

    private fun modeOf(path: Path): Int = try {
        var m = 0
        for (p in Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)) m = m or (1 shl (8 - p.ordinal))
        m
    } catch (_: Exception) { 0b110_100_100 }

    private fun applyMode(path: Path, mode: Int) {
        val perms = HashSet<PosixFilePermission>()
        val all = PosixFilePermission.values() // OWNER_READ .. OTHERS_EXECUTE, 9 entries
        for (i in all.indices) if (mode and (1 shl (8 - i)) != 0) perms += all[i]
        perms += PosixFilePermission.OWNER_READ
        perms += PosixFilePermission.OWNER_WRITE
        try {
            Files.setPosixFilePermissions(path, perms)
        } catch (_: Exception) {
            val f = path.toFile()
            f.setReadable(true, mode and 0b000_100_100 == 0)
            f.setWritable(true, true)
            if (mode and 0b001_001_001 != 0) f.setExecutable(true, mode and 0b000_001_001 == 0)
        }
    }

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        var count = 0L
        override fun read(): Int = delegate.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len).also { if (it > 0) count += it }
        override fun close() = delegate.close()
    }

    companion object {
        private const val BLOCK = 512
        private const val MAX_META = 1 shl 20

        /** Normalises an archive path; returns null for the archive root, throws when unsafe. */
        fun safeRelative(raw: String): String? {
            if (raw.isEmpty()) throw UnsafeArchiveException("Empty entry name")
            if (raw.startsWith("/")) throw UnsafeArchiveException("Absolute entry path: $raw")
            if (raw.contains('\u0000') || raw.contains('\\')) throw UnsafeArchiveException("Invalid entry path: $raw")
            val parts = ArrayList<String>()
            for (part in raw.split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> throw UnsafeArchiveException("Parent reference in entry path: $raw")
                    else -> parts += part
                }
            }
            return if (parts.isEmpty()) null else parts.joinToString("/")
        }

        private fun readBlock(input: InputStream, buf: ByteArray): Boolean {
            var off = 0
            while (off < BLOCK) {
                val n = input.read(buf, off, BLOCK - off)
                if (n < 0) {
                    if (off == 0) return false
                    throw EOFException("Truncated tar header")
                }
                off += n
            }
            return true
        }

        private fun verifyChecksum(header: ByteArray) {
            val stored = parseOctal(header, 148, 8)
            var sum = 0L
            for (i in 0 until BLOCK) sum += if (i in 148 until 156) 32 else (header[i].toInt() and 0xff)
            if (stored != sum) throw IOException("Corrupt tar header (checksum mismatch)")
        }

        internal fun parseOctal(buf: ByteArray, offset: Int, length: Int): Long {
            if (buf[offset].toInt() and 0x80 != 0) { // GNU base-256 encoding
                var v = 0L
                for (i in offset + 1 until offset + length) v = (v shl 8) or (buf[i].toLong() and 0xff)
                return v
            }
            var v = 0L
            for (i in offset until offset + length) {
                val c = buf[i].toInt()
                if (c == 0 || c == ' '.code) { if (v != 0L) break else continue }
                if (c < '0'.code || c > '7'.code) throw IOException("Invalid octal field in tar header")
                v = (v shl 3) + (c - '0'.code)
            }
            return v
        }

        private fun cString(buf: ByteArray, offset: Int, length: Int): String {
            var end = offset
            while (end < offset + length && buf[end].toInt() != 0) end++
            return String(buf, offset, end - offset, Charsets.UTF_8)
        }

        private fun ustarName(header: ByteArray): String {
            val name = cString(header, 0, 100)
            val magic = cString(header, 257, 6)
            if (magic.startsWith("ustar")) {
                val prefix = cString(header, 345, 155)
                if (prefix.isNotEmpty()) return "$prefix/$name"
            }
            return name
        }

        private fun readString(input: InputStream, size: Long): String {
            if (size < 0 || size > MAX_META) throw IOException("Unreasonable tar metadata size $size")
            val data = ByteArray(size.toInt())
            var off = 0
            while (off < data.size) {
                val n = input.read(data, off, data.size - off)
                if (n < 0) throw EOFException("Truncated tar metadata")
                off += n
            }
            skipPadding(input, size)
            return String(data, Charsets.UTF_8).trimEnd('\u0000')
        }

        private fun parsePax(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            var i = 0
            val bytes = text.toByteArray(Charsets.UTF_8)
            while (i < bytes.size) {
                var sp = i
                while (sp < bytes.size && bytes[sp] != ' '.code.toByte()) sp++
                if (sp >= bytes.size) break
                val len = String(bytes, i, sp - i).toIntOrNull() ?: throw IOException("Bad pax record")
                if (len <= 0 || i + len > bytes.size) throw IOException("Bad pax record length")
                val record = String(bytes, sp + 1, i + len - sp - 2, Charsets.UTF_8) // drop trailing \n
                val eq = record.indexOf('=')
                if (eq > 0) out[record.substring(0, eq)] = record.substring(eq + 1)
                i += len
            }
            return out
        }

        private fun copyExact(input: InputStream, out: java.io.OutputStream, size: Long) {
            val buffer = ByteArray(1 shl 16)
            var remaining = size
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n < 0) throw EOFException("Truncated tar entry")
                out.write(buffer, 0, n)
                remaining -= n
            }
        }

        private fun skip(input: InputStream, size: Long) {
            copyExact(input, NullOutputStream, size)
            skipPadding(input, size)
        }

        private fun skipPadding(input: InputStream, size: Long) {
            val pad = ((BLOCK - (size % BLOCK)) % BLOCK)
            copyExact(input, NullOutputStream, pad)
        }

        private object NullOutputStream : java.io.OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        }
    }
}
