package com.experience.virtualos.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.experience.virtualos.App
import com.experience.virtualos.R
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.images.CatalogImage
import com.experience.virtualos.core.runtime.InstallStage
import com.experience.virtualos.core.runtime.RuntimeManifest
import com.experience.virtualos.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

object WorkKeys {
    const val STAGE = "stage"
    const val DONE = "done"
    const val TOTAL = "total"
    const val ERROR = "error"
    const val MODE = "mode"
    const val ARCHIVE = "archive"
    const val MANIFEST = "manifest"
    const val IMAGE = "image"
    const val RUNTIME_WORK = "runtime-install"
    fun imageWork(id: String) = "image-$id"
}

/** Shared progress + foreground-notification handling. */
abstract class ProgressWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    abstract val title: String
    private var lastUpdate = 0L

    protected suspend fun report(stage: String, done: Long = 0, total: Long = 0, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdate < 400) return
        lastUpdate = now
        setProgress(workDataOf(WorkKeys.STAGE to stage, WorkKeys.DONE to done, WorkKeys.TOTAL to total))
        try { setForeground(foregroundInfo(stage, done, total)) } catch (_: Exception) { /* background start restrictions */ }
    }

    protected fun reportBlocking(stage: String, done: Long = 0, total: Long = 0) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 400) return
        lastUpdate = now
        setProgressAsync(workDataOf(WorkKeys.STAGE to stage, WorkKeys.DONE to done, WorkKeys.TOTAL to total))
        try { setForegroundAsync(foregroundInfo(stage, done, total)) } catch (_: Exception) {}
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Starting…", 0, 0)

    private fun foregroundInfo(stage: String, done: Long, total: Long): ForegroundInfo {
        val builder = NotificationCompat.Builder(applicationContext, App.CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_stat_vm)
            .setContentTitle(title)
            .setContentText(if (total > 0) "$stage · ${FileUtils.formatBytes(done)} of ${FileUtils.formatBytes(total)}" else stage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (total > 0) builder.setProgress(1000, ((done * 1000) / total).toInt().coerceIn(0, 1000), false) else builder.setProgress(0, 0, true)
        val id = title.hashCode()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, builder.build())
    }

    protected fun failure(message: String): Result = Result.failure(workDataOf(WorkKeys.ERROR to message))
}

class RuntimeInstallWorker(context: Context, params: WorkerParameters) : ProgressWorker(context, params) {
    override val title = "Installing Experience Virtual OS runtime"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val graph = applicationContext.graph
        val installer = graph.runtimeInstaller
        val log = graph.activityLog
        try {
            report("Preparing…", force = true)
            val onStage: (InstallStage) -> Unit = { stage ->
                when (stage) {
                    InstallStage.FetchingManifest -> reportBlocking("Fetching manifest…")
                    is InstallStage.Downloading -> reportBlocking("Downloading runtime", stage.bytes, stage.total)
                    InstallStage.Verifying -> reportBlocking("Verifying SHA-256…")
                    is InstallStage.Extracting -> reportBlocking("Extracting", stage.bytes, stage.total)
                    InstallStage.Checking -> reportBlocking("Checking files and running self-test…")
                    InstallStage.Activating -> reportBlocking("Activating…")
                }
            }
            val installed = if (inputData.getString(WorkKeys.MODE) == "local") {
                val archive = File(inputData.getString(WorkKeys.ARCHIVE)!!)
                val manifestFile = File(inputData.getString(WorkKeys.MANIFEST)!!)
                try {
                    installer.installFromFile(archive, RuntimeManifest.parse(manifestFile.readText()), onStage)
                } finally {
                    archive.delete(); manifestFile.delete()
                }
            } else {
                report("Fetching manifest…", force = true)
                val manifest = installer.fetchManifest(graph.settings.manifestUrl)
                installer.install(manifest, onStage) { isStopped }
            }
            log.success("Runtime", "Runtime ${installed.runtimeVersion} installed and self-tested (${installed.source})")
            Result.success()
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            log.error("Runtime", "Runtime installation failed: $message")
            failure(message)
        }
    }

    companion object {
        fun enqueueRemote(context: Context) = enqueue(context, workDataOf(WorkKeys.MODE to "remote"))

        fun enqueueLocal(context: Context, archive: File, manifest: File) =
            enqueue(context, workDataOf(WorkKeys.MODE to "local", WorkKeys.ARCHIVE to archive.absolutePath, WorkKeys.MANIFEST to manifest.absolutePath))

        private fun enqueue(context: Context, data: Data) {
            val request = OneTimeWorkRequestBuilder<RuntimeInstallWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WorkKeys.RUNTIME_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}

class ImageInstallWorker(context: Context, params: WorkerParameters) : ProgressWorker(context, params) {
    override val title = "Installing Android system image"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val graph = applicationContext.graph
        val image = try {
            Json.decodeFromString(CatalogImage.serializer(), inputData.getString(WorkKeys.IMAGE)!!)
        } catch (e: Exception) {
            return@withContext failure("Invalid image request")
        }
        try {
            report("Starting download…", force = true)
            val installed = graph.imageRepository.install(image, { stage, done, total -> reportBlocking(stage, done, total) }) { isStopped }
            graph.activityLog.success("Images", "Installed ${installed.title}")
            Result.success()
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            graph.activityLog.error("Images", "${image.title}: $message")
            failure(message)
        }
    }

    companion object {
        fun enqueue(context: Context, image: CatalogImage) {
            val request = OneTimeWorkRequestBuilder<ImageInstallWorker>()
                .setInputData(workDataOf(WorkKeys.IMAGE to Json.encodeToString(CatalogImage.serializer(), image)))
                .addTag("image:${image.id}")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WorkKeys.imageWork(image.id), ExistingWorkPolicy.KEEP, request)
        }
    }
}
