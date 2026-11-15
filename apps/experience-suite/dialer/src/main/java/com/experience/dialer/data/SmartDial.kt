package com.experience.dialer.data

import java.text.Normalizer

/**
 * T9 "smart dial": matches the digits typed on the keypad against contact names
 * (letters mapped to their keypad digit) and phone numbers.
 *
 * A name matches when the typed digits spell a run of letters that starts at the
 * beginning of any word and may continue across word boundaries ("john smith" is
 * matched by 5646 and by 564676484), or when they spell the initials of
 * consecutive words (57 → "John Smith"). A number matches when its digits contain
 * the typed digits.
 */
object SmartDial {

    enum class Kind(val rank: Int) { NUMBER_PREFIX(0), NAME_WORD(1), NAME_INITIALS(2), NUMBER_CONTAINS(3) }

    data class Match(val kind: Kind, val nameRange: IntRange? = null, val numberDigitRange: IntRange? = null)

    private val letters = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
    )
    private val letterToDigit: Map<Char, Char> = buildMap {
        letters.forEach { (digit, chars) -> chars.forEach { put(it, digit) } }
    }

    /** Keypad digit for [c] (letters map to their key, digits to themselves), or null. */
    fun digitFor(c: Char): Char? {
        if (c in '0'..'9') return c
        val lower = c.lowercaseChar()
        letterToDigit[lower]?.let { return it }
        // Fold accents: "é" → "e".
        val folded = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD).firstOrNull() ?: return null
        return letterToDigit[folded]
    }

    fun toDigits(text: String): String = buildString { text.forEach { c -> digitFor(c)?.let(::append) } }

    /** Only the digits of a dial query; '+', '*', '#' and separators are ignored. */
    fun queryDigits(query: String): String = query.filter { it in '0'..'9' }

    fun matchName(name: String, query: String): Match? {
        val q = queryDigits(query)
        if (q.isEmpty()) return null
        // (index in name, digit) for every dialable character.
        val positions = ArrayList<Pair<Int, Char>>(name.length)
        val wordStarts = ArrayList<Int>() // indexes into positions
        var previousWasDialable = false
        name.forEachIndexed { i, c ->
            val d = digitFor(c)
            if (d != null) {
                if (!previousWasDialable) wordStarts += positions.size
                positions += i to d
                previousWasDialable = true
            } else {
                previousWasDialable = false
            }
        }
        for (start in wordStarts) {
            if (start + q.length > positions.size) continue
            var ok = true
            for (k in q.indices) if (positions[start + k].second != q[k]) { ok = false; break }
            if (ok) return Match(Kind.NAME_WORD, positions[start].first..positions[start + q.length - 1].first)
        }
        if (q.length >= 2) {
            for (s in wordStarts.indices) {
                if (s + q.length > wordStarts.size) break
                var ok = true
                for (k in q.indices) if (positions[wordStarts[s + k]].second != q[k]) { ok = false; break }
                if (ok) return Match(Kind.NAME_INITIALS)
            }
        }
        return null
    }

    fun matchNumber(number: String, query: String): Match? {
        val q = queryDigits(query)
        if (q.isEmpty()) return null
        val digits = number.filter { it in '0'..'9' }
        val idx = digits.indexOf(q)
        if (idx < 0) return null
        val kind = if (idx == 0 || (number.trimStart().startsWith("+") && idx <= 3)) Kind.NUMBER_PREFIX else Kind.NUMBER_CONTAINS
        return Match(kind, numberDigitRange = idx until idx + q.length)
    }

    /** Best match for a contact phone entry, or null when it does not match. */
    fun match(name: String, number: String, query: String): Match? {
        val n = matchName(name, query)
        val m = matchNumber(number, query)
        return listOfNotNull(n, m).minByOrNull { it.kind.rank }
    }

    /** Filters and orders [items] for [query]; ties keep the input order (alphabetical). */
    fun <T> filter(items: List<T>, query: String, name: (T) -> String, number: (T) -> String): List<Pair<T, Match>> {
        if (queryDigits(query).isEmpty()) return emptyList()
        return items.mapNotNull { item -> match(name(item), number(item), query)?.let { item to it } }
            .sortedBy { it.second.kind.rank }
    }
}
