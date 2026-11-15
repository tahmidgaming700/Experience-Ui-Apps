package com.experience.contacts.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import java.io.OutputStream

/** Read/write access to ContactsContract (READ_CONTACTS / WRITE_CONTACTS). */
class ContactsRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun list(): List<ContactItem> {
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED)
        val out = ArrayList<ContactItem>()
        runCatching {
            resolver.query(Contacts.CONTENT_URI, projection, null, null, "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
                while (c.moveToNext()) {
                    out += ContactItem(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3), c.getInt(4) != 0)
                }
            }
        }
        return out
    }

    /** Contact ids matching [query] by name, phone number or email address. */
    fun search(query: String): Set<Long> {
        val q = query.trim()
        if (q.isEmpty()) return emptySet()
        val ids = HashSet<Long>()
        fun collect(base: Uri, column: String) = runCatching {
            resolver.query(Uri.withAppendedPath(base, Uri.encode(q)), arrayOf(column), null, null, null)?.use { c ->
                while (c.moveToNext()) ids += c.getLong(0)
            }
        }
        collect(Contacts.CONTENT_FILTER_URI, Contacts._ID)
        if (q.any { it.isDigit() }) collect(Phone.CONTENT_FILTER_URI, Phone.CONTACT_ID)
        collect(Email.CONTENT_FILTER_URI, Email.CONTACT_ID)
        return ids
    }

    /** Resolves a lookup / contact / raw-contact URI to a contact lookup URI. */
    fun resolve(uri: Uri): Uri? = runCatching {
        val path = uri.toString()
        when {
            path.startsWith(RawContacts.CONTENT_URI.toString()) -> RawContacts.getContactLookupUri(resolver, uri)
            else -> {
                val contact = Contacts.lookupContact(resolver, uri) ?: return@runCatching null
                Contacts.getLookupUri(resolver, contact)
            }
        }
    }.getOrNull()

    fun details(lookupUri: Uri): ContactDetails? {
        val contactUri = runCatching { Contacts.lookupContact(resolver, lookupUri) }.getOrNull() ?: return null
        val id = ContentUris.parseId(contactUri)
        var name = ""
        var lookupKey = ""
        var starred = false
        var photo: String? = null
        resolver.query(contactUri, arrayOf(Contacts.DISPLAY_NAME_PRIMARY, Contacts.LOOKUP_KEY, Contacts.STARRED, Contacts.PHOTO_URI), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            name = c.getString(0).orEmpty()
            lookupKey = c.getString(1).orEmpty()
            starred = c.getInt(2) != 0
            photo = c.getString(3)
        } ?: return null

        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        val addresses = ArrayList<LabeledValue>()
        val websites = LinkedHashSet<String>()
        val notes = LinkedHashSet<String>()
        var company: String? = null
        var title: String? = null
        var birthday: String? = null
        var nickname: String? = null
        val projection = arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.IS_SUPER_PRIMARY)
        resolver.query(Data.CONTENT_URI, projection, "${Data.CONTACT_ID} = ?", arrayOf(id.toString()), "${Data.IS_SUPER_PRIMARY} DESC, ${Data._ID}")?.use { c ->
            while (c.moveToNext()) {
                val dataId = c.getLong(0)
                val value = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val type = if (c.isNull(3)) 0 else c.getInt(3)
                val custom = c.getString(4)
                val primary = c.getInt(6) != 0
                when (c.getString(1)) {
                    Phone.CONTENT_ITEM_TYPE -> if (phones.none { it.value.filter(Char::isDigit) == value.filter(Char::isDigit) }) {
                        phones += LabeledValue(dataId, value, Phone.getTypeLabel(context.resources, type, custom).toString(), primary)
                    }
                    Email.CONTENT_ITEM_TYPE -> if (emails.none { it.value.equals(value, true) }) {
                        emails += LabeledValue(dataId, value, Email.getTypeLabel(context.resources, type, custom).toString(), primary)
                    }
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (addresses.none { it.value == value }) {
                        addresses += LabeledValue(dataId, value, StructuredPostal.getTypeLabel(context.resources, type, custom).toString())
                    }
                    Website.CONTENT_ITEM_TYPE -> websites += value
                    Note.CONTENT_ITEM_TYPE -> notes += value
                    Organization.CONTENT_ITEM_TYPE -> if (company == null) { company = value; title = c.getString(5) }
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) birthday = value
                    Nickname.CONTENT_ITEM_TYPE -> nickname = value
                }
            }
        }
        // Organization rows may carry only a title (DATA4) with no company (DATA1).
        if (company == null) {
            resolver.query(Data.CONTENT_URI, arrayOf(Organization.TITLE), "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(id.toString(), Organization.CONTENT_ITEM_TYPE), null)?.use { c -> if (c.moveToFirst()) title = c.getString(0) }
        }
        val accounts = ArrayList<String>()
        resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), "${RawContacts.CONTACT_ID} = ?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) accounts += c.getString(0) ?: "Device"
        }
        return ContactDetails(id, lookupKey, name, starred, photo, phones, emails, addresses, websites.toList(), company, title,
            notes.toList(), birthday, nickname, accounts.distinct())
    }

    fun setStarred(contactId: Long, starred: Boolean) = runCatching {
        resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }, null, null)
    }.isSuccess

    fun delete(lookupUri: Uri): Boolean = runCatching { resolver.delete(lookupUri, null, null) > 0 }.getOrDefault(false)

    /**
     * Writes vCards for [lookupKeys] to [out] using the provider's own vCard composer
     * (CONTENT_MULTI_VCARD_URI), in chunks to keep URIs a sane length.
     */
    fun exportVCards(lookupKeys: List<String>, out: OutputStream): Int {
        var written = 0
        lookupKeys.chunked(100).forEach { chunk ->
            val uri = if (chunk.size == 1) Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, chunk[0])
            else Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(chunk.joinToString(":")))
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.createInputStream().use { it.copyTo(out) }
                written += chunk.size
            }
        }
        out.flush()
        return written
    }

    fun vCardUri(lookupKey: String): Uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey)
}
