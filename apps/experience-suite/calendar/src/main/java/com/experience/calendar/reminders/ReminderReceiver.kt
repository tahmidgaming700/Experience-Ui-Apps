package com.experience.calendar.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import android.text.format.DateUtils
import com.experience.calendar.MainActivity
import com.experience.calendar.R
import com.experience.design.components.hasPermissions

/**
 * Event reminders.
 *
 * The Calendar Provider schedules its own alarms from the Reminders table and, when one is
 * due, broadcasts android.intent.action.EVENT_REMINDER (data content://com.android.calendar/…;
 * the action is exempt from Android 8+ implicit-broadcast limits). We then read the due rows
 * from CalendarAlerts, post a notification for each and mark them FIRED; dismissing the
 * notification (or its action) marks the alert DISMISSED so the provider stops tracking it.
 */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "event_reminders"
        const val ACTION_DISMISS = "com.experience.calendar.action.DISMISS_ALERT"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = context.getString(R.string.channel_reminders_desc) }
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!context.hasPermissions(listOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR))) return
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ACTION_DISMISS -> dismiss(context, intent.getLongExtra(EXTRA_ALERT_ID, -1), intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                    else -> postDueAlerts(context)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun dismiss(context: Context, alertId: Long, notificationId: Int) {
        if (alertId < 0) return
        val cv = ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED) }
        runCatching { context.contentResolver.update(ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId), cv, null, null) }
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }

    private fun postDueAlerts(context: Context) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val proj = arrayOf(
            CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END,
            CalendarAlerts.TITLE, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY,
        )
        val sel = "${CalendarAlerts.STATE} = ? AND ${CalendarAlerts.ALARM_TIME} <= ?"
        val args = arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), now.toString())
        val nm = context.getSystemService(NotificationManager::class.java)
        context.contentResolver.query(CalendarAlerts.CONTENT_URI, proj, sel, args, "${CalendarAlerts.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val alertId = c.getLong(0)
                val eventId = c.getLong(1)
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val title = c.getString(4).takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.untitled_event)
                val location = c.getString(5)
                val allDay = c.getInt(6) != 0
                val notificationId = (alertId % Int.MAX_VALUE).toInt()

                val flags = if (allDay) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_UTC else DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
                val whenText = DateUtils.formatDateRange(context, begin, end, flags)
                val text = listOfNotNull(whenText, location?.takeIf { it.isNotBlank() }).joinToString(" · ")

                val open = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
                    .setClass(context, MainActivity::class.java)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val dismissIntent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_DISMISS)
                    .putExtra(EXTRA_ALERT_ID, alertId).putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val dismissPi = PendingIntent.getBroadcast(context, notificationId, dismissIntent, piFlags)

                val n = android.app.Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_event)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setCategory(android.app.Notification.CATEGORY_EVENT)
                    .setWhen(begin)
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(context, notificationId, open, piFlags))
                    .setDeleteIntent(dismissPi)
                    .addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.dismiss), dismissPi).build())
                    .build()
                nm.notify(notificationId, n)

                val cv = ContentValues().apply {
                    put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED)
                    put(CalendarAlerts.RECEIVED_TIME, now)
                    put(CalendarAlerts.NOTIFY_TIME, now)
                }
                runCatching { context.contentResolver.update(ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId), cv, null, null) }
            }
        }
    }
}
