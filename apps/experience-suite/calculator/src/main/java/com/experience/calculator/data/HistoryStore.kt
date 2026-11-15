package com.experience.calculator.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val expression: String, val result: String, val timestamp: Long)

/** Calculation history persisted as a JSON array in private SharedPreferences (newest first). */
class HistoryStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("calculator", Context.MODE_PRIVATE))

    companion object {
        const val MAX_ENTRIES = 100
        private const val KEY_HISTORY = "history"
        private const val KEY_ANGLE = "angle_mode"
    }

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HistoryEntry(o.getString("e"), o.getString("r"), o.optLong("t"))
            }
        }.getOrDefault(emptyList())
    }

    fun add(entry: HistoryEntry): List<HistoryEntry> {
        val list = (listOf(entry) + load().filterNot { it.expression == entry.expression && it.result == entry.result })
            .take(MAX_ENTRIES)
        save(list)
        return list
    }

    fun clear() = prefs.edit().remove(KEY_HISTORY).apply()

    private fun save(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("e", it.expression).put("r", it.result).put("t", it.timestamp)) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    var angleModeName: String
        get() = prefs.getString(KEY_ANGLE, "DEG") ?: "DEG"
        set(value) = prefs.edit().putString(KEY_ANGLE, value).apply()
}
