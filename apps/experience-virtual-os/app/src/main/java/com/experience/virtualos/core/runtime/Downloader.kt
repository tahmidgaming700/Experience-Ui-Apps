package com.experience.virtualos.core.runtime

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/** Abstraction over network transfers so the installer can be unit-tested. */
interface Downloader {
    /** Fetches a small text document (e.g. manifest.json). */
    fun fetchText(url: String, maxBytes: Int = 1 shl 20): String

    /**
     * Downloads [url] into [destination], resuming a partial file when the
     * server supports byte ranges. [onProgress] receives (bytesSoFar, total or -1).
     * [isCancelled] is polled between chunks.
     */
    fun download(url: String, destination: File, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean)
}

class HttpDownloader(private val userAgent: String) : Downloader {

    override fun fetchText(url: String, maxBytes: Int): String {
        val conn = open(url, null)
        try {
            val code = conn.responseCode
            if (code == 404) throw IOException("Not found (HTTP 404): $url")
            if (code !in 200..299) throw IOException("HTTP $code from ${conn.url.host}")
            return conn.inputStream.use { readLimited(it, maxBytes) }
        } finally {
            conn.disconnect()
        }
    }

    override fun download(url: String, destination: File, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean) {
        destination.parentFile?.mkdirs()
        val existing = if (destination.isFile) destination.length() else 0L
        val conn = open(url, if (existing > 0) "bytes=$existing-" else null)
        try {
            val code = conn.responseCode
            val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (code != HttpURLConnection.HTTP_OK && !append) {
                if (code == 416) { // range not satisfiable: start again
                    destination.delete()
                    return download(url, destination, onProgress, isCancelled)
                }
                throw IOException("HTTP $code from ${conn.url.host}")
            }
            val length = conn.contentLengthLong
            val total = if (length < 0) -1 else if (append) existing + length else length
            var done = if (append) existing else 0L
            conn.inputStream.use { input ->
                FileOutputStream(destination, append).use { out ->
                    val buffer = ByteArray(1 shl 16)
                    var lastReport = 0L
                    while (true) {
                        if (isCancelled()) throw CancellationException("Download cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - lastReport >= 256 * 1024) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                    out.fd.sync()
                }
            }
            onProgress(done, total)
            if (total >= 0 && done != total) throw IOException("Download ended early ($done of $total bytes)")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, range: String?): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            if (current.protocol != "https") throw IOException("Refusing non-HTTPS URL $current")
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (range != null) conn.setRequestProperty("Range", range)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without Location")
                conn.disconnect()
                current = URL(current, location)
                return@repeat
            }
            return conn
        }
        throw IOException("Too many redirects for $url")
    }

    private fun readLimited(input: InputStream, max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) throw IOException("Response larger than $max bytes")
        }
        return out.toString("UTF-8")
    }
}
