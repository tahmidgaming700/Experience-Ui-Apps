package com.experience.notes.data

import org.json.JSONArray
import org.json.JSONObject

/** Tonal palette role used as a note's background; mapped to colour-scheme containers in the UI. */
enum class NoteColor { DEFAULT, PRIMARY, SECONDARY, TERTIARY, ERROR, SURFACE_HIGH }

data class ChecklistItem(val text: String, val checked: Boolean = false)

data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val color: NoteColor = NoteColor.DEFAULT,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** Epoch millis when moved to the trash, or null when not trashed. */
    val trashedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val isTrashed get() = trashedAt != null
    val isBlank get() = title.isBlank() && body.isBlank() && checklist.all { it.text.isBlank() }

    /** Plain-text rendering used for sharing and shortcuts. */
    fun toShareText(): String = buildString {
        if (title.isNotBlank()) appendLine(title.trim())
        if (body.isNotBlank()) { if (isNotEmpty()) appendLine(); appendLine(body.trim()) }
        if (checklist.isNotEmpty()) {
            if (isNotEmpty()) appendLine()
            checklist.forEach { appendLine((if (it.checked) "☑ " else "☐ ") + it.text) }
        }
    }.trimEnd()
}

enum class NoteFilter { NOTES, ARCHIVE, TRASH }

internal object ChecklistCodec {
    fun encode(items: List<ChecklistItem>): String? {
        if (items.isEmpty()) return null
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.text).put("c", it.checked)) }
        return arr.toString()
    }

    fun decode(raw: String?): List<ChecklistItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> ChecklistItem(o.optString("t"), o.optBoolean("c")) } }
        }.getOrDefault(emptyList())
    }
}
