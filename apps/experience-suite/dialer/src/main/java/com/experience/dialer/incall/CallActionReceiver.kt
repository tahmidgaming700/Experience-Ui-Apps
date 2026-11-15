package com.experience.dialer.incall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Decline / hang up from the call notifications (not exported). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(InCallActivity.EXTRA_CALL_ID) ?: return
        when (intent.action) {
            ACTION_DECLINE -> CallManager.reject(id)
            ACTION_HANGUP -> CallManager.hangup(id)
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.experience.dialer.action.DECLINE"
        const val ACTION_HANGUP = "com.experience.dialer.action.HANGUP"
    }
}
