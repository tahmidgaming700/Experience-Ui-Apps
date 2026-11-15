package com.experience.contacts.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale

/** A value with its vCard TYPE parameters (upper-case, e.g. CELL, HOME, PREF). */
data class VTyped(val value: String, val types: Set<String> = emptySet())

data class VAddress(
    val poBox: String = "",
    val extended: String = "",
    val street: String = "",
    val locality: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    val types: Set<String> = emptySet(),
) {
    /** Multi-line address as shown in the UI and stored in FORMATTED_ADDRESS. */
    val formatted: String
        get() = listOf(
            listOf(poBox, extended, street).filter { it.isNotBlank() }.joinToString("\n"),
            listOf(postalCode, locality).filter { it.isNotBlank() }.joinToString(" "),
            region, country,
        ).filter { it.isNotBlank() }.joinToString("\n")
}

data class VCardContact(
    val formattedName: String? = null,
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val phones: List<VTyped> = emptyList(),
    val emails: List<VTyped> = emptyList(),
    val addresses: List<VAddress> = emptyList(),
    val organization: String? = null,
    val title: String? = null,
    val note: String? = null,
    val urls: List<String> = emptyList(),
    /** "yyyy-MM-dd" or "--MM-dd" (no year), the formats ContactsContract Event rows use. */
    val birthday: String? = null,
) {
    val displayName: String
        get() = formattedName?.takeIf { it.isNotBlank() }
            ?: listOf(prefix, given, middle, family, suffix).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
            ?: organization?.takeIf { it.isNotBlank() }
            ?: phones.firstOrNull()?.value ?: emails.firstOrNull()?.value ?: ""
}

/**
 * vCard 2.1 / 3.0 (and tolerant 4.0) parser for the properties Experience Contacts
 * imports: N, FN, TEL, EMAIL, ADR, ORG, TITLE, NOTE, URL, BDAY.
 *
 * Handles line folding (3.0 whitespace continuation and 2.1 quoted-printable soft
 * breaks), QUOTED-PRINTABLE with CHARSET, 2.1 bare type parameters (TEL;CELL) and
 * 3.0 TYPE= lists, groups (item1.TEL) and backslash escaping.
 */
object VCardParser {

    fun parse(text: String): List<VCardContact> {
        val out = ArrayList<VCardContact>()
        var builder: Builder? = null
        var depth = 0
        for (line in unfold(text)) {
            val prop = parseLine(line) ?: continue
            when (prop.name) {
                "BEGIN" -> if (prop.rawValue.trim().equals("VCARD", true)) {
                    depth++
                    if (depth == 1) builder = Builder()
                }
                "END" -> if (prop.rawValue.trim().equals("VCARD", true)) {
                    if (depth == 1) builder?.build()?.let { out += it }
                    depth = (depth - 1).coerceAtLeast(0)
                    if (depth == 0) builder = null
                }
                else -> if (depth == 1) builder?.add(prop)
            }
        }
        return out
    }

    /** Joins folded lines into logical property lines. */
    internal fun unfold(text: String): List<String> {
        val raw = text.split("\r\n", "\n", "\r")
        val out = ArrayList<String>()
        for (line in raw) {
            val prev = out.lastOrNull()
            when {
                prev != null && isQuotedPrintable(prev) && prev.endsWith("=") -> out[out.lastIndex] = prev.dropLast(1) + line
                prev != null && (line.startsWith(" ") || line.startsWith("\t")) -> out[out.lastIndex] = prev + line.substring(1)
                line.isNotEmpty() -> out += line
            }
        }
        return out
    }

    private fun isQuotedPrintable(line: String): Boolean {
        val head = line.substringBefore(':')
        return head.contains("QUOTED-PRINTABLE", ignoreCase = true)
    }

    internal class Property(val name: String, val params: Map<String, List<String>>, val types: Set<String>, val rawValue: String) {
        fun decoded(): String {
            val encoding = params["ENCODING"]?.firstOrNull()?.uppercase(Locale.ROOT)
            val charset = params["CHARSET"]?.firstOrNull()
            if (encoding == "QUOTED-PRINTABLE" || "QUOTED-PRINTABLE" in types) {
                val bytes = decodeQuotedPrintable(rawValue)
                val cs = runCatching { Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
                return String(bytes, cs)
            }
            return rawValue
        }
    }

    internal fun parseLine(line: String): Property? {
        // Split on the first ':' that is not inside a quoted parameter value.
        var inQuotes = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(';')
        val name = parts[0].substringAfterLast('.').trim().uppercase(Locale.ROOT)
        val params = LinkedHashMap<String, MutableList<String>>()
        val types = LinkedHashSet<String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq < 0) {
                // vCard 2.1 bare parameter: TEL;CELL;PREF / QUOTED-PRINTABLE / BASE64.
                val bare = p.trim().uppercase(Locale.ROOT)
                if (bare == "QUOTED-PRINTABLE" || bare == "BASE64" || bare == "B") {
                    params.getOrPut("ENCODING") { mutableListOf() } += bare
                }
                types += bare
            } else {
                val key = p.substring(0, eq).trim().uppercase(Locale.ROOT)
                val values = p.substring(eq + 1).split(',').map { it.trim().trim('"') }
                params.getOrPut(key) { mutableListOf() } += values
                if (key == "TYPE") types += values.map { it.uppercase(Locale.ROOT) }
            }
        }
        return Property(name, params, types, value)
    }

    internal fun decodeQuotedPrintable(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) { out.write(v); i += 3; continue }
            }
            // Non-ASCII characters can appear when the file was already decoded as UTF-8.
            val bytes = c.toString().toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            i++
        }
        return out.toByteArray()
    }

    /** Splits a structured value on unescaped ';' and unescapes each component. */
    internal fun components(value: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) { sb.append(c).append(value[i + 1]); i += 2; continue }
            if (c == ';') { out += unescape(sb.toString()); sb.clear() } else sb.append(c)
            i++
        }
        out += unescape(sb.toString())
        return out
    }

    internal fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    /** "19900517", "1990-05-17", "1990-05-17T00:00:00Z", "--0517", "--05-17" → ContactsContract form. */
    fun normalizeBirthday(raw: String): String? {
        val v = raw.trim().substringBefore('T')
        Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})$").find(v)?.let { m ->
            val (y, mo, d) = m.destructured
            return "$y-$mo-$d"
        }
        Regex("^--(\\d{2})-?(\\d{2})$").find(v)?.let { m ->
            val (mo, d) = m.destructured
            return "--$mo-$d"
        }
        return null
    }

    private class Builder {
        var fn: String? = null
        var n: List<String>? = null
        val phones = ArrayList<VTyped>()
        val emails = ArrayList<VTyped>()
        val addresses = ArrayList<VAddress>()
        var org: String? = null
        var title: String? = null
        val notes = ArrayList<String>()
        val urls = ArrayList<String>()
        var bday: String? = null

        fun add(p: Property) {
            val value = p.decoded()
            when (p.name) {
                "FN" -> fn = unescape(value).trim()
                "N" -> n = components(value).map { it.trim() }
                "TEL" -> unescape(value).trim().removePrefix("tel:").takeIf { it.isNotEmpty() }?.let { phones += VTyped(it, p.types) }
                "EMAIL" -> unescape(value).trim().takeIf { it.isNotEmpty() }?.let { emails += VTyped(it, p.types) }
                "ADR" -> {
                    val c = components(value).map { it.trim() } + List(7) { "" }
                    val a = VAddress(c[0], c[1], c[2], c[3], c[4], c[5], c[6], p.types)
                    if (a.formatted.isNotBlank()) addresses += a
                }
                "ORG" -> org = components(value).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotEmpty() }
                "TITLE" -> title = unescape(value).trim().takeIf { it.isNotEmpty() }
                "NOTE" -> unescape(value).trim().takeIf { it.isNotEmpty() }?.let { notes += it }
                "URL" -> unescape(value).trim().takeIf { it.isNotEmpty() }?.let { urls += it }
                "BDAY" -> bday = normalizeBirthday(value)
            }
        }

        fun build(): VCardContact? {
            val nn = (n.orEmpty() + List(5) { "" })
            val c = VCardContact(
                formattedName = fn,
                family = nn[0], given = nn[1], middle = nn[2], prefix = nn[3], suffix = nn[4],
                phones = phones, emails = emails, addresses = addresses,
                organization = org, title = title,
                note = notes.joinToString("\n").takeIf { it.isNotEmpty() },
                urls = urls, birthday = bday,
            )
            return c.takeIf { it.displayName.isNotBlank() }
        }
    }
}

/** Maps vCard TYPE parameters to ContactsContract type constants. */
object VCardTypes {
    fun phoneType(types: Set<String>): Int = when {
        "FAX" in types && "HOME" in types -> Phone.TYPE_FAX_HOME
        "FAX" in types -> Phone.TYPE_FAX_WORK
        "PAGER" in types -> Phone.TYPE_PAGER
        ("CELL" in types || "MOBILE" in types) && "WORK" in types -> Phone.TYPE_WORK_MOBILE
        "CELL" in types || "MOBILE" in types || "IPHONE" in types -> Phone.TYPE_MOBILE
        "WORK" in types -> Phone.TYPE_WORK
        "HOME" in types -> Phone.TYPE_HOME
        "MAIN" in types -> Phone.TYPE_MAIN
        "CAR" in types -> Phone.TYPE_CAR
        "ISDN" in types -> Phone.TYPE_ISDN
        else -> Phone.TYPE_OTHER
    }

    fun emailType(types: Set<String>): Int = when {
        "WORK" in types -> Email.TYPE_WORK
        "HOME" in types -> Email.TYPE_HOME
        "CELL" in types -> Email.TYPE_MOBILE
        else -> Email.TYPE_OTHER
    }

    fun postalType(types: Set<String>): Int = when {
        "WORK" in types -> StructuredPostal.TYPE_WORK
        "HOME" in types -> StructuredPostal.TYPE_HOME
        else -> StructuredPostal.TYPE_OTHER
    }

    fun isPreferred(types: Set<String>) = "PREF" in types
}
