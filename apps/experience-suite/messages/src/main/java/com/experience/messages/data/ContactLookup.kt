package com.experience.messages.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import java.util.concurrent.ConcurrentHashMap

data class ContactInfo(val name: String?, val photoUri: String?, val lookupUri: Uri?)

/** Number → contact name/photo through PhoneLookup, cached for the process lifetime. */
object ContactLookup {
    private val cache = ConcurrentHashMap<String, ContactInfo>()

    fun invalidate() = cache.clear()

    fun lookup(context: Context, number: String): ContactInfo {
        cache[number]?.let { return it }
        if (number.isBlank() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactInfo(null, null, null)
        }
        val info = runCatching {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup._ID, PhoneLookup.LOOKUP_KEY), null, null, null)?.use { c ->
                if (c.moveToFirst()) ContactInfo(c.getString(0), c.getString(1), c.getString(3)?.let { ContactsContract.Contacts.getLookupUri(c.getLong(2), it) }) else null
            }
        }.getOrNull() ?: ContactInfo(null, null, null)
        cache[number] = info
        return info
    }

    fun displayName(context: Context, addresses: List<String>): String =
        addresses.joinToString(", ") { lookup(context, it).name ?: it }.ifBlank { "Unknown" }
}
