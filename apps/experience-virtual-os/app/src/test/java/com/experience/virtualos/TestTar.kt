package com.experience.virtualos

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** Minimal ustar writer for building test archives. */
class TestTar {
    private val out = ByteArrayOutputStream()

    fun dir(name: String, mode: Int = 0b111_101_101) = entry(name, '5', ByteArray(0), mode)
    fun file(name: String, content: String, mode: Int = 0b110_100_100) = entry(name, '0', content.toByteArray(), mode)
    fun symlink(name: String, target: String) = entry(name, '2', ByteArray(0), 0b111_111_111, link = target)
    fun hardlink(name: String, target: String) = entry(name, '1', ByteArray(0), 0b110_100_100, link = target)
    fun device(name: String) = entry(name, '3', ByteArray(0), 0b110_110_110)

    fun gnuLongName(name: String, content: String) = apply {
        entry("././@LongLink", 'L', (name + "\u0000").toByteArray(), 0)
        entry(name.take(99), '0', content.toByteArray(), 0b110_100_100)
    }

    fun paxPath(name: String, content: String) = apply {
        val record = paxRecord("path", name)
        entry("PaxHeaders/x", 'x', record.toByteArray(), 0b110_100_100)
        entry("placeholder", '0', content.toByteArray(), 0b110_100_100)
    }

    private fun paxRecord(key: String, value: String): String {
        val body = " $key=$value\n"
        val bodyBytes = body.toByteArray().size
        var len = bodyBytes + 1
        while (len.toString().length + bodyBytes != len) len = len.toString().length + bodyBytes
        return len.toString() + body
    }

    fun entry(name: String, type: Char, data: ByteArray, mode: Int, link: String = "") = apply {
        val h = ByteArray(512)
        fun put(off: Int, len: Int, s: String) { val b = s.toByteArray(); System.arraycopy(b, 0, h, off, minOf(b.size, len)) }
        fun octal(off: Int, len: Int, v: Long) = put(off, len, v.toString(8).padStart(len - 1, '0'))
        put(0, 100, name)
        octal(100, 8, mode.toLong())
        octal(108, 8, 0); octal(116, 8, 0)
        octal(124, 12, data.size.toLong())
        octal(136, 12, 0)
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        h[156] = type.code.toByte()
        put(157, 100, link)
        put(257, 6, "ustar\u0000"); put(263, 2, "00")
        val sum = h.sumOf { it.toInt() and 0xff }
        put(148, 8, sum.toString(8).padStart(6, '0') + "\u0000 ")
        out.write(h)
        out.write(data)
        val pad = (512 - data.size % 512) % 512
        out.write(ByteArray(pad))
    }

    fun bytes(): ByteArray = out.toByteArray() + ByteArray(1024)

    fun writeGzip(file: File): File {
        GZIPOutputStream(file.outputStream()).use { it.write(bytes()) }
        return file
    }
}
