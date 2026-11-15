package com.experience.virtualos.core.proot

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Builds and starts processes inside the Alpine runtime using the Bionic PRoot
 * shipped in the APK's native library directory.
 *
 * Host side (Android/Bionic):   nativeLibraryDir/libproot.so (+ loader, talloc)
 * Guest side (Linux/musl):      <runtime>/rootfs/...
 *
 * App storage is bound into the guest at [GUEST_DATA], so QEMU can open
 * device disks and images with stable guest paths.
 */
class ProotLauncher(
    private val nativeLibraryDir: File,
    private val tmpDir: File,
    private val hostDataDir: File,
    private val disableSeccomp: Boolean = false,
) {
    val proot = File(nativeLibraryDir, "libproot.so")
    val loader = File(nativeLibraryDir, "libproot-loader.so")

    /** Problems that prevent PRoot from running at all (empty when usable). */
    fun check(): List<String> = buildList {
        if (!proot.isFile) add("PRoot is missing from this APK build (libproot.so). Install an official build.")
        if (!loader.isFile) add("PRoot loader is missing from this APK build (libproot-loader.so).")
        if (!File(nativeLibraryDir, "libtalloc.so").isFile) add("libtalloc.so is missing from this APK build.")
    }

    fun command(rootfs: File, argv: List<String>, workDir: String = "/root"): List<String> = buildList {
        add(proot.absolutePath)
        add("--kill-on-exit")
        add("--link2symlink")
        add("-0")
        add("-r"); add(rootfs.absolutePath)
        add("-b"); add("/dev")
        add("-b"); add("/proc")
        add("-b"); add("/sys")
        add("-b"); add("/dev/urandom:/dev/random")
        add("-b"); add("${hostDataDir.absolutePath}:$GUEST_DATA")
        add("-w"); add(workDir)
        add("/usr/bin/env"); add("-i")
        add("HOME=/root")
        add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        add("LANG=C.UTF-8")
        add("TERM=dumb")
        add("TMPDIR=/tmp")
        addAll(argv)
    }

    fun environment(): Map<String, String> = buildMap {
        put("LD_LIBRARY_PATH", nativeLibraryDir.absolutePath)
        put("PROOT_LOADER", loader.absolutePath)
        put("PROOT_TMP_DIR", tmpDir.absolutePath)
        if (disableSeccomp) put("PROOT_NO_SECCOMP", "1")
    }

    fun start(rootfs: File, argv: List<String>, log: File?): Process {
        val problems = check()
        if (problems.isNotEmpty()) throw IOException(problems.joinToString(" "))
        if (!tmpDir.isDirectory && !tmpDir.mkdirs()) throw IOException("Cannot create $tmpDir")
        val builder = ProcessBuilder(command(rootfs, argv)).redirectErrorStream(true)
        builder.environment().apply {
            clear()
            put("PATH", "/system/bin")
            put("ANDROID_ROOT", "/system")
            put("ANDROID_DATA", "/data")
            putAll(environment())
        }
        if (log != null) {
            log.parentFile?.mkdirs()
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        }
        return builder.start()
    }

    /** Runs a short command to completion and returns (exitCode, output). */
    fun run(rootfs: File, argv: List<String>, timeoutSeconds: Long = 60): Pair<Int, String> {
        val p = start(rootfs, argv, null)
        val output = StringBuilder()
        val reader = Thread {
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { if (output.length < 32_000) output.append(it).append('\n') }
            }
        }.apply { start() }
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            reader.join(1000)
            throw IOException("Command timed out after ${timeoutSeconds}s: ${argv.joinToString(" ")}\n$output")
        }
        reader.join(2000)
        return p.exitValue() to output.toString()
    }

    companion object {
        const val GUEST_DATA = "/data/evos"

        /**
         * Executes QEMU from a (staged) runtime through PRoot. Proves the loader,
         * libraries and permissions work on this device, not just that files exist.
         */
        fun selfTest(launcher: ProotLauncher, runtimeDir: File, manifest: com.experience.virtualos.core.runtime.RuntimeManifest) {
            val problems = launcher.check()
            if (problems.isNotEmpty()) throw IOException(problems.joinToString(" "))
            val qemu = "/" + manifest.qemuEntrypoint.removePrefix("rootfs/")
            val (code, out) = launcher.run(File(runtimeDir, "rootfs"), listOf(qemu, "--version"), 120)
            if (code != 0 || !out.contains("QEMU emulator version")) {
                throw IOException("Runtime self-test failed (exit $code): ${out.take(1500)}")
            }
        }

        /** Maps a file in app storage to the path QEMU sees inside the guest. */
        fun guestPath(hostDataDir: File, file: File): String {
            val base = hostDataDir.absolutePath.trimEnd('/')
            val abs = file.absolutePath
            require(abs.startsWith("$base/")) { "$file is outside app data" }
            return GUEST_DATA + abs.substring(base.length)
        }
    }
}
