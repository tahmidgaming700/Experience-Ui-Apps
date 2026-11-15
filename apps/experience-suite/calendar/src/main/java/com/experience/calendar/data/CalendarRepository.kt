package com.experience.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import com.experience.calendar.logic.Durations
import com.experience.calendar.logic.EventDraft
import java.util.TimeZone

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
    val accessLevel: Int,
) {
    val writable get() = accessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR
}

/** One expanded occurrence from the Instances table. */
data class EventInstance(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
    val calendarId: Long,
    /** Julian day numbers (local time) covered, as computed by the provider. */
    val startDay: Int,
    val endDay: Int,
    val recurring: Boolean,
)

data class EventDetails(
    val draft: EventDraft,
    val rrule: String?,
    val color: Int,
    val calendarName: String,
    val organizer: String?,
    val canModify: Boolean,
)

/**
 * Calendar Provider access. All methods block; call them off the main thread.
 * Requires READ_CALENDAR / WRITE_CALENDAR.
 */
class CalendarRepository(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    companion object {
        const val LOCAL_ACCOUNT_NAME = "Experience"
        const val LOCAL_COLOR = 0xFF4355B9.toInt()

        /** Calendars URI that acts as a sync adapter for the local account (needed to create calendars). */
        fun syncAdapterUri(uri: Uri, accountName: String = LOCAL_ACCOUNT_NAME, accountType: String = CalendarContract.ACCOUNT_TYPE_LOCAL): Uri =
            uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
                .build()
    }

    fun calendars(): List<CalendarInfo> {
        val proj = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.VISIBLE, Calendars.CALENDAR_ACCESS_LEVEL,
        )
        return resolver.query(Calendars.CONTENT_URI, proj, null, null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}")?.use { c ->
            buildList {
                while (c.moveToNext()) add(
                    CalendarInfo(
                        id = c.getLong(0), name = c.getString(1) ?: "", accountName = c.getString(2) ?: "",
                        accountType = c.getString(3) ?: "", color = c.getInt(4), visible = c.getInt(5) != 0, accessLevel = c.getInt(6),
                    )
                )
            }
        } ?: emptyList()
    }

    fun setVisible(calendarId: Long, visible: Boolean) {
        val cv = ContentValues().apply { put(Calendars.VISIBLE, if (visible) 1 else 0) }
        resolver.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId), cv, null, null)
    }

    /** Creates the on-device "Experience" calendar when no writable calendar exists. Returns its id. */
    fun ensureWritableCalendar(displayName: String): Long? {
        calendars().firstOrNull { it.writable }?.let { return it.id }
        val cv = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, LOCAL_ACCOUNT_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(Calendars.CALENDAR_COLOR, LOCAL_COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(Calendars.CAN_ORGANIZER_RESPOND, 0)
        }
        val uri = runCatching { resolver.insert(syncAdapterUri(Calendars.CONTENT_URI), cv) }.getOrNull() ?: return null
        return ContentUris.parseId(uri)
    }

    /** Expanded occurrences overlapping [startMillis, endMillis) from visible calendars. */
    fun instances(startMillis: Long, endMillis: Long): List<EventInstance> {
        val builder = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val proj = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
            Instances.DISPLAY_COLOR, Instances.EVENT_LOCATION, Instances.CALENDAR_ID,
            Instances.START_DAY, Instances.END_DAY, Instances.RRULE, Instances.RDATE,
        )
        return resolver.query(
            builder.build(), proj, "${Instances.VISIBLE} = 1 AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})",
            null, "${Instances.BEGIN} ASC, ${Instances.END} DESC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(
                    EventInstance(
                        eventId = c.getLong(0), title = c.getString(1) ?: "", begin = c.getLong(2), end = c.getLong(3),
                        allDay = c.getInt(4) != 0, color = c.getInt(5), location = c.getString(6) ?: "",
                        calendarId = c.getLong(7), startDay = c.getInt(8), endDay = c.getInt(9),
                        recurring = !c.getString(10).isNullOrEmpty() || !c.getString(11).isNullOrEmpty(),
                    )
                )
            }
        } ?: emptyList()
    }

    fun reminders(eventId: Long): List<Int> =
        resolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC")
            ?.use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } } ?: emptyList()

    fun event(eventId: Long): EventDetails? {
        val proj = arrayOf(
            Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.ALL_DAY,
            Events.DTSTART, Events.DTEND, Events.DURATION, Events.EVENT_TIMEZONE, Events.RRULE, Events.DISPLAY_COLOR,
            Events.CALENDAR_DISPLAY_NAME, Events.ORGANIZER, Events.CALENDAR_ACCESS_LEVEL,
        )
        val details = resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), proj, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val start = c.getLong(6)
            val end = if (c.isNull(7)) start + (Durations.parseMillis(c.getString(8)) ?: 3_600_000L) else c.getLong(7)
            val rrule = c.getString(10)
            val draft = EventDraft.fromStored(
                id = c.getLong(0), calendarId = c.getLong(1), title = c.getString(2) ?: "", description = c.getString(3) ?: "",
                location = c.getString(4) ?: "", allDay = c.getInt(5) != 0, dtStart = start, dtEnd = end,
                timeZone = c.getString(9), rrule = rrule, reminders = emptyList(),
            )
            EventDetails(draft, rrule, c.getInt(11), c.getString(12) ?: "", c.getString(13), c.getInt(14) >= Calendars.CAL_ACCESS_CONTRIBUTOR)
        } ?: return null
        return details.copy(draft = details.draft.copy(reminders = reminders(eventId)))
    }

    private fun Map<String, Any?>.toContentValues() = ContentValues().also { cv ->
        forEach { (k, v) ->
            when (v) {
                null -> cv.putNull(k)
                is Long -> cv.put(k, v)
                is Int -> cv.put(k, v)
                is String -> cv.put(k, v)
                is Boolean -> cv.put(k, v)
                else -> cv.put(k, v.toString())
            }
        }
    }

    /** Inserts or updates [draft] (whole series for recurring events) and replaces its reminders. */
    fun save(draft: EventDraft): Long {
        val cv = draft.toValues().toContentValues()
        val id = if (draft.id == 0L) {
            ContentUris.parseId(resolver.insert(Events.CONTENT_URI, cv) ?: error("insert failed"))
        } else {
            resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, draft.id), cv, null, null)
            resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(draft.id.toString()))
            draft.id
        }
        draft.reminders.distinct().forEach { minutes ->
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, id)
                put(Reminders.MINUTES, minutes)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
        return id
    }

    fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    /** Cancels a single occurrence of a recurring event by inserting a cancelled exception. */
    fun deleteOccurrence(eventId: Long, instanceBegin: Long) {
        val cv = ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
            put(Events.STATUS, Events.STATUS_CANCELED)
        }
        resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eventId), cv)
    }
}
