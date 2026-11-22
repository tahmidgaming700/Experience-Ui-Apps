package com.experience.virtualos.core.vm

import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.host.HostInfo
import com.experience.virtualos.core.images.ImageRepository
import com.experience.virtualos.core.log.ActivityLog
import com.experience.virtualos.core.proot.ProotLauncher
import com.experience.virtualos.core.runtime.RuntimeInstaller
import com.experience.virtualos.core.runtime.RuntimeManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

enum class VmPhase { STOPPED, PREPARING, RUNNING, STOPPING, FAILED }

/**
 * Status of one virtual device. RUNNING means the QEMU process is alive; it
 * says nothing about whether the guest OS has finished booting, which the UI
 * must not claim. The serial console is the source of truth for guest state.
 */
data class VmStatus(
    val phase: VmPhase,
    val message: String = "",
    val vncPort: Int = 0,
    val accelerator: String = "",
    val startedAtMillis: Long = 0,
)

/**
 * Owns QEMU processes. Only one device runs at a time: TCG guests are CPU and
 * memory heavy, and phones such as the Huawei Y9 2019 have 3–4 GB of RAM.
 */
class VmController(
    private val dataDir: File,
    private val devices: DeviceStore,
    private val images: ImageRepository,
    private val runtime: RuntimeInstaller,
    private val launcher: () -> ProotLauncher,
    private val host: () -> HostInfo,
    private val log: ActivityLog,
    private val tcgThreads: () -> Int,
    private val onRunningChanged: (Boolean) -> Unit,
    private val engine: () -> com.experience.virtualos.core.engine.NativeEngine,
) {
    private val _status = MutableStateFlow<Map<String, VmStatus>>(emptyMap())
    val status: StateFlow<Map<String, VmStatus>> = _status.asStateFlow()

    /** A running device: a QEMU process, or a native-engine container ([containerId]). */
    private data class Session(
        val deviceId: String,
        val process: Process?,
        val ports: QemuCommandBuilder.Ports,
        val containerId: String? = null,
    ) {
        val alive: Boolean get() = process?.isAlive ?: true
    }
    @Volatile private var session: Session? = null

    fun statusOf(id: String): VmStatus = _status.value[id] ?: VmStatus(VmPhase.STOPPED)
    val runningDeviceId: String? get() = session?.takeIf { it.alive }?.deviceId

    fun qemuLog(id: String) = File(devices.directory(id), "qemu.log")
    fun serialLog(id: String) = File(devices.directory(id), "serial.log")
    fun launchFile(id: String) = File(devices.directory(id), "launch.txt")

    /** Blocking; call from a background thread. */
    fun start(id: String) {
        val device = devices.get(id) ?: throw IOException("Device not found.")
        session?.let { if (it.alive) throw IOException("Another virtual device is already running. Stop it first.") }
        set(id, VmStatus(VmPhase.PREPARING, "Checking runtime…"))
        try {
            val installed = runtime.current() ?: throw IOException("Install the runtime before starting a device.")
            val runtimeDir = runtime.currentDirectory() ?: throw IOException("The runtime directory is missing. Repair the runtime.")
            val problems = runtime.verify()
            if (problems.isNotEmpty()) throw IOException("Runtime check failed: ${problems.joinToString()}")
            if (device.kind == GuestKind.LINUX_CONTAINER) {
                startContainer(device, File(runtimeDir, "rootfs"))
                return
            }
            val proot = launcher()
            proot.check().takeIf { it.isNotEmpty() }?.let { throw IOException(it.joinToString(" ")) }
            val rootfs = File(runtimeDir, "rootfs")
            val hostInfo = host()
            val budget = hostInfo.availableMemoryBytes / (1024 * 1024)
            if (device.memoryMb > budget - 256) {
                throw IOException("Not enough free RAM: the device needs ${device.memoryMb} MB but only $budget MB is available. Close other apps or lower the device memory.")
            }

            val dir = devices.directory(id).apply { mkdirs() }
            val manifest = installed.manifest
            fun guest(f: File) = ProotLauncher.guestPath(dataDir, f)
            fun runtimeGuest(entry: String) = "/" + manifest.entrypoints.getValue(entry).removePrefix("rootfs/")

            set(id, VmStatus(VmPhase.PREPARING, "Preparing disks…"))
            val paths: QemuCommandBuilder.Paths
            var layout: com.experience.virtualos.core.images.ImageLayout? = null
            when (device.kind) {
                GuestKind.LINUX_ARM64 -> {
                    val disk = File(dir, "disk.qcow2")
                    if (!disk.isFile) {
                        val (code, out) = proot.run(rootfs, listOf(runtimeGuest(RuntimeManifest.ENTRY_QEMU_IMG), "create", "-f", "qcow2", guest(disk), "${device.diskSizeGb}G"))
                        if (code != 0 || !disk.isFile) throw IOException("qemu-img could not create the disk: $out")
                        log.info("Device", "Created ${device.diskSizeGb} GB disk for ${device.name}")
                    }
                    val vars = File(dir, "efivars.fd")
                    if (!vars.isFile) {
                        val template = File(runtimeDir, manifest.entrypoints["uefiVars"] ?: throw IOException("This runtime has no UEFI variable store; update the runtime."))
                        if (!template.isFile) throw IOException("UEFI variable store template is missing from the runtime.")
                        FileUtils.copy(template, vars)
                    }
                    val media = device.installMedia?.let { File(dir, it) }?.takeIf { it.isFile }
                    paths = QemuCommandBuilder.Paths(
                        qemu = runtimeGuest(RuntimeManifest.ENTRY_QEMU),
                        firmwareCode = runtimeGuest(RuntimeManifest.ENTRY_UEFI),
                        firmwareVars = guest(vars),
                        systemDisk = guest(disk),
                        installMedia = media?.let { guest(it) },
                        serialLog = guest(serialLog(id)),
                    )
                }
                GuestKind.LINUX_CONTAINER -> error("handled above")
                GuestKind.ANDROID_AOSP -> {
                    val imageId = device.systemImageId ?: throw IOException("No Android system image selected.")
                    val image = images.installedImage(imageId) ?: throw IOException("The selected Android image is not installed.")
                    layout = image.layout
                    val imageDir = images.directory(imageId)
                    val userdata = File(dir, "userdata.img")
                    if (!userdata.isFile) {
                        val mke2fs = manifest.entrypoints["mke2fs"] ?: throw IOException("This runtime has no mke2fs; update the runtime to create Android data disks.")
                        sparse(userdata, device.diskSizeGb * 1024L * 1024 * 1024)
                        val (code, out) = proot.run(rootfs, listOf("/" + mke2fs.removePrefix("rootfs/"), "-q", "-F", "-t", "ext4", "-L", "data", "-O", "quota,verity", guest(userdata)), 300)
                        if (code != 0) { userdata.delete(); throw IOException("mke2fs failed: $out") }
                    }
                    val metadata = File(dir, "metadata.img")
                    if (!metadata.isFile) FileUtils.copy(File(imageDir, image.layout.metadataTemplate), metadata)
                    val sdcard = File(dir, "sdcard.img").also { if (!it.isFile) sparse(it, 64L shl 20) }
                    val cache = File(dir, "cache.img").also { if (!it.isFile) sparse(it, 64L shl 20) }
                    paths = QemuCommandBuilder.Paths(
                        qemu = runtimeGuest(RuntimeManifest.ENTRY_QEMU),
                        firmwareCode = runtimeGuest(RuntimeManifest.ENTRY_UEFI),
                        firmwareVars = null,
                        systemDisk = guest(userdata),
                        installMedia = null,
                        serialLog = guest(serialLog(id)),
                        androidImageDir = guest(imageDir),
                        androidMetadata = guest(metadata),
                        androidSdcard = guest(sdcard),
                        androidCache = guest(cache),
                    )
                }
            }

            val ports = QemuCommandBuilder.Ports(vncDisplay = VNC_DISPLAY, qmpPort = QMP_PORT)
            val kvm = hostInfo.kvm == HostInfo.KvmStatus.ACCESSIBLE
            val threads = tcgThreads().takeIf { it > 0 } ?: hostInfo.cpuCores
            rotate(qemuLog(id)); rotate(serialLog(id))
            var accel = if (kvm) "KVM" else "TCG"
            var process = launch(proot, rootfs, device, paths, ports, kvm, threads, layout, id)
            if (kvm && process.waitForExit(6000)) {
                log.warning("Device", "KVM was refused for ${device.name}; retrying with TCG software emulation.")
                accel = "TCG"
                process = launch(proot, rootfs, device, paths, ports, false, threads, layout, id)
            }
            if (process.waitForExit(4000)) {
                throw IOException("QEMU exited immediately (code ${process.exitValue()}).\n" + FileUtils.readTail(qemuLog(id), 4000).trim())
            }
            session = Session(id, process, ports)
            devices.markStarted(id)
            set(id, VmStatus(VmPhase.RUNNING, "QEMU process running ($accel). Guest boot progress is shown in the console.", 5900 + ports.vncDisplay, accel, System.currentTimeMillis()))
            log.success("Device", "Started ${device.name} with $accel")
            onRunningChanged(true)
            Thread({ watch(id, device.name, process) }, "evos-vm-watch").start()
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            set(id, VmStatus(VmPhase.FAILED, message))
            log.error("Device", "${device.name} could not start: ${message.lineSequence().first()}")
            throw e
        }
    }

    private fun launch(
        proot: ProotLauncher, rootfs: File, device: VirtualDevice, paths: QemuCommandBuilder.Paths,
        ports: QemuCommandBuilder.Ports, kvm: Boolean, threads: Int,
        layout: com.experience.virtualos.core.images.ImageLayout?, id: String,
    ): Process {
        val argv = QemuCommandBuilder.build(device, paths, ports, kvm, threads, layout)
        FileUtils.writeAtomically(launchFile(id), proot.command(rootfs, argv).joinToString(" \\\n  ") + "\n")
        return proot.start(rootfs, argv, qemuLog(id))
    }

    fun consoleLog(id: String) = File(devices.directory(id), "console.log")

    private fun containerId(id: String) = "evos-" + id.take(18)

    private fun startContainer(device: VirtualDevice, rootfs: File) {
        val id = device.id
        val engine = engine()
        engine.check().takeIf { it.isNotEmpty() }?.let { throw IOException(it.joinToString(" ")) }
        set(id, VmStatus(VmPhase.PREPARING, "Starting the native engine…"))
        val dir = devices.directory(id).apply { mkdirs() }
        val bundle = File(dir, "bundle")
        // PID 1 keeps the container alive; commands run beside it with exec.
        engine.writeBundle(bundle, rootfs, File(dir, "home"), sanitizeHostname(device.name),
            listOf("/bin/sh", "-c", "echo container-ready; while :; do sleep 3600; done"))
        val cid = containerId(id)
        val started = System.currentTimeMillis()
        rotate(consoleLog(id))
        engine.start(cid, bundle)
        val ms = System.currentTimeMillis() - started
        consoleLog(id).appendText("# ${device.name}: native engine (gVisor) container started in $ms ms.\n" +
            "# Files outside /root are reset when the container stops.\n")
        session = Session(id, null, QemuCommandBuilder.Ports(0, 0), cid)
        devices.markStarted(id)
        set(id, VmStatus(VmPhase.RUNNING, "Container running natively (gVisor) — started in $ms ms. Use the console to run commands.", 0, "native", System.currentTimeMillis()))
        log.success("Device", "Started ${device.name} with the native engine in $ms ms")
        onRunningChanged(true)
        Thread({ watchContainer(id, device.name, cid) }, "evos-container-watch").start()
    }

    private fun watchContainer(id: String, name: String, cid: String) {
        val engine = engine()
        while (true) {
            try { Thread.sleep(3000) } catch (_: InterruptedException) { return }
            if (session?.containerId != cid) return
            if (!engine.isRunning(cid)) break
        }
        val wasStopping = statusOf(id).phase == VmPhase.STOPPING
        if (session?.containerId == cid) session = null
        set(id, VmStatus(if (wasStopping) VmPhase.STOPPED else VmPhase.FAILED, if (wasStopping) "Stopped" else "The container exited unexpectedly. See the console and engine logs."))
        if (wasStopping) log.info("Device", "$name stopped") else log.error("Device", "$name: container exited unexpectedly")
        onRunningChanged(false)
    }

    /** Runs [command] in the running container of device [id]; appends to its console log. */
    fun execInContainer(id: String, command: String): Pair<Int, String> {
        val s = session?.takeIf { it.deviceId == id && it.containerId != null } ?: throw IOException("This container is not running.")
        val (code, out) = engine().exec(s.containerId!!, command)
        consoleLog(id).appendText("$ $command\n$out${if (out.endsWith("\n") || out.isEmpty()) "" else "\n"}${if (code != 0) "[exit $code]\n" else ""}")
        return code to out
    }

    private fun sanitizeHostname(name: String) =
        name.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').take(40).ifBlank { "evos" }

    private fun watch(id: String, name: String, process: Process) {
        val code = try { process.waitFor() } catch (_: InterruptedException) { return }
        val wasStopping = statusOf(id).phase == VmPhase.STOPPING
        if (session?.process === process) session = null
        if (wasStopping || code == 0) {
            set(id, VmStatus(VmPhase.STOPPED, "Stopped"))
            log.info("Device", "$name stopped")
        } else {
            val tail = FileUtils.readTail(qemuLog(id), 1500).trim()
            set(id, VmStatus(VmPhase.FAILED, "QEMU exited with code $code. ${tail.lineSequence().lastOrNull().orEmpty()}"))
            log.error("Device", "$name: QEMU exited with code $code")
        }
        onRunningChanged(false)
    }

    /** Graceful ACPI power-down via QMP, then quit, then kill. Blocking. */
    fun stop(id: String, force: Boolean = false) {
        val s = session?.takeIf { it.deviceId == id } ?: run { set(id, VmStatus(VmPhase.STOPPED)); return }
        set(id, statusOf(id).copy(phase = VmPhase.STOPPING, message = "Stopping…"))
        if (s.containerId != null) {
            engine().stop(s.containerId)
            return // watchContainer() records the final state.
        }
        val process = s.process ?: return
        if (!force) {
            try {
                QmpClient(s.ports.qmpPort).connect().use { it.execute("system_powerdown") }
                if (process.waitForExit(20_000)) return
                QmpClient(s.ports.qmpPort).connect().use { it.execute("quit") }
                if (process.waitForExit(5_000)) return
            } catch (_: Exception) {
                // Fall through to termination.
            }
        }
        process.destroy()
        if (!process.waitForExit(3_000)) process.destroyForcibly()
    }

    fun stopAll() {
        session?.let { stop(it.deviceId) }
    }

    private fun set(id: String, status: VmStatus) {
        _status.value = _status.value + (id to status)
    }

    private fun Process.waitForExit(ms: Long): Boolean = waitFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)

    private fun sparse(file: File, size: Long) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(size) }
    }

    private fun rotate(file: File) {
        if (file.isFile) {
            val old = File(file.parentFile, file.name + ".1")
            old.delete()
            file.renameTo(old)
        }
    }

    companion object {
        const val VNC_DISPLAY = 17
        const val QMP_PORT = 44417
    }
}
