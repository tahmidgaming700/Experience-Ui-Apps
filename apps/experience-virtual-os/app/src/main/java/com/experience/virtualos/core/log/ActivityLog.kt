package com.experience.virtualos.core.log

import com.experience.virtualos.core.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

@Serializable
data class ActivityEntry(val timeMillis: Long, val level: LogLevel, val category: String, val message: String)

/** Persistent, bounded record of user-visible events (installs, starts, failures). */
class ActivityLog(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val serializer = ListSerializer(ActivityEntry.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<ActivityEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(level: LogLevel, category: String, message: String) {
        val next = (listOf(ActivityEntry(clock(), level, category, message.take(2000))) + _entries.value).take(MAX)
        _entries.value = next
        try {
            FileUtils.writeAtomically(file, json.encodeToString(serializer, next))
        } catch (_: Exception) {
            // Logging must never take the app down.
        }
    }

    fun info(category: String, message: String) = add(LogLevel.INFO, category, message)
    fun success(category: String, message: String) = add(LogLevel.SUCCESS, category, message)
    fun warning(category: String, message: String) = add(LogLevel.WARNING, category, message)
    fun error(category: String, message: String) = add(LogLevel.ERROR, category, message)

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        file.delete()
    }

    private fun load(): List<ActivityEntry> = try {
        if (file.isFile) json.decodeFromString(serializer, file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val MAX = 200
    }
}
