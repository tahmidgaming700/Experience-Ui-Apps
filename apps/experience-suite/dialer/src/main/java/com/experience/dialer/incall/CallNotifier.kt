package com.experience.dialer.incall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.experience.dialer.R

/**
 * Incoming-call (heads-up + full-screen intent) and ongoing-call notifications.
 * The incoming channel is silent: Telecom plays the ringtone (see the manifest note
 * on IN_CALL_SERVICE_RINGING).
 */
class CallNotifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val incoming = NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shows incoming calls with Answer and Decline"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the call in progress"
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(incoming, ongoing))
    }

    fun update(calls: List<CallSnapshot>) {
        val top = calls.filter { it.parentId == null && !it.isEnded }
        val ringing = top.firstOrNull { it.isRinging }
        if (ringing != null) {
            nm.notify(ID_INCOMING, incoming(ringing, waiting = top.any { it !== ringing }))
        } else {
            nm.cancel(ID_INCOMING)
        }
        val primary = top.firstOrNull { it.isActive } ?: top.firstOrNull { it.isDialing || it.needsAccount } ?: top.firstOrNull { it.isHeld }
        if (primary != null) nm.notify(ID_ONGOING, ongoing(primary)) else nm.cancel(ID_ONGOING)
    }

    fun cancelAll() {
        nm.cancel(ID_INCOMING)
        nm.cancel(ID_ONGOING)
    }

    private fun showUi(requestCode: Int, action: String? = null, callId: String? = null): PendingIntent {
        val intent = Intent(context, InCallActivity::class.java)
            .setAction(action ?: Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        if (callId != null) intent.putExtra(InCallActivity.EXTRA_CALL_ID, callId)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun broadcast(requestCode: Int, action: String, callId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(InCallActivity.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun incoming(call: CallSnapshot, waiting: Boolean): Notification {
        val subtitle = buildString {
            append(if (waiting) "Incoming call · call waiting" else "Incoming call")
            if (call.name != null && !call.number.isNullOrBlank() && !call.numberHidden) append(" · ").append(call.number)
        }
        return Notification.Builder(context, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(call.title)
            .setContentText(subtitle)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(showUi(10))
            .setFullScreenIntent(showUi(11), true)
            .addAction(action("Decline", broadcast(12, CallActionReceiver.ACTION_DECLINE, call.id)))
            .addAction(action("Answer", showUi(13, InCallActivity.ACTION_ANSWER, call.id)))
            .build()
    }

    private fun ongoing(call: CallSnapshot): Notification {
        val b = Notification.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(call.title)
            .setContentText(CallSnapshot.stateLabel(call.state).let { if (call.isActive) "Ongoing call" else it })
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(showUi(20))
            .addAction(action("Hang up", broadcast(21, CallActionReceiver.ACTION_HANGUP, call.id)))
        if (call.isActive && call.connectTimeMillis > 0) {
            b.setUsesChronometer(true).setWhen(call.connectTimeMillis).setShowWhen(true)
        }
        return b.build()
    }

    private fun action(title: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_call), title, pi).build()

    companion object {
        const val CH_INCOMING = "incoming_calls"
        const val CH_ONGOING = "ongoing_calls"
        private const val ID_INCOMING = 1
        private const val ID_ONGOING = 2
    }
}
