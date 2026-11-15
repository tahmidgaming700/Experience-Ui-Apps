package com.experience.messages.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.experience.messages.data.Recipients

/**
 * "Reply with message" when declining a call (Telecom → default SMS app,
 * android.intent.action.RESPOND_VIA_MESSAGE, protected by SEND_RESPOND_VIA_MESSAGE).
 * Sends the SMS and records it like any other outgoing message.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val recipients = Recipients.fromSchemeSpecificPart(intent.data?.schemeSpecificPart)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", SubscriptionManager.getDefaultSmsSubscriptionId()))
        Thread {
            try {
                if (recipients.isNotEmpty() && !text.isNullOrBlank()) SmsSender.send(this, recipients, text, subId)
            } finally {
                stopSelf(startId)
            }
        }.start()
        return START_NOT_STICKY
    }
}
