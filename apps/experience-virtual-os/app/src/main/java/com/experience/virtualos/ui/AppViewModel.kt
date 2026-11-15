package com.experience.virtualos.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.host.HostInfo
import com.experience.virtualos.core.images.CatalogImage
import com.experience.virtualos.core.images.InstalledImage
import com.experience.virtualos.core.runtime.InstalledRuntime
import com.experience.virtualos.core.runtime.ManifestValidator
import com.experience.virtualos.core.runtime.RuntimeManifest
import com.experience.virtualos.core.vm.GuestKind
import com.experience.virtualos.core.vm.VirtualDevice
import com.experience.virtualos.graph
import com.experience.virtualos.work.ImageInstallWorker
import com.experience.virtualos.work.RuntimeInstallWorker
import com.experience.virtualos.work.WorkKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Snapshot of the runtime for display. */
data class RuntimeState(
    val installed: InstalledRuntime? = null,
    val problems: List<String> = emptyList(),
    val sizeBytes: Long = 0,
    val prootProblems: List<String> = emptyList(),
    val remote: RemoteManifestState = RemoteManifestState.Unknown,
) {
    val healthy: Boolean get() = installed != null && problems.isEmpty() && prootProblems.isEmpty()
}

sealed interface RemoteManifestState {
    data object Unknown : RemoteManifestState
    data object Checking : RemoteManifestState
    data class Available(val manifest: RuntimeManifest, val isUpdate: Boolean) : RemoteManifestState
    data class Error(val message: String) : RemoteManifestState
}

data class TaskProgress(val running: Boolean, val stage: String, val done: Long, val total: Long, val error: String?, val succeeded: Boolean) {
    val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Loaded(val images: List<CatalogImage>) : CatalogState
    data class Error(val message: String) : CatalogState
}

data class StorageUsage(val runtime: Long, val images: Long, val devices: Long, val free: Long, val total: Long)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app.graph
    private val work = WorkManager.getInstance(app)

    val settings = g.settings.state
    val devices = g.deviceStore.devices
    val vmStatus = g.vmController.status
    val activity = g.activityLog.entries

    private val _host = MutableStateFlow(g.hostInfo())
    val host: StateFlow<HostInfo> = _host.asStateFlow()

    private val _runtime = MutableStateFlow(RuntimeState())
    val runtime: StateFlow<RuntimeState> = _runtime.asStateFlow()

    private val _images = MutableStateFlow<List<InstalledImage>>(emptyList())
    val installedImages: StateFlow<List<InstalledImage>> = _images.asStateFlow()

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    val runtimeTask: StateFlow<TaskProgress?> = work.getWorkInfosForUniqueWorkLiveData(WorkKeys.RUNTIME_WORK).asFlow()
        .map { infos -> infos.lastOrNull()?.toProgress()?.also { if (!it.running) refreshRuntime() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val imageTasks: StateFlow<Map<String, TaskProgress>> = work.getWorkInfosByTagLiveData(ImageInstallWorker::class.java.name).asFlow()
        .map { infos ->
            val map = HashMap<String, TaskProgress>()
            // Unique work names are not exposed on WorkInfo, so the image id travels in the progress data via tags.
            infos.forEach { info -> info.tags.firstOrNull { it.startsWith("image:") }?.let { map[it.removePrefix("image:")] = info.toProgress() } }
            if (infos.any { it.state.isFinished }) refreshImages()
            map
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        refreshAll()
    }

    fun refreshAll() {
        _host.value = g.hostInfo()
        refreshRuntime()
        refreshImages()
        refreshStorage()
    }

    fun refreshRuntime() = viewModelScope.launch(Dispatchers.IO) {
        val installed = g.runtimeInstaller.current()
        val problems = if (installed == null) emptyList() else g.runtimeInstaller.verify()
        val size = g.runtimeInstaller.currentDirectory()?.let { FileUtils.sizeOf(it) } ?: 0
        _runtime.value = _runtime.value.copy(installed = installed, problems = problems, sizeBytes = size, prootProblems = g.prootLauncher().check())
    }

    fun refreshImages() = viewModelScope.launch(Dispatchers.IO) { _images.value = g.imageRepository.installed() }

    fun refreshStorage() = viewModelScope.launch(Dispatchers.IO) {
        val data = g.dataDir
        _storage.value = StorageUsage(
            runtime = FileUtils.sizeOf(File(data, "runtime")),
            images = FileUtils.sizeOf(File(data, "images")),
            devices = FileUtils.sizeOf(File(data, "devices")),
            free = data.usableSpace,
            total = data.totalSpace,
        )
    }

    fun consumeMessage() { _messages.value = null }
    private fun say(text: String) { _messages.value = text }

    // ---- Settings -------------------------------------------------------
    fun updateSettings(change: (com.experience.virtualos.core.AppSettings) -> com.experience.virtualos.core.AppSettings) = g.settings.update(change)

    // ---- Runtime --------------------------------------------------------
    fun checkForRuntime() = viewModelScope.launch(Dispatchers.IO) {
        _runtime.value = _runtime.value.copy(remote = RemoteManifestState.Checking)
        _runtime.value = _runtime.value.copy(
            remote = try {
                val m = g.runtimeInstaller.fetchManifest(g.settings.manifestUrl)
                RemoteManifestState.Available(m, g.runtimeInstaller.isUpdate(m))
            } catch (e: Exception) {
                RemoteManifestState.Error(e.message ?: e.toString())
            },
        )
    }

    fun installRuntime() {
        g.activityLog.info("Runtime", "Runtime installation started")
        RuntimeInstallWorker.enqueueRemote(getApplication())
    }

    /** Manifest of the runtime packaged inside this APK, or null when none is included. */
    val bundledRuntime: RuntimeManifest? = com.experience.virtualos.work.BundledRuntime.manifest(app)

    fun installBundledRuntime() {
        g.activityLog.info("Runtime", "Installing the runtime included in the app")
        RuntimeInstallWorker.enqueueBundled(getApplication())
    }

    private val _selfTest = MutableStateFlow<String?>(null)
    /** Output of the last manual self-test, shown in a dialog. */
    val selfTest: StateFlow<String?> = _selfTest.asStateFlow()
    fun dismissSelfTest() { _selfTest.value = null }

    /** Runs QEMU through PRoot from the installed runtime and reports the raw output. */
    fun runSelfTest() = viewModelScope.launch(Dispatchers.IO) {
        _selfTest.value = "Running…"
        _selfTest.value = try {
            val dir = g.runtimeInstaller.currentDirectory() ?: error("No runtime is installed.")
            val launcher = g.prootLauncher()
            val problems = launcher.check()
            if (problems.isNotEmpty()) error(problems.joinToString("\n"))
            val rootfs = File(dir, "rootfs")
            val manifest = g.runtimeInstaller.current()?.manifest ?: error("No runtime is installed.")
            val qemu = "/" + manifest.qemuEntrypoint.removePrefix("rootfs/")
            val (c1, o1) = launcher.run(rootfs, listOf("/bin/sh", "-c", ". /etc/os-release; echo \"\$PRETTY_NAME\"; uname -m; ldd --version 2>/dev/null | head -1"), 60)
            val (c2, o2) = launcher.run(rootfs, listOf(qemu, "--version"), 120)
            val ok = c1 == 0 && c2 == 0 && o2.contains("QEMU emulator version")
            (if (ok) "PASSED\n\n" else "FAILED\n\n") + "Userspace (exit $c1):\n${o1.trim()}\n\nQEMU (exit $c2):\n${o2.trim()}"
        } catch (e: Exception) {
            "FAILED\n\n${e.message ?: e}"
        }.also { g.activityLog.add(if (it.startsWith("PASSED")) com.experience.virtualos.core.log.LogLevel.SUCCESS else com.experience.virtualos.core.log.LogLevel.ERROR, "Runtime", "Self-test: ${it.take(300)}") }
    }

    fun cancelRuntimeInstall() = work.cancelUniqueWork(WorkKeys.RUNTIME_WORK)

    /** Copies a user-picked archive + manifest into private storage and installs them. */
    fun installRuntimeFromFiles(archive: Uri, manifest: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val dir = File(getApplication<Application>().cacheDir, "import").apply { mkdirs() }
            val manifestText = resolver.openInputStream(manifest)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot read manifest")
            val parsed = RuntimeManifest.parse(manifestText)
            ManifestValidator.validate(parsed, com.experience.virtualos.core.runtime.HostConstraints(
                android.os.Build.SUPPORTED_ABIS.toList(), android.os.Build.VERSION.SDK_INT, com.experience.virtualos.BuildConfig.VERSION_CODE, requireGithubHost = false))
            val m = File(dir, "manifest.json").apply { writeText(manifestText) }
            val a = File(dir, "base.tar.gz")
            resolver.openInputStream(archive)?.use { input -> a.outputStream().use { input.copyTo(it, 1 shl 16) } } ?: error("Cannot read archive")
            RuntimeInstallWorker.enqueueLocal(getApplication(), a, m)
        } catch (e: Exception) {
            say("Import failed: ${e.message}")
        }
    }

    fun uninstallRuntime() = viewModelScope.launch(Dispatchers.IO) {
        if (g.vmController.runningDeviceId != null) { say("Stop the running device first."); return@launch }
        g.runtimeInstaller.uninstall()
        g.activityLog.warning("Runtime", "Runtime removed")
        refreshRuntime(); refreshStorage()
    }

    // ---- Images ---------------------------------------------------------
    fun loadCatalog() = viewModelScope.launch(Dispatchers.IO) {
        _catalog.value = CatalogState.Loading
        _catalog.value = try { CatalogState.Loaded(g.imageRepository.fetchCatalog()) } catch (e: Exception) { CatalogState.Error(e.message ?: e.toString()) }
    }

    fun installImage(image: CatalogImage) {
        g.activityLog.info("Images", "Downloading ${image.title} (${image.tag}, ${FileUtils.formatBytes(image.sizeBytes)})")
        ImageInstallWorker.enqueue(getApplication(), image)
    }

    fun cancelImage(id: String) = work.cancelUniqueWork(WorkKeys.imageWork(id))

    fun deleteImage(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val inUse = devices.value.filter { it.systemImageId == id }
        if (inUse.isNotEmpty()) { say("Used by ${inUse.joinToString { it.name }}. Delete or reconfigure those devices first."); return@launch }
        g.imageRepository.delete(id)
        g.activityLog.info("Images", "Deleted image $id")
        refreshImages(); refreshStorage()
    }

    // ---- Devices --------------------------------------------------------
    fun saveDevice(device: VirtualDevice, isNew: Boolean, onDone: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val saved = if (isNew) g.deviceStore.create(device) else g.deviceStore.update(device)
            g.activityLog.info("Device", "${if (isNew) "Created" else "Updated"} ${saved.name}")
            withContext(Dispatchers.Main) { onDone(saved.id) }
        } catch (e: Exception) {
            say(e.message ?: "Could not save the device.")
        }
    }

    fun duplicateDevice(id: String, includeDisks: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val copy = g.deviceStore.duplicate(id, includeDisks)
            g.activityLog.info("Device", "Duplicated as ${copy.name}")
            say("Created ${copy.name}")
            refreshStorage()
        } catch (e: Exception) { say(e.message ?: "Duplicate failed") }
    }

    fun deleteDevice(id: String) = viewModelScope.launch(Dispatchers.IO) {
        if (g.vmController.runningDeviceId == id) { say("Stop the device before deleting it."); return@launch }
        val name = g.deviceStore.get(id)?.name
        g.deviceStore.delete(id)
        g.activityLog.warning("Device", "Deleted $name")
        refreshStorage()
    }

    fun exportDevice(id: String, target: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = g.deviceStore.export(id)
            getApplication<Application>().contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) }
            say("Configuration exported")
        } catch (e: Exception) { say("Export failed: ${e.message}") }
    }

    fun importDevice(source: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("unreadable")
            if (text.length > 64_000) error("file too large")
            val d = g.deviceStore.import(text)
            g.activityLog.info("Device", "Imported ${d.name}")
            say("Imported ${d.name}")
        } catch (e: Exception) { say(e.message ?: "Import failed") }
    }

    /** Copies installation media (ISO) chosen by the user into the device directory. */
    fun attachMedia(id: String, source: Uri, onProgress: (Long) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val device = g.deviceStore.get(id) ?: return@launch
            if (device.kind != GuestKind.LINUX_ARM64) return@launch
            val target = File(g.deviceStore.directory(id), "media.iso")
            val tmp = File(target.parentFile, "media.iso.part")
            getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16); var total = 0L
                    while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); total += n; onProgress(total) }
                }
            } ?: error("unreadable file")
            if (!tmp.renameTo(target)) error("could not store media")
            g.deviceStore.update(device.copy(installMedia = target.name, bootFromMedia = true))
            g.activityLog.info("Device", "Attached ${FileUtils.formatBytes(target.length())} installation media to ${device.name}")
            say("Installation media attached")
            refreshStorage()
        } catch (e: Exception) { say("Could not attach media: ${e.message}") }
    }

    fun detachMedia(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val device = g.deviceStore.get(id) ?: return@launch
        File(g.deviceStore.directory(id), "media.iso").delete()
        g.deviceStore.update(device.copy(installMedia = null))
        refreshStorage()
    }

    fun resetDisks(id: String) = viewModelScope.launch(Dispatchers.IO) {
        if (g.vmController.runningDeviceId == id) { say("Stop the device first."); return@launch }
        val dir = g.deviceStore.directory(id)
        listOf("disk.qcow2", "efivars.fd", "userdata.img", "metadata.img", "sdcard.img", "cache.img").forEach { File(dir, it).delete() }
        g.activityLog.warning("Device", "Erased disks of ${g.deviceStore.get(id)?.name}")
        say("Disks erased. They will be recreated on next start.")
        refreshStorage()
    }

    fun startDevice(id: String) = viewModelScope.launch(Dispatchers.IO) {
        try { g.vmController.start(id) } catch (e: Exception) { /* status + activity log carry the error */ }
    }

    fun stopDevice(id: String, force: Boolean = false) = viewModelScope.launch(Dispatchers.IO) { g.vmController.stop(id, force) }

    fun readLog(file: File, max: Int = 48_000): String = FileUtils.readTail(file, max)

    fun deviceFiles(id: String) = Triple(g.vmController.serialLog(id), g.vmController.qemuLog(id), g.vmController.launchFile(id))

    fun clearActivity() = g.activityLog.clear()

    /** Plain-text diagnostics report suitable for sharing with developers. */
    fun diagnosticsReport(): String {
        val h = host.value
        val r = runtime.value
        return buildString {
            appendLine("Experience Virtual OS ${com.experience.virtualos.BuildConfig.VERSION_NAME} (${com.experience.virtualos.BuildConfig.VERSION_CODE})")
            appendLine("Device: ${h.manufacturer} ${h.model} · Android ${h.androidRelease} (API ${h.sdkInt}) · ${h.socHint ?: "unknown SoC"}")
            appendLine("ABIs: ${h.supportedAbis.joinToString()} · cores ${h.cpuCores}")
            appendLine("RAM: ${FileUtils.formatBytes(h.availableMemoryBytes)} free of ${FileUtils.formatBytes(h.totalMemoryBytes)}")
            appendLine("Storage: ${FileUtils.formatBytes(h.storageFreeBytes)} free of ${FileUtils.formatBytes(h.storageTotalBytes)}")
            appendLine("KVM: ${h.kvm.label}")
            appendLine("Runtime: ${r.installed?.let { "${it.runtimeVersion} (${it.archiveSha256.take(16)}…, ${it.source})" } ?: "not installed"}")
            r.installed?.manifest?.components?.forEach { (k, v) -> appendLine("  $k: $v") }
            if (r.problems.isNotEmpty()) appendLine("Runtime problems: ${r.problems.joinToString()}")
            if (r.prootProblems.isNotEmpty()) appendLine("PRoot problems: ${r.prootProblems.joinToString()}")
            appendLine("Devices: ${devices.value.size} · images: ${installedImages.value.size}")
            appendLine("Settings: ${settings.value}")
            appendLine()
            appendLine("Recent activity:")
            activity.value.take(30).forEach { appendLine("  ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(it.timeMillis)} ${it.level} ${it.category}: ${it.message}") }
        }
    }

    private fun WorkInfo.toProgress() = TaskProgress(
        running = !state.isFinished,
        stage = progress.getString(WorkKeys.STAGE) ?: when (state) {
            WorkInfo.State.ENQUEUED -> "Waiting to start…"
            WorkInfo.State.BLOCKED -> "Waiting…"
            WorkInfo.State.SUCCEEDED -> "Completed"
            WorkInfo.State.FAILED -> "Failed"
            WorkInfo.State.CANCELLED -> "Cancelled"
            else -> "Working…"
        },
        done = progress.getLong(WorkKeys.DONE, 0),
        total = progress.getLong(WorkKeys.TOTAL, 0),
        error = outputData.getString(WorkKeys.ERROR),
        succeeded = state == WorkInfo.State.SUCCEEDED,
    )
}
