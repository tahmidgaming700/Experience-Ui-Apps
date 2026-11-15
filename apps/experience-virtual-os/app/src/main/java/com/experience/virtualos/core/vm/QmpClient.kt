package com.experience.virtualos.core.vm

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/** Minimal QMP client over QEMU's localhost-only TCP monitor. */
class QmpClient(private val port: Int) : AutoCloseable {
    private val socket = Socket()
    private lateinit var reader: BufferedReader
    private lateinit var writer: OutputStreamWriter

    fun connect(timeoutMs: Int = 3000): QmpClient {
        socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
        socket.soTimeout = timeoutMs
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
        val greeting = reader.readLine() ?: error("QMP closed during greeting")
        require(greeting.contains("\"QMP\"")) { "Unexpected QMP greeting" }
        execute("qmp_capabilities")
        return this
    }

    /** Sends a command and returns the raw JSON reply line (skipping async events). */
    fun execute(command: String): String {
        writer.write("{\"execute\":\"$command\"}\n")
        writer.flush()
        while (true) {
            val line = reader.readLine() ?: return ""
            if (line.contains("\"return\"") || line.contains("\"error\"")) return line
        }
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
