package com.experience.messages.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.experience.messages.MainActivity
import com.experience.messages.R
import com.experience.messages.data.ContactLookup
import com.experience.messages.data.MessagesRepository

/**
 * One MessagingStyle notification per conversation (id = thread id) with direct
 * reply and "Mark as read", plus "not sent" and "MMS not downloaded" notices.
 */
class MessageNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply { description = "New text messages" },
            NotificationChannel(CH_FAILED, "Not sent", NotificationManager.IMPORTANCE_HIGH).apply { description = "Messages that could not be sent" },
            NotificationChannel(CH_MMS, "Multimedia messages", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Incoming MMS that cannot be downloaded" },
        ))
    }

    private data class Unread(val sender: String, val body: String, val date: Long)

    private fun unread(threadId: Long): List<Unread> {
        val out = ArrayList<Unread>()
        runCatching {
            context.contentResolver.query(Sms.Inbox.CONTENT_URI, arrayOf(Sms.ADDRESS, Sms.BODY, Sms.DATE),
                "${Sms.THREAD_ID} = ? AND ${Sms.READ} = 0", arrayOf(threadId.toString()), "${Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) out += Unread(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
            }
        }
        runCatching {
            context.contentResolver.query(Mms.Inbox.CONTENT_URI, arrayOf(Mms._ID, Mms.DATE),
                "${Mms.THREAD_ID} = ? AND ${Mms.READ} = 0", arrayOf(threadId.toString()), null)?.use { c ->
                while (c.moveToNext()) out += Unread("", "Multimedia message", c.getLong(1) * 1000)
            }
        }
        return out.sortedBy { it.date }.takeLast(8)
    }

    @android.annotation.SuppressLint("MissingPermission") // POST_NOTIFICATIONS does not exist on API 30.
    fun notifyThread(threadId: Long) {
        ensureChannels()
        if (ActiveThread.threadId == threadId) return
        val messages = unread(threadId)
        if (messages.isEmpty()) { cancel(threadId); return }
        val addresses = runCatching { MessagesRepository(context).addressesOf(threadId) }.getOrDefault(emptyList())
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (addresses.size > 1) {
            style.conversationTitle = ContactLookup.displayName(context, addresses)
            style.isGroupConversation = true
        }
        messages.forEach { m ->
            val address = m.sender.ifBlank { addresses.firstOrNull().orEmpty() }
            val sender = Person.Builder().setName(ContactLookup.lookup(context, address).name ?: address).setKey(address).build()
            style.addMessage(m.body, m.date, sender)
        }
        val replyInput = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_stat_message, "Reply", replyIntent(threadId))
            .addRemoteInput(replyInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val markRead = NotificationCompat.Action.Builder(R.drawable.ic_stat_message, "Mark as read", markReadIntent(threadId))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val n = NotificationCompat.Builder(context, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openThread(threadId))
            .setAutoCancel(true)
            .setWhen(messages.last().date)
            .setShowWhen(true)
            .setNumber(messages.size)
            .addAction(reply)
            .addAction(markRead)
            .build()
        runCatching { nm.notify(TAG_THREAD, threadId.toInt(), n) }
    }

    fun cancel(threadId: Long) = nm.cancel(TAG_THREAD, threadId.toInt())

    @android.annotation.SuppressLint("MissingPermission")
    fun notifySendFailed(messageUri: Uri) {
        ensureChannels()
        val (threadId, address) = runCatching {
            context.contentResolver.query(messageUri, arrayOf(Sms.THREAD_ID, Sms.ADDRESS), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) to c.getString(1).orEmpty() else null
            }
        }.getOrNull() ?: return
        val who = ContactLookup.lookup(context, address).name ?: address
        val n = NotificationCompat.Builder(context, CH_FAILED)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Message not sent")
            .setContentText("To $who. Open the conversation to try again.")
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openThread(threadId))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(TAG_FAILED, threadId.toInt(), n) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun notifyMmsUnsupported(threadId: Long, from: String, sizeBytes: Long) {
        ensureChannels()
        val who = ContactLookup.lookup(context, from).name ?: from.ifBlank { "Unknown sender" }
        val size = if (sizeBytes > 0) " (${(sizeBytes + 1023) / 1024} KB)" else ""
        val n = NotificationCompat.Builder(context, CH_MMS)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Multimedia message from $who")
            .setContentText("Experience Messages can't download MMS yet$size.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "A multimedia message$size arrived from $who. Downloading MMS is not supported by Experience Messages yet, so its content can't be shown."))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openThread(threadId))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(TAG_MMS, threadId.toInt(), n) }
    }

    private fun openThread(threadId: Long): PendingIntent = PendingIntent.getActivity(
        context, threadId.toInt(),
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_THREAD).putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Mutable: RemoteInput results are filled in by the system (mutable by default on API 30). */
    private fun replyIntent(threadId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, threadId.toInt(),
        Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_REPLY).putExtra(MainActivity.EXTRA_THREAD_ID, threadId),
        PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun markReadIntent(threadId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, threadId.toInt(),
        Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_MARK_READ).putExtra(MainActivity.EXTRA_THREAD_ID, threadId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CH_MESSAGES = "messages"
        const val CH_FAILED = "failed"
        const val CH_MMS = "mms"
        const val KEY_REPLY = "reply"
        private const val TAG_THREAD = "thread"
        private const val TAG_FAILED = "failed"
        private const val TAG_MMS = "mms"
    }
}
