package com.experience.messages.mms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.telephony.SubscriptionManager
import com.experience.messages.data.LocalPrefs
import com.experience.messages.data.MessagesRepository
import com.experience.messages.notify.MessageNotifier

/**
 * WAP_PUSH_DELIVER for application/vnd.wap.mms-message (default SMS app only,
 * protected by BROADCAST_WAP_PUSH).
 *
 * MMS download is NOT implemented: the m-notification-ind is decoded and recorded
 * in the MMS provider as an undownloaded message (m_type 130, with its content
 * location and transaction id, so a future downloader can fetch it), and the user
 * is told that the message cannot be downloaded yet. No NotifyResp-ind is sent, so
 * the MMSC keeps the message until it expires.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID))
        val pending = goAsync()
        Thread {
            try {
                handle(context, pdu, subId)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, pdu: ByteArray, subId: Int) {
        val ind = MmsNotificationParser.parse(pdu) ?: return // delivery/read reports etc. are ignored
        val from = ind.from.orEmpty()
        if (from.isNotEmpty() && runCatching { BlockedNumberContract.isBlocked(context, from) }.getOrDefault(false)) return
        val resolver = context.contentResolver
        // Carriers resend notifications; keep one row per transaction id.
        val duplicate = runCatching {
            resolver.query(Mms.CONTENT_URI, arrayOf(Mms._ID), "${Mms.TRANSACTION_ID} = ?", arrayOf(ind.transactionId), null)?.use { it.count > 0 }
        }.getOrNull() == true
        if (duplicate) return

        val threadId = if (from.isNotEmpty()) runCatching { Telephony.Threads.getOrCreateThreadId(context, from) }.getOrNull() else null
        val nowSeconds = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            if (threadId != null) put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, MessagesRepository.MESSAGE_TYPE_NOTIFICATION_IND)
            put(Mms.CONTENT_LOCATION, ind.contentLocation)
            put(Mms.TRANSACTION_ID, ind.transactionId)
            put(Mms.MESSAGE_SIZE, ind.messageSize)
            put(Mms.EXPIRY, if (ind.expiryRelative) nowSeconds + ind.expiry else ind.expiry)
            ind.subject?.let { put(Mms.SUBJECT, it); put(Mms.SUBJECT_CHARSET, 106) }
            put(Mms.MMS_VERSION, ind.mmsVersion)
            put(Mms.DATE, nowSeconds)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = runCatching { resolver.insert(Mms.CONTENT_URI, values) }.getOrNull() ?: return
        if (from.isNotEmpty()) {
            val addr = ContentValues().apply {
                put(Mms.Addr.ADDRESS, from)
                put(Mms.Addr.TYPE, 137) // PduHeaders.FROM
                put(Mms.Addr.CHARSET, 106)
            }
            runCatching { resolver.insert(Uri.parse("content://mms/${uri.lastPathSegment}/addr"), addr) }
        }
        val storedThread = threadId ?: runCatching {
            resolver.query(uri, arrayOf(Mms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
        }.getOrNull() ?: return
        LocalPrefs.get(context).setArchived(storedThread, false)
        MessageNotifier(context).notifyMmsUnsupported(storedThread, from, ind.messageSize)
    }
}
