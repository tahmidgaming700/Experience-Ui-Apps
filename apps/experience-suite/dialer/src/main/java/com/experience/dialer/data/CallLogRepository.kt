package com.experience.dialer.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog

/** Reads and edits CallLog.Calls (READ_CALL_LOG / WRITE_CALL_LOG). */
class CallLogRepository(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun load(limit: Int = 1000): List<CallEntry> {
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.NUMBER_PRESENTATION,
            CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_NUMBER_LABEL, CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_PHOTO_URI, CallLog.Calls.CACHED_LOOKUP_URI,
            CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.IS_READ,
        )
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        val out = ArrayList<CallEntry>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    out += CallEntry(
                        id = c.getLong(0),
                        number = c.getString(1).orEmpty(),
                        presentation = c.getInt(2),
                        name = c.getString(3)?.takeIf { it.isNotBlank() },
                        numberLabel = phoneTypeLabel(c.getInt(5), c.getString(4)),
                        type = c.getInt(6),
                        dateMillis = c.getLong(7),
                        durationSeconds = c.getLong(8),
                        photoUri = c.getString(9),
                        lookupUri = c.getString(10),
                        accountId = c.getString(11),
                        isRead = c.isNull(12) || c.getInt(12) != 0,
                    )
                }
            }
        }
        return out
    }

    fun delete(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return runCatching {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN (${ids.joinToString(",")})", null,
            )
        }.getOrDefault(0)
    }

    fun clearAll(): Int = runCatching { context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) }.getOrDefault(0)

    /** Marks missed calls as seen and read (clears the "new" state other apps show). */
    fun markMissedRead() {
        runCatching {
            val values = ContentValues().apply {
                put(CallLog.Calls.IS_READ, 1)
                put(CallLog.Calls.NEW, 0)
            }
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI, values,
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.IS_READ} = 0 OR ${CallLog.Calls.NEW} = 1)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            )
        }
    }
}

/** Human label for a ContactsContract Phone.TYPE (+ custom label). */
fun phoneTypeLabel(type: Int, custom: String?): String? {
    if (!custom.isNullOrBlank()) return custom
    return when (type) {
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Fax"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Work mobile"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
        else -> null
    }
}
