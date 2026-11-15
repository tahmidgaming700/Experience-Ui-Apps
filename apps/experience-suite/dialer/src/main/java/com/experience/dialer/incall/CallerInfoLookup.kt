package com.experience.dialer.incall

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import com.experience.dialer.data.phoneTypeLabel

data class CallerInfo(val name: String?, val photoUri: String?, val label: String?, val lookupUri: String?)

/** Contact lookup by number through ContactsContract.PhoneLookup (READ_CONTACTS). */
object CallerInfoLookup {
    fun lookup(context: Context, number: String): CallerInfo? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI, PhoneLookup.TYPE, PhoneLookup.LABEL, PhoneLookup.LOOKUP_KEY, PhoneLookup._ID)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val lookupKey = c.getString(4)
                CallerInfo(
                    name = c.getString(0),
                    photoUri = c.getString(1),
                    label = phoneTypeLabel(c.getInt(2), c.getString(3)),
                    lookupUri = lookupKey?.let { android.provider.ContactsContract.Contacts.getLookupUri(c.getLong(5), it).toString() },
                )
            }
        }.getOrNull()
    }
}
