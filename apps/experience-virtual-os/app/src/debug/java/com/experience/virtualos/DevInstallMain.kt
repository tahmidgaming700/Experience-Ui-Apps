package com.experience.virtualos

import com.experience.virtualos.core.TarExtractor
import com.experience.virtualos.core.proot.ProotLauncher
import com.experience.virtualos.core.runtime.HostConstraints
import com.experience.virtualos.core.runtime.HttpDownloader
import com.experience.virtualos.core.runtime.RuntimeInstaller
import com.experience.virtualos.core.runtime.RuntimeLayout
import com.experience.virtualos.core.runtime.RuntimeManifest
import com.experience.virtualos.core.vm.GuestKind
import com.experience.virtualos.core.vm.QemuCommandBuilder
import com.experience.virtualos.core.vm.VirtualDevice
import java.io.File

/**
 * Debug-only harness that runs the app's real installer and PRoot code under
 * ART without the UI, e.g. on a device or test system:
 *
 *   CLASSPATH=app-debug.apk app_process /system/bin com.experience.virtualos.DevInstallMain \
 *       <base.tar.gz> <manifest.json> <dataDir> <nativeLibDir>
 */
object DevInstallMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val (archive, manifestFile, dataDirPath, libDir) = args
        val dataDir = File(dataDirPath).apply { mkdirs() }
        val launcher = ProotLauncher(File(libDir), File(dataDir, "proot-tmp"), dataDir)
        val installer = RuntimeInstaller(
            RuntimeLayout(File(dataDir, "runtime")),
            HttpDownloader("evos-dev"),
            HostConstraints(android.os.Build.SUPPORTED_ABIS.toList(), android.os.Build.VERSION.SDK_INT, BuildConfig.VERSION_CODE),
            smokeTest = { dir, m -> println("self-test in $dir"); ProotLauncher.selfTest(launcher, dir, m); println("self-test OK") },
        )
        println("Android API ${android.os.Build.VERSION.SDK_INT}, ABIs ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        var last = ""
        val result = installer.installFromFile(File(archive), RuntimeManifest.parse(File(manifestFile).readText())) { stage ->
            val name = stage.javaClass.simpleName
            if (name != last) { println("stage: $name"); last = name }
        }
        println("INSTALLED ${result.runtimeVersion} sha=${result.archiveSha256.take(16)} problems=${installer.verify()}")

        // Start a Linux device exactly as VmController would (no media), check QEMU stays up.
        val runtimeDir = installer.currentDirectory()!!
        val rootfs = File(runtimeDir, "rootfs")
        val dev = File(dataDir, "devices/dev1").apply { mkdirs() }
        val disk = File(dev, "disk.qcow2")
        val (c1, o1) = launcher.run(rootfs, listOf("/usr/bin/qemu-img", "create", "-f", "qcow2", ProotLauncher.guestPath(dataDir, disk), "2G"))
        println("qemu-img exit=$c1 ${o1.trim()}")
        File(runtimeDir, installer.current()!!.manifest.entrypoints.getValue("uefiVars")).copyTo(File(dev, "efivars.fd"), overwrite = true)
        val device = VirtualDevice(id = "dev1", name = "Test", kind = GuestKind.LINUX_ARM64, memoryMb = 512, cpuCores = 1, createdAtMillis = 0, updatedAtMillis = 0)
        val argv = QemuCommandBuilder.build(device, QemuCommandBuilder.Paths(
            qemu = "/usr/bin/qemu-system-aarch64", firmwareCode = "/" + installer.current()!!.manifest.entrypoints.getValue("uefiFirmware").removePrefix("rootfs/"),
            firmwareVars = ProotLauncher.guestPath(dataDir, File(dev, "efivars.fd")), systemDisk = ProotLauncher.guestPath(dataDir, disk),
            installMedia = null, serialLog = ProotLauncher.guestPath(dataDir, File(dev, "serial.log")),
        ), QemuCommandBuilder.Ports(17, 44417), useKvm = false, tcgThreads = 1)
        val log = File(dev, "qemu.log")
        val p = launcher.start(rootfs, argv, log)
        Thread.sleep(40_000)
        val alive = p.isAlive
        var banner = ""
        try { java.net.Socket("127.0.0.1", 5917).use { s -> s.soTimeout = 5000; val b = ByteArray(12); s.getInputStream().read(b); banner = String(b) } } catch (e: Exception) { banner = "error: ${e.message}" }
        println("QEMU alive after 40s=$alive VNC banner='${banner.trim()}'")
        println("serial: " + File(dev, "serial.log").takeIf { it.exists() }?.readText()?.replace(Regex("\\u001b\\[[0-9;=?]*[A-Za-z]"), "")?.take(600))
        println("qemu.log: " + log.readText().take(600))
        p.destroy()
        println("DONE")
        @Suppress("UNUSED_VARIABLE") val unused = TarExtractor::class
    }
}
