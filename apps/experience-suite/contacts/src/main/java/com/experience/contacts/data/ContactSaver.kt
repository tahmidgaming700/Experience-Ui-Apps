package com.experience.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import java.io.ByteArrayOutputStream

/**
 * Loads a [ContactDraft] for one raw contact and saves drafts back through a single
 * ContentProviderOperation batch. New contacts are device-local raw contacts
 * (ACCOUNT_NAME / ACCOUNT_TYPE null).
 */
class ContactSaver(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** The raw contact to edit: the device-local one if present, else the first. */
    fun rawContactFor(lookupUri: Uri): Long? {
        val contact = runCatching { Contacts.lookupContact(resolver, lookupUri) }.getOrNull() ?: return null
        val contactId = android.content.ContentUris.parseId(contact)
        var first: Long? = null
        resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                if (first == null) first = c.getLong(0)
                if (c.isNull(1)) return c.getLong(0)
            }
        }
        return first
    }

    fun load(rawContactId: Long): ContactDraft {
        var d = ContactDraft(rawContactId = rawContactId)
        val phones = ArrayList<EditableField>()
        val emails = ArrayList<EditableField>()
        val addresses = ArrayList<EditableField>()
        val websites = ArrayList<EditableField>()
        val projection = arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6)
        resolver.query(Data.CONTENT_URI, projection, "${Data.RAW_CONTACT_ID} = ?", arrayOf(rawContactId.toString()), Data._ID)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val v1 = c.getString(2).orEmpty()
                val type = if (c.isNull(3)) 0 else c.getInt(3)
                val label = c.getString(4)
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> d = d.copy(
                        nameDataId = id,
                        given = c.getString(3).orEmpty(), family = c.getString(4).orEmpty(),
                        prefix = c.getString(5).orEmpty(), middle = c.getString(6).orEmpty(), suffix = c.getString(7).orEmpty(),
                    ).let { if (it.fullName.isBlank() && v1.isNotBlank()) NameSplit.split(v1).let { (g, f) -> it.copy(given = g, family = f) } else it }
                    Phone.CONTENT_ITEM_TYPE -> phones += EditableField(id, v1, type, label)
                    Email.CONTENT_ITEM_TYPE -> emails += EditableField(id, v1, type, label)
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses += EditableField(id, v1, type, label)
                    Website.CONTENT_ITEM_TYPE -> websites += EditableField(id, v1, type, label)
                    Organization.CONTENT_ITEM_TYPE -> d = d.copy(orgDataId = id, company = v1, jobTitle = c.getString(5).orEmpty())
                    Note.CONTENT_ITEM_TYPE -> d = d.copy(noteDataId = id, note = v1)
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) d = d.copy(birthdayDataId = id, birthday = v1)
                    Photo.CONTENT_ITEM_TYPE -> d = d.copy(photoDataId = id)
                }
            }
        }
        return d.copy(phones = phones, emails = emails, addresses = addresses, websites = websites)
    }

    /** Saves [draft] (compared with [original] for existing contacts); returns the contact lookup URI. */
    fun save(draft: ContactDraft, original: ContactDraft?): Uri? {
        val ops = ArrayList<ContentProviderOperation>()
        val raw = draft.rawContactId
        val back = raw == null
        if (back) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        }
        fun insert(mime: String, values: ContentValues) {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValues(values).withValue(Data.MIMETYPE, mime)
            if (back) b.withValueBackReference(Data.RAW_CONTACT_ID, 0) else b.withValue(Data.RAW_CONTACT_ID, raw)
            ops += b.build()
        }
        fun update(dataId: Long, values: ContentValues) {
            ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI).withSelection("${Data._ID} = ?", arrayOf(dataId.toString())).withValues(values).build()
        }
        fun delete(dataId: Long) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI).withSelection("${Data._ID} = ?", arrayOf(dataId.toString())).build()
        }
        fun apply(rowOps: List<RowOp>, mime: String, values: (EditableField) -> ContentValues) = rowOps.forEach { op ->
            when (op) {
                is RowOp.Insert -> insert(mime, values(op.field))
                is RowOp.Update -> update(op.dataId, values(op.field))
                is RowOp.Delete -> delete(op.dataId)
            }
        }
        fun typed(valueColumn: String, f: EditableField) = ContentValues().apply {
            put(valueColumn, f.value)
            put(Data.DATA2, f.type)
            put(Data.DATA3, f.label)
        }

        // Name: components plus DISPLAY_NAME so the provider does not have to guess.
        val nameValues = ContentValues().apply {
            put(StructuredName.DISPLAY_NAME, draft.fullName.ifBlank { null })
            put(StructuredName.PREFIX, draft.prefix.ifBlank { null })
            put(StructuredName.GIVEN_NAME, draft.given.ifBlank { null })
            put(StructuredName.MIDDLE_NAME, draft.middle.ifBlank { null })
            put(StructuredName.FAMILY_NAME, draft.family.ifBlank { null })
            put(StructuredName.SUFFIX, draft.suffix.ifBlank { null })
        }
        when {
            draft.nameDataId != null && draft.fullName.isBlank() -> delete(draft.nameDataId)
            draft.nameDataId != null -> if (original == null || original.fullName != draft.fullName ||
                original.given != draft.given || original.family != draft.family || original.middle != draft.middle ||
                original.prefix != draft.prefix || original.suffix != draft.suffix) update(draft.nameDataId, nameValues)
            draft.fullName.isNotBlank() -> insert(StructuredName.CONTENT_ITEM_TYPE, nameValues)
        }

        apply(DraftDiff.rows(original?.phones.orEmpty(), draft.phones), Phone.CONTENT_ITEM_TYPE) { typed(Phone.NUMBER, it) }
        apply(DraftDiff.rows(original?.emails.orEmpty(), draft.emails), Email.CONTENT_ITEM_TYPE) { typed(Email.ADDRESS, it) }
        apply(DraftDiff.rows(original?.addresses.orEmpty(), draft.addresses), StructuredPostal.CONTENT_ITEM_TYPE) { typed(StructuredPostal.FORMATTED_ADDRESS, it) }
        apply(DraftDiff.rows(original?.websites.orEmpty(), draft.websites), Website.CONTENT_ITEM_TYPE) { typed(Website.URL, it) }

        // Organization: company and title share one row.
        val orgChanged = original == null || original.company != draft.company || original.jobTitle != draft.jobTitle
        if (orgChanged) {
            val blank = draft.company.isBlank() && draft.jobTitle.isBlank()
            val values = ContentValues().apply {
                put(Organization.COMPANY, draft.company.trim().ifBlank { null })
                put(Organization.TITLE, draft.jobTitle.trim().ifBlank { null })
                put(Organization.TYPE, Organization.TYPE_WORK)
            }
            when {
                draft.orgDataId != null && blank -> delete(draft.orgDataId)
                draft.orgDataId != null -> update(draft.orgDataId, values)
                !blank -> insert(Organization.CONTENT_ITEM_TYPE, values)
            }
        }
        DraftDiff.single(draft.noteDataId, original?.note, draft.note)?.let { op ->
            apply(listOf(op), Note.CONTENT_ITEM_TYPE) { ContentValues().apply { put(Note.NOTE, it.value) } }
        }
        DraftDiff.single(draft.birthdayDataId, original?.birthday, draft.birthday)?.let { op ->
            apply(listOf(op), Event.CONTENT_ITEM_TYPE) {
                ContentValues().apply { put(Event.START_DATE, it.value); put(Event.TYPE, Event.TYPE_BIRTHDAY) }
            }
        }
        // Photo: full-size JPEG blob in the Photo row; the provider derives the thumbnail
        // and display photo from it.
        when {
            draft.newPhoto != null -> {
                val values = ContentValues().apply { put(Photo.PHOTO, draft.newPhoto); put(Data.IS_SUPER_PRIMARY, 1); put(Data.IS_PRIMARY, 1) }
                if (draft.photoDataId != null) update(draft.photoDataId, values) else insert(Photo.CONTENT_ITEM_TYPE, values)
            }
            draft.removePhoto && draft.photoDataId != null -> delete(draft.photoDataId)
        }

        if (ops.isEmpty()) return raw?.let { RawContacts.getContactLookupUri(resolver, android.content.ContentUris.withAppendedId(RawContacts.CONTENT_URI, it)) }
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawUri = if (back) results.firstOrNull()?.uri else android.content.ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw!!)
        return rawUri?.let { runCatching { RawContacts.getContactLookupUri(resolver, it) }.getOrNull() }
    }

    /** Decodes an image, centre-crops it square and scales it to at most 720 px JPEG. */
    fun preparePhoto(uri: Uri): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 720) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return@runCatching null
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val target = minOf(side, 720)
        val scaled = if (target == side) square else Bitmap.createScaledBitmap(square, target, target, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }.getOrNull()
}
