package com.experience.settings.update

import android.content.Context
import android.os.Build
import com.experience.design.system.SystemInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class Done(val status: UpdateStatus, val checkedAt: Long) : CheckState
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val done: Long, val total: Long) : DownloadState
    data object Verifying : DownloadState
    data class Ready(val file: File, val build: ChannelBuild) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * The Experience "foundation" updater: fetches the OTA manifest, compares build
 * numbers and downloads + verifies the GSI image. It runs in an app-wide scope so a
 * download continues while the user leaves the screen (as long as the process lives).
 * It never flashes anything: an Android 11 GSI cannot install itself safely, so the
 * UI shows the fastbootd steps instead.
 */
object Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private val _check = MutableStateFlow<CheckState>(CheckState.Idle)
    val check: StateFlow<CheckState> = _check
    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    val manifestUrl: String get() = SystemInfo.prop("ro.experience.updater.uri").ifBlank { UpdateLogic.DEFAULT_MANIFEST_URL }
    val installedBuild: String get() = SystemInfo.prop("ro.experience.build.number").ifBlank { "0" }

    fun defaultChannel(): String = SystemInfo.experienceReleaseChannel.lowercase().takeIf { it in UpdateLogic.CHANNELS } ?: "stable"

    fun check(channel: String) {
        checkJob?.cancel()
        _check.value = CheckState.Checking
        checkJob = scope.launch {
            val status = try {
                val manifest = UpdateLogic.parse(fetch(manifestUrl))
                UpdateLogic.evaluate(manifest, channel, installedBuild, Build.VERSION.SDK_INT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ManifestException) {
                UpdateStatus.Error(e.message ?: "Invalid update manifest")
            } catch (e: IOException) {
                UpdateStatus.Error("Couldn't reach the update server (${e.message ?: e.javaClass.simpleName})")
            }
            _check.value = CheckState.Done(status, System.currentTimeMillis())
        }
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code == 404) throw IOException("no manifest published at $url")
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun downloadsDir(context: Context): File =
        (context.getExternalFilesDir("updates") ?: File(context.filesDir, "updates")).apply { mkdirs() }

    fun startDownload(context: Context, build: ChannelBuild) {
        if (downloadJob?.isActive == true) return
        val url = build.url ?: return
        val expected = build.sha256 ?: return
        val name = build.fileName ?: "experience-update.img.xz"
        val dir = downloadsDir(context.applicationContext)
        val target = File(dir, name)
        val part = File(dir, "$name.part")
        downloadJob = scope.launch {
            try {
                // Reuse a previously verified download.
                if (target.isFile && sha256(target) == expected) {
                    _download.value = DownloadState.Ready(target, build); return@launch
                }
                _download.value = DownloadState.Downloading(0, build.size)
                val digest = MessageDigest.getInstance("SHA-256")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
                try {
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: build.size
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            var done = 0L
                            var lastEmit = 0L
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                done += n
                                if (done - lastEmit > 512 * 1024) { lastEmit = done; _download.value = DownloadState.Downloading(done, total) }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                _download.value = DownloadState.Verifying
                val actual = UpdateLogic.hex(digest.digest())
                if (actual != expected) {
                    part.delete()
                    _download.value = DownloadState.Failed("Checksum mismatch: the downloaded file is corrupt or was tampered with, so it was deleted.")
                    return@launch
                }
                target.delete()
                if (!part.renameTo(target)) throw IOException("couldn't save ${target.name}")
                _download.value = DownloadState.Ready(target, build)
            } catch (e: CancellationException) {
                part.delete()
                _download.value = DownloadState.Idle
                throw e
            } catch (e: IOException) {
                part.delete()
                _download.value = DownloadState.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun deleteDownload(file: File) {
        file.delete()
        _download.value = DownloadState.Idle
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return UpdateLogic.hex(md.digest())
    }
}
