package com.experience.launcher.model

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Alphabetical ordering, sections and search for the app drawer. */
object AppSearch {

    private val diacritics = Regex("\\p{Mn}+")

    /** Lower-cased, accent-free form used for matching ("Café" -> "cafe"). */
    fun normalize(text: String): String =
        diacritics.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()

    /** Sorts by label using the locale's collation rules. */
    fun <T> sorted(items: List<T>, locale: Locale = Locale.getDefault(), label: (T) -> String): List<T> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return items.sortedWith { a, b -> collator.compare(label(a), label(b)) }
    }

    /**
     * Filters [items] by [query] and ranks them: label prefix matches first, then
     * matches at the start of a word, then any substring of the label, then matches
     * on the package name. Within a rank the input order (alphabetical) is kept.
     */
    fun <T> search(items: List<T>, query: String, packageName: (T) -> String = { "" }, label: (T) -> String): List<T> {
        val q = normalize(query)
        if (q.isEmpty()) return items
        return items.mapNotNull { item ->
            val l = normalize(label(item))
            val rank = when {
                l.startsWith(q) -> 0
                l.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
                l.contains(q) -> 2
                packageName(item).lowercase(Locale.ROOT).contains(q) -> 3
                else -> return@mapNotNull null
            }
            rank to item
        }.sortedBy { it.first }.map { it.second }
    }

    /** Section letter for the drawer index: an accent-free capital letter, or "#". */
    fun sectionOf(label: String): String {
        val first = normalize(label).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }
}
