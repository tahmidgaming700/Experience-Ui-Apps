package com.experience.virtualos.debug

import android.app.Instrumentation
import android.os.Bundle
import com.experience.virtualos.core.engine.NativeEngine
import java.io.File

/**
 * Debug-only on-device check of the native engine from inside the app
 * process, which (unlike `adb shell`) runs under the app uid and Android's
 * app seccomp filter. It drives the same [NativeEngine] calls as the UI:
 *
 *   adb shell am instrument -w -e rootfs /path/to/rootfs [-e name ubuntu] [-e platform ptrace] \
 *       com.experience.virtualos/.debug.EngineProbe
 *
 * The rootfs is copied into app storage first (the engine's overlay needs a
 * writable root directory owned by the app).
 */
class EngineProbe : Instrumentation() {
    private lateinit var args: Bundle

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        args = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        val report = StringBuilder()
        fun log(s: String) { report.append(s).append('\n'); sendStatus(1, Bundle().apply { putString("stream", s + "\n") }) }
        var ok = false
        try {
            val ctx = targetContext
            val platform = args.getString("platform") ?: "systrap"
            log("probe: uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()} platform=$platform")
            log("probe: " + File("/proc/self/status").readLines().filter { it.startsWith("Seccomp") || it.startsWith("NoNewPrivs") }.joinToString(" "))
            val src = File(requireNotNull(args.getString("rootfs")) { "pass -e rootfs <dir>" })
            val rootfs = File(ctx.filesDir, "probe-rootfs-" + (args.getString("name") ?: "default"))
            if (!File(rootfs, "bin").exists()) {
                rootfs.deleteRecursively(); rootfs.mkdirs()
                // -RP: recurse, keep symlinks; owners become the app uid.
                val t0 = System.nanoTime()
                val cp = ProcessBuilder("/system/bin/cp", "-RP", "${src.path}/.", rootfs.path).redirectErrorStream(true).start()
                val out = cp.inputStream.bufferedReader().readText()
                check(cp.waitFor() == 0) { "copy failed: $out" }
                log("probe: copied rootfs in ${(System.nanoTime() - t0) / 1_000_000} ms")
            }
            val engine = NativeEngine(File(ctx.applicationInfo.nativeLibraryDir), File(ctx.filesDir, "engine"), File(ctx.cacheDir, "engine"), { platform })
            log("probe: engine problems=${engine.check()}")

            var t0 = System.nanoTime()
            log(engine.selfTest(rootfs, File(ctx.cacheDir, "probe")))
            log("probe: self-test ${(System.nanoTime() - t0) / 1_000_000} ms")

            val bundle = File(ctx.filesDir, "probe-bundle")
            engine.writeBundle(bundle, rootfs, File(ctx.filesDir, "probe-home"), "evos-probe", listOf("/bin/sh", "-c", "while :; do sleep 3600; done"))
            t0 = System.nanoTime()
            engine.start("evos-probe", bundle)
            log("probe: detached start ${(System.nanoTime() - t0) / 1_000_000} ms, running=${engine.isRunning("evos-probe")}")
            t0 = System.nanoTime()
            val (code, out) = engine.exec("evos-probe", "echo EXEC_OK; echo hi > /root/persist.txt; cat /root/persist.txt; ls / | head -3", 300)
            log("probe: exec exit=$code ${(System.nanoTime() - t0) / 1_000_000} ms\n$out")
            engine.stop("evos-probe")
            val persisted = File(ctx.filesDir, "probe-home/persist.txt").takeIf { it.isFile }?.readText()?.trim()
            log("probe: after stop running=${engine.isRunning("evos-probe")} persisted=$persisted")
            ok = code == 0 && out.contains("EXEC_OK") && persisted == "hi"
        } catch (t: Throwable) {
            log("probe: FAILED ${t.javaClass.simpleName}: ${t.message}")
        }
        log(if (ok) "ENGINE_PROBE_PASS" else "ENGINE_PROBE_FAIL")
        finish(if (ok) 0 else 1, Bundle().apply { putString("stream", if (ok) "ENGINE_PROBE_PASS\n" else "ENGINE_PROBE_FAIL\n") })
    }
}
