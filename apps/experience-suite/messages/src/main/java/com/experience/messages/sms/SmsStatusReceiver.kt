package com.experience.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import android.telephony.SmsMessage
import com.experience.messages.notify.MessageNotifier

/**
 * Results of SmsManager sends (not exported; reached only through our PendingIntents).
 * Moves the outbox row to Sent / Failed and records delivery reports.
 */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    SmsSender.ACTION_SENT -> onSent(context, uri, intent)
                    SmsSender.ACTION_DELIVERED -> onDelivered(context, uri, intent)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun onSent(context: Context, uri: Uri, intent: Intent) {
        val part = intent.getIntExtra(SmsSender.EXTRA_PART, 0)
        val parts = intent.getIntExtra(SmsSender.EXTRA_PARTS, 1)
        if (resultCode != Activity.RESULT_OK) {
            markFailed(context, uri, intent.getIntExtra("errorCode", resultCode))
            return
        }
        if (part != parts - 1) return
        // Last part sent; keep a failure recorded by an earlier part.
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(Sms.TYPE, Sms.MESSAGE_TYPE_SENT) },
                "${Sms.TYPE} != ?", arrayOf(Sms.MESSAGE_TYPE_FAILED.toString()))
        }
    }

    private fun onDelivered(context: Context, uri: Uri, intent: Intent) {
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val format = intent.getStringExtra("format")
        val message = runCatching { SmsMessage.createFromPdu(pdu, format) }.getOrNull() ?: return
        // TP-Status (3GPP TS 23.040 9.2.3.15): 0x00-0x1F complete, 0x20-0x3F still trying, 0x40+ failed.
        val tpStatus = message.status
        val status = when {
            tpStatus < 0x20 -> Sms.STATUS_COMPLETE
            tpStatus < 0x40 -> Sms.STATUS_PENDING
            else -> Sms.STATUS_FAILED
        }
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Sms.STATUS, status) }, null, null) }
    }

    companion object {
        fun markFailed(context: Context, uri: Uri, errorCode: Int) {
            runCatching {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED)
                    put(Sms.ERROR_CODE, errorCode)
                }, null, null)
            }
            MessageNotifier(context).notifySendFailed(uri)
        }
    }
}
