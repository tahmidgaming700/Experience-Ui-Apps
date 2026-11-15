package com.experience.dialer.data

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone

/** One phone number of a contact. */
data class DialContact(
    val dataId: Long,
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String,
    val label: String?,
    val photoUri: String?,
    val starred: Boolean,
) {
    val lookupUri get() = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
}

/** Loads every phone number with its contact, alphabetically (READ_CONTACTS). */
class DialContactsRepository(private val context: Context) {
    fun loadPhones(): List<DialContact> {
        val projection = arrayOf(
            Phone._ID, Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER,
            Phone.TYPE, Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED,
        )
        val out = ArrayList<DialContact>()
        val seen = HashSet<Pair<Long, String>>()
        runCatching {
            context.contentResolver.query(
                Phone.CONTENT_URI, projection, null, null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(4) ?: continue
                    val contactId = c.getLong(1)
                    // Linked raw contacts often repeat the same number; keep one.
                    if (!seen.add(contactId to number.filter { it.isDigit() || it == '+' })) continue
                    out += DialContact(
                        dataId = c.getLong(0),
                        contactId = contactId,
                        lookupKey = c.getString(2).orEmpty(),
                        name = c.getString(3) ?: number,
                        number = number,
                        label = phoneTypeLabel(c.getInt(5), c.getString(6)),
                        photoUri = c.getString(7),
                        starred = c.getInt(8) != 0,
                    )
                }
            }
        }
        return out
    }
}
