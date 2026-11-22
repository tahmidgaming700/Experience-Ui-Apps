package com.experience.virtualos.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Native engine (experimental): runs a Linux userspace directly on the host
 * CPU under gVisor's user-space kernel, instead of emulating a machine with
 * QEMU. This is the same architecture AndVM uses for Android guests, built
 * from open source: gVisor (Apache-2.0) with this project's `--android-host`
 * patches (patches/gvisor), which let it run inside an unprivileged Android
 * app process on kernels without user namespaces (Android 9+, Linux 4.9+).
 *
 * Binaries ship in the APK as native libraries (the only place Android lets
 * an app execute its own code). runsc finds its sidecars by fixed names, so
 * [binDir] holds correctly named symlinks to them.
 */
class NativeEngine(
    private val nativeLibraryDir: File,
    private val engineDir: File,
    private val tmpDir: File,
    private val platform: () -> String = { "systrap" },
) {
    val runsc = File(nativeLibraryDir, "libevos_runsc.so")
    private val sidecars = mapOf(
        "gvisor_sentry" to "libevos_gvisor_sentry.so",
        "gvisor-sentry-prewarmer" to "libevos_gvisor_prewarmer.so",
        "runsc-fd-parking" to "libevos_gvisor_fdparking.so",
        "gvisor-proc-umounter" to "libevos_gvisor_procumounter.so",
    )
    private val binDir = File(engineDir, "bin")
    private val stateDir = File(engineDir, "state")
    val logDir = File(engineDir, "logs")

    /** Problems preventing the engine from running (empty when usable). */
    fun check(): List<String> = buildList {
        if (!runsc.isFile) add("The native engine is not included in this APK build.")
        sidecars.values.forEach { if (!File(nativeLibraryDir, it).isFile) add("Missing engine component $it.") }
    }

    val available: Boolean get() = check().isEmpty()

    private fun prepare() {
        val problems = check()
        if (problems.isNotEmpty()) throw IOException(problems.joinToString(" "))
        listOf(binDir, stateDir, logDir, tmpDir).forEach { if (!it.isDirectory && !it.mkdirs()) throw IOException("Cannot create $it") }
        for ((name, lib) in sidecars) {
            val link = File(binDir, name).toPath()
            val target = File(nativeLibraryDir, lib).toPath()
            // nativeLibraryDir changes when the app is updated: refresh links.
            if (Files.isSymbolicLink(link) && Files.readSymbolicLink(link) == target) continue
            Files.deleteIfExists(link)
            Files.createSymbolicLink(link, target)
        }
    }

    private fun baseCommand(debugName: String?): List<String> = buildList {
        add(runsc.absolutePath)
        add("--root=${stateDir.absolutePath}")
        add("--android-host")
        add("--network=host")
        add("--ignore-cgroups")
        add("--platform=${platform()}")
        if (debugName != null) {
            add("--debug")
            add("--debug-log=${File(logDir, debugName).absolutePath}/")
        }
    }

    private fun processBuilder(args: List<String>): ProcessBuilder =
        ProcessBuilder(args).redirectErrorStream(true).also { pb ->
            pb.environment().apply {
                clear()
                put("PATH", "/system/bin")
                put("HOME", engineDir.absolutePath)
                put("TMPDIR", tmpDir.absolutePath)
                put("GVISOR_SIDECAR_BINARIES_DIR", binDir.absolutePath)
            }
            pb.directory(engineDir)
        }

    /**
     * Runs runsc with [args] and returns (exit code, combined output).
     *
     * With [outputFile], output goes to that file instead of a pipe. Use it
     * for `run --detach`: the sandbox inherits runsc's stdio and outlives it,
     * so a pipe would never reach end-of-file.
     */
    fun runsc(args: List<String>, timeoutSeconds: Long = 120, debugName: String? = null, outputFile: File? = null): Pair<Int, String> {
        prepare()
        val pb = processBuilder(baseCommand(debugName) + args).redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        if (outputFile != null) {
            outputFile.parentFile?.mkdirs()
            pb.redirectOutput(outputFile)
            val p = pb.start()
            val finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            val text = outputFile.takeIf { it.isFile }?.readText()?.takeLast(64_000).orEmpty()
            if (!finished) throw IOException("Native engine timed out after ${timeoutSeconds}s.\n$text")
            return p.exitValue() to text
        }
        val p = pb.start()
        val out = StringBuilder()
        val reader = Thread {
            p.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (out.length < 64_000) out.append(it).append('\n') } }
        }.apply { start() }
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            reader.join(1000)
            throw IOException("Native engine timed out after ${timeoutSeconds}s.\n$out")
        }
        reader.join(2000)
        return p.exitValue() to out.toString()
    }

    /**
     * Writes an OCI bundle for a container whose root is [rootfs] (shared,
     * not modified: writes go to an overlay) and whose /root is the
     * persistent [homeDir].
     */
    fun writeBundle(bundleDir: File, rootfs: File, homeDir: File, hostname: String, args: List<String>) {
        bundleDir.mkdirs(); homeDir.mkdirs()
        val config = bundleConfig(rootfs.canonicalPath, homeDir.canonicalPath, hostname, args)
        File(bundleDir, "config.json").writeText(json.encodeToString(JsonObject.serializer(), config))
    }

    /** Starts [id] in the background; runsc returns once it is running. */
    fun start(id: String, bundleDir: File): String {
        runsc(listOf("delete", "--force", id), 30) // stale state from a crash
        val (code, out) = runsc(listOf("run", "--detach", "--bundle", bundleDir.absolutePath, id), 180, debugName = id,
            outputFile = File(logDir, "$id.start.log"))
        if (code != 0) throw IOException("The native engine could not start the container (exit $code).\n${out.trim()}")
        return out
    }

    /** Runs a shell command inside running container [id]. */
    fun exec(id: String, command: String, timeoutSeconds: Long = 600): Pair<Int, String> =
        runsc(listOf("exec", id, "/bin/sh", "-c", command), timeoutSeconds)

    fun isRunning(id: String): Boolean = try {
        val (code, out) = runsc(listOf("state", id), 30)
        code == 0 && out.contains("\"status\": \"running\"")
    } catch (_: Exception) {
        false
    }

    fun stop(id: String) {
        runsc(listOf("kill", id, "KILL"), 30)
        runsc(listOf("delete", "--force", id), 60)
    }

    /**
     * Self-test: runs a short command in a throwaway container on [rootfs]
     * and returns the full output. Throws if it does not complete.
     */
    fun selfTest(rootfs: File, scratch: File): String {
        val bundle = File(scratch, "selftest-bundle")
        writeBundle(bundle, rootfs, File(scratch, "selftest-home"), "evos-selftest",
            listOf("/bin/sh", "-c", ". /etc/os-release; echo \"ENGINE_OK \$PRETTY_NAME\"; uname -srm; id"))
        runsc(listOf("delete", "--force", "evos-selftest"), 30)
        val (code, out) = runsc(listOf("run", "--bundle", bundle.absolutePath, "evos-selftest"), 180, debugName = "selftest")
        if (code != 0 || !out.contains("ENGINE_OK")) throw IOException("Native engine self-test failed (exit $code).\n${out.trim()}")
        return out.trim()
    }

    companion object {
        private val json = Json { prettyPrint = true }

        /** Default capability set of a container root user (as Docker/runc). */
        val CAPABILITIES = listOf(
            "CAP_CHOWN", "CAP_DAC_OVERRIDE", "CAP_FOWNER", "CAP_FSETID", "CAP_KILL", "CAP_SETGID",
            "CAP_SETUID", "CAP_SETPCAP", "CAP_NET_BIND_SERVICE", "CAP_NET_RAW", "CAP_SYS_CHROOT",
            "CAP_MKNOD", "CAP_AUDIT_WRITE", "CAP_SETFCAP",
        )

        fun bundleConfig(rootfs: String, home: String, hostname: String, args: List<String>): JsonObject = buildJsonObject {
            put("ociVersion", "1.0.0")
            putJsonObject("process") {
                put("terminal", false)
                putJsonObject("user") { put("uid", 0); put("gid", 0) }
                put("args", JsonArray(args.map { JsonPrimitive(it) }))
                putJsonArray("env") {
                    add(JsonPrimitive("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"))
                    add(JsonPrimitive("HOME=/root"))
                    add(JsonPrimitive("LANG=C.UTF-8"))
                    add(JsonPrimitive("TERM=dumb"))
                    add(JsonPrimitive("DEBIAN_FRONTEND=noninteractive"))
                }
                put("cwd", "/root")
                putJsonObject("capabilities") {
                    for (set in listOf("bounding", "effective", "permitted")) put(set, JsonArray(CAPABILITIES.map { JsonPrimitive(it) }))
                }
            }
            putJsonObject("root") { put("path", rootfs); put("readonly", false) }
            put("hostname", hostname)
            putJsonArray("mounts") {
                add(buildJsonObject { put("destination", "/proc"); put("type", "proc"); put("source", "proc") })
                add(buildJsonObject { put("destination", "/tmp"); put("type", "tmpfs"); put("source", "tmpfs") })
                add(buildJsonObject {
                    put("destination", "/root"); put("type", "bind"); put("source", home)
                    put("options", JsonArray(listOf("rbind", "rw").map { JsonPrimitive(it) }))
                })
            }
            putJsonObject("linux") { put("namespaces", JsonArray(emptyList())) }
        }
    }
}
