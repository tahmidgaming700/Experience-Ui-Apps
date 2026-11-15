package com.experience.converter.model

import android.content.Context

/** One remembered conversion; the result is recomputed when shown. */
data class RecentConversion(val category: Category, val fromId: String, val toId: String, val input: String)

/** Pure (de)serialisation of the recent list: one entry per line, tab-separated fields. */
object RecentsCodec {
    const val MAX = 12

    fun encode(items: List<RecentConversion>): String = items.joinToString("\n") {
        listOf(it.category.name, it.fromId, it.toId, it.input.filterNot { c -> c == '\t' || c == '\n' }).joinToString("\t")
    }

    fun decode(text: String?): List<RecentConversion> = text.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 4) return@mapNotNull null
        val cat = Category.entries.firstOrNull { it.name == f[0] } ?: return@mapNotNull null
        RecentConversion(cat, f[1], f[2], f[3])
    }.toList()

    /** Puts [item] first, removing an older entry with the same category and units. */
    fun push(items: List<RecentConversion>, item: RecentConversion): List<RecentConversion> =
        (listOf(item) + items.filterNot { it.category == item.category && it.fromId == item.fromId && it.toId == item.toId })
            .take(MAX)
}

class RecentsStore(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<RecentConversion> = RecentsCodec.decode(prefs.getString(KEY, null))

    fun save(items: List<RecentConversion>) {
        prefs.edit().putString(KEY, RecentsCodec.encode(items)).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "items" }
}
