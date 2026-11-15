package com.experience.contacts.data

import android.net.Uri
import android.provider.ContactsContract
import java.text.Normalizer
import java.util.Locale

/** Row of the contact list. */
data class ContactItem(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
) {
    val lookupUri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

data class LabeledValue(val dataId: Long, val value: String, val label: String, val primary: Boolean = false)

data class ContactDetails(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val photoUri: String?,
    val phones: List<LabeledValue>,
    val emails: List<LabeledValue>,
    val addresses: List<LabeledValue>,
    val websites: List<String>,
    val company: String?,
    val jobTitle: String?,
    val notes: List<String>,
    val birthday: String?,
    val nickname: String?,
    val accounts: List<String>,
) {
    val lookupUri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

/** A–Z section helpers for the list and its fast-scroll rail. */
object Sections {
    /** Upper-case first letter with accents removed ("Émile" → "E"), "#" for non-letters. */
    fun of(name: String): String {
        val first = name.trimStart().firstOrNull() ?: return "#"
        if (!first.isLetter()) return "#"
        val folded = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first()
        return folded.toString().uppercase(Locale.getDefault())
    }

    /** Index of the first item of each section, in list order. */
    fun <T> starts(items: List<T>, name: (T) -> String): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        items.forEachIndexed { i, item -> out.putIfAbsent(of(name(item)), i) }
        return out
    }
}
