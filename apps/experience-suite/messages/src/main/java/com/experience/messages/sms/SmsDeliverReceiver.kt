package com.experience.messages.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.BlockedNumberContract
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.telephony.SubscriptionManager
import com.experience.messages.data.LocalPrefs
import com.experience.messages.notify.MessageNotifier

/**
 * SMS_DELIVER (sent only to the default SMS app, protected by BROADCAST_SMS).
 * Joins multipart PDUs, drops blocked senders, stores the message in Sms.Inbox and
 * posts the conversation notification.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val pending = goAsync()
        Thread {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val first = parts.first()
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        // The framework already filters blocked numbers; checking again covers numbers
        // blocked between dispatch and delivery. The default SMS app may query this.
        if (runCatching { BlockedNumberContract.isBlocked(context, address) }.getOrDefault(false)) return

        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID))
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull()
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.DATE_SENT, first.timestampMillis)
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
            put(Sms.PROTOCOL, first.protocolIdentifier)
            put(Sms.REPLY_PATH_PRESENT, if (first.isReplyPathPresent) 1 else 0)
            first.serviceCenterAddress?.let { put(Sms.SERVICE_CENTER, it) }
            first.pseudoSubject?.takeIf { it.isNotBlank() }?.let { put(Sms.SUBJECT, it) }
            put(Sms.SUBSCRIPTION_ID, subId)
            if (threadId != null) put(Sms.THREAD_ID, threadId)
        }
        val uri = runCatching { context.contentResolver.insert(Sms.Inbox.CONTENT_URI, values) }.getOrNull() ?: return
        val storedThread = threadId ?: runCatching {
            context.contentResolver.query(uri, arrayOf(Sms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
        }.getOrNull() ?: return
        // A new message brings an archived conversation back, like other messaging apps.
        LocalPrefs.get(context).setArchived(storedThread, false)
        MessageNotifier(context).notifyThread(storedThread)
    }
}
