package com.experience.contacts.data

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

/** Reads vCard files and inserts them as device-local contacts. */
class VCardImporter(private val context: Context) {

    fun read(uri: Uri): List<VCardContact> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        return VCardParser.parse(String(bytes, Charsets.UTF_8))
    }

    /** Inserts [contacts]; [progress] receives the number done. Returns how many were stored. */
    fun import(contacts: List<VCardContact>, progress: (Int) -> Unit = {}): Int {
        var done = 0
        contacts.forEach { v ->
            if (runCatching { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations(v)) }.isSuccess) done++
            progress(done)
        }
        return done
    }

    internal fun operations(v: VCardContact): ArrayList<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null).withValue(RawContacts.ACCOUNT_NAME, null).build()
        fun row(mime: String, vararg pairs: Pair<String, Any?>) {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0).withValue(Data.MIMETYPE, mime)
            pairs.forEach { (k, value) -> b.withValue(k, value) }
            ops += b.build()
        }
        row(
            StructuredName.CONTENT_ITEM_TYPE,
            StructuredName.DISPLAY_NAME to v.displayName,
            StructuredName.PREFIX to v.prefix.ifBlank { null },
            StructuredName.GIVEN_NAME to v.given.ifBlank { null },
            StructuredName.MIDDLE_NAME to v.middle.ifBlank { null },
            StructuredName.FAMILY_NAME to v.family.ifBlank { null },
            StructuredName.SUFFIX to v.suffix.ifBlank { null },
        )
        v.phones.forEach { p ->
            row(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to p.value, Phone.TYPE to VCardTypes.phoneType(p.types),
                Data.IS_PRIMARY to if (VCardTypes.isPreferred(p.types)) 1 else 0)
        }
        v.emails.forEach { e ->
            row(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to e.value, Email.TYPE to VCardTypes.emailType(e.types),
                Data.IS_PRIMARY to if (VCardTypes.isPreferred(e.types)) 1 else 0)
        }
        v.addresses.forEach { a ->
            row(
                StructuredPostal.CONTENT_ITEM_TYPE,
                StructuredPostal.FORMATTED_ADDRESS to a.formatted,
                StructuredPostal.TYPE to VCardTypes.postalType(a.types),
                StructuredPostal.POBOX to a.poBox.ifBlank { null },
                StructuredPostal.NEIGHBORHOOD to a.extended.ifBlank { null },
                StructuredPostal.STREET to a.street.ifBlank { null },
                StructuredPostal.CITY to a.locality.ifBlank { null },
                StructuredPostal.REGION to a.region.ifBlank { null },
                StructuredPostal.POSTCODE to a.postalCode.ifBlank { null },
                StructuredPostal.COUNTRY to a.country.ifBlank { null },
            )
        }
        if (v.organization != null || v.title != null) {
            row(Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to v.organization, Organization.TITLE to v.title, Organization.TYPE to Organization.TYPE_WORK)
        }
        v.note?.let { row(Note.CONTENT_ITEM_TYPE, Note.NOTE to it) }
        v.urls.forEach { row(Website.CONTENT_ITEM_TYPE, Website.URL to it, Website.TYPE to Website.TYPE_OTHER) }
        v.birthday?.let { row(Event.CONTENT_ITEM_TYPE, Event.START_DATE to it, Event.TYPE to Event.TYPE_BIRTHDAY) }
        return ops
    }
}
