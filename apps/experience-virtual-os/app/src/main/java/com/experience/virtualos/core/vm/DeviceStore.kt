package com.experience.virtualos.core.vm

import com.experience.virtualos.core.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Persistent virtual-device database.
 *
 * Configurations live in `<base>/devices.json` (written atomically); each
 * device owns `<base>/<id>/` for its disks, NVRAM and logs. All mutations go
 * through this class, which keeps an in-memory [StateFlow] for the UI.
 */
class DeviceStore(
    private val base: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val index = File(base, "devices.json")
    private val lock = Any()
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<VirtualDevice>> = _devices.asStateFlow()

    fun directory(id: String): File {
        require(id.matches(Regex("^[A-Za-z0-9-]{1,64}$"))) { "invalid device id" }
        return File(base, id)
    }

    fun get(id: String): VirtualDevice? = _devices.value.firstOrNull { it.id == id }

    fun create(template: VirtualDevice): VirtualDevice {
        val now = clock()
        val device = template.copy(id = newId(), createdAtMillis = now, updatedAtMillis = now, lastStartedAtMillis = null)
        requireValid(device)
        mutate { it + device }
        directory(device.id).mkdirs()
        return device
    }

    fun update(device: VirtualDevice): VirtualDevice {
        requireValid(device)
        val updated = device.copy(updatedAtMillis = clock())
        mutate { list ->
            if (list.none { it.id == device.id }) throw IOException("Device ${device.id} no longer exists.")
            list.map { if (it.id == device.id) updated else it }
        }
        return updated
    }

    fun markStarted(id: String) {
        mutate { list -> list.map { if (it.id == id) it.copy(lastStartedAtMillis = clock()) else it } }
    }

    /**
     * Copies a device. With [includeDisks] the writable disks and NVRAM are
     * copied too (may be large); otherwise only the configuration is cloned
     * and the copy starts with fresh disks.
     */
    fun duplicate(id: String, includeDisks: Boolean): VirtualDevice {
        val source = get(id) ?: throw IOException("Device not found.")
        val copy = create(source.copy(name = uniqueName("${source.name} copy")))
        if (includeDisks) {
            val from = directory(source.id)
            val to = directory(copy.id)
            from.listFiles()?.filter { it.isFile && (it.name.endsWith(".img") || it.name.endsWith(".qcow2") || it.name.endsWith(".fd") || it.name.endsWith(".iso")) }
                ?.forEach { FileUtils.copy(it, File(to, it.name)) }
        }
        return copy
    }

    fun delete(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
        FileUtils.deleteRecursively(directory(id))
    }

    fun export(id: String): String {
        val device = get(id) ?: throw IOException("Device not found.")
        return json.encodeToString(DeviceExport.serializer(), DeviceExport(device = device))
    }

    /** Imports a configuration exported by [export]. Always creates a new device. */
    fun import(text: String): VirtualDevice {
        val parsed = try {
            json.decodeFromString(DeviceExport.serializer(), text)
        } catch (e: Exception) {
            throw IOException("This file is not an Experience Virtual OS device configuration.", e)
        }
        if (parsed.format != DeviceExport.FORMAT) throw IOException("Unknown configuration format '${parsed.format}'.")
        if (parsed.version != 1) throw IOException("Configuration version ${parsed.version} is not supported.")
        // Disk media are not part of the export; the imported device starts clean.
        return create(parsed.device.copy(name = uniqueName(parsed.device.name), installMedia = null))
    }

    fun uniqueName(wanted: String): String {
        val names = _devices.value.map { it.name }.toSet()
        if (wanted !in names) return wanted
        var i = 2
        while ("$wanted $i" in names) i++
        return "$wanted $i"
    }

    private fun requireValid(device: VirtualDevice) {
        val problems = device.validate()
        if (problems.isNotEmpty()) throw IllegalArgumentException(problems.joinToString(" "))
    }

    private fun mutate(change: (List<VirtualDevice>) -> List<VirtualDevice>) = synchronized(lock) {
        val next = change(_devices.value)
        save(next)
        _devices.value = next
    }

    private fun load(): List<VirtualDevice> {
        if (!index.isFile) return emptyList()
        return try {
            json.decodeFromString(Index.serializer(), index.readText()).devices
        } catch (e: Exception) {
            // Keep the unreadable file for diagnosis instead of overwriting it.
            index.renameTo(File(base, "devices.corrupt-${clock()}.json"))
            emptyList()
        }
    }

    private fun save(list: List<VirtualDevice>) {
        base.mkdirs()
        FileUtils.writeAtomically(index, json.encodeToString(Index.serializer(), Index(1, list)))
    }

    @Serializable
    private data class Index(val version: Int, val devices: List<VirtualDevice>)

    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    }
}
