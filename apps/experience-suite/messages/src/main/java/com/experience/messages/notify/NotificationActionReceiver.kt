package com.experience.messages.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import androidx.core.app.RemoteInput
import com.experience.messages.MainActivity
import com.experience.messages.data.MessagesRepository
import com.experience.messages.sms.SmsSender

/** Direct reply and "Mark as read" from conversation notifications (not exported). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MainActivity.EXTRA_THREAD_ID, -1)
        if (threadId < 0) return
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageNotifier.KEY_REPLY)?.toString()
        val pending = goAsync()
        Thread {
            try {
                val repo = MessagesRepository(context)
                if (intent.action == ACTION_REPLY && !reply.isNullOrBlank()) {
                    val addresses = repo.addressesOf(threadId)
                    if (addresses.isNotEmpty()) {
                        SmsSender.send(context, addresses, reply, SubscriptionManager.getDefaultSmsSubscriptionId(), threadId)
                    }
                }
                repo.markRead(threadId)
                MessageNotifier(context).cancel(threadId)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_REPLY = "com.experience.messages.action.REPLY"
        const val ACTION_MARK_READ = "com.experience.messages.action.MARK_READ"
    }
}
