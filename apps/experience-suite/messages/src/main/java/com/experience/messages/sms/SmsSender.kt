package com.experience.messages.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.experience.messages.data.LocalPrefs

/**
 * Sends SMS as the default SMS app. The default app owns persistence: each outgoing
 * message is written to Sms.Outbox first, then moved to Sent or Failed (and its
 * delivery STATUS updated) by [SmsStatusReceiver] from the PendingIntent results.
 */
object SmsSender {
    const val ACTION_SENT = "com.experience.messages.SMS_SENT"
    const val ACTION_DELIVERED = "com.experience.messages.SMS_DELIVERED"
    const val EXTRA_PART = "part"
    const val EXTRA_PARTS = "parts"

    fun smsManager(subId: Int): SmsManager =
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) SmsManager.getSmsManagerForSubscriptionId(subId)
        else SmsManager.getDefault()

    fun isDefaultSmsApp(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /**
     * Records and sends [text] to every address (group SMS is one SMS per recipient,
     * all filed under [threadId]). Returns the message URIs written.
     */
    fun send(context: Context, addresses: List<String>, text: String, subId: Int, threadId: Long? = null): List<Uri> {
        val body = text.trim()
        if (body.isEmpty() || addresses.isEmpty()) return emptyList()
        val thread = threadId ?: Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())
        val delivery = LocalPrefs.get(context).deliveryReports.value
        return addresses.mapNotNull { address ->
            val values = ContentValues().apply {
                put(Sms.ADDRESS, address)
                put(Sms.BODY, body)
                put(Sms.DATE, System.currentTimeMillis())
                put(Sms.READ, 1)
                put(Sms.SEEN, 1)
                put(Sms.THREAD_ID, thread)
                put(Sms.SUBSCRIPTION_ID, subId)
                put(Sms.STATUS, if (delivery) Sms.STATUS_PENDING else Sms.STATUS_NONE)
            }
            val uri = runCatching { context.contentResolver.insert(Sms.Outbox.CONTENT_URI, values) }.getOrNull() ?: return@mapNotNull null
            transmit(context, uri, address, body, subId, delivery)
            uri
        }
    }

    /** Sends a stored message again (failed → outbox). */
    fun resend(context: Context, messageUri: Uri) {
        val c = context.contentResolver.query(messageUri, arrayOf(Sms.ADDRESS, Sms.BODY, Sms.SUBSCRIPTION_ID), null, null, null) ?: return
        val (address, body, subId) = c.use { if (it.moveToFirst()) Triple(it.getString(0), it.getString(1), it.getInt(2)) else return }
        if (address.isNullOrBlank() || body.isNullOrBlank()) return
        val delivery = LocalPrefs.get(context).deliveryReports.value
        context.contentResolver.update(messageUri, ContentValues().apply {
            put(Sms.TYPE, Sms.MESSAGE_TYPE_OUTBOX)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.STATUS, if (delivery) Sms.STATUS_PENDING else Sms.STATUS_NONE)
        }, null, null)
        transmit(context, messageUri, address, body, subId, delivery)
    }

    private fun transmit(context: Context, uri: Uri, address: String, body: String, subId: Int, delivery: Boolean) {
        val manager = runCatching { smsManager(subId) }.getOrElse { SmsManager.getDefault() }
        val parts = manager.divideMessage(body)
        val sent = ArrayList<PendingIntent>()
        val delivered = ArrayList<PendingIntent?>()
        parts.indices.forEach { i ->
            sent += statusIntent(context, ACTION_SENT, uri, i, parts.size)
            // Delivery is reported once, for the last part.
            delivered += if (delivery && i == parts.lastIndex) statusIntent(context, ACTION_DELIVERED, uri, i, parts.size) else null
        }
        try {
            if (parts.size > 1) manager.sendMultipartTextMessage(address, null, parts, sent, ArrayList(delivered))
            else manager.sendTextMessage(address, null, body, sent[0], delivered[0])
        } catch (e: Exception) {
            // Invalid address or radio unavailable: mark failed right away.
            SmsStatusReceiver.markFailed(context, uri, -1)
        }
    }

    private fun statusIntent(context: Context, action: String, uri: Uri, part: Int, parts: Int): PendingIntent {
        val intent = Intent(action, uri, context, SmsStatusReceiver::class.java)
            .putExtra(EXTRA_PART, part).putExtra(EXTRA_PARTS, parts)
        return PendingIntent.getBroadcast(context, part, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
