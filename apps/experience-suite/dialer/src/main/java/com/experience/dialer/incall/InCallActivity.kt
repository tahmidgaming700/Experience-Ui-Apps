package com.experience.dialer.incall

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.experience.design.ExperienceActivity

/**
 * Full-screen call UI. Shown over the lock screen (showWhenLocked / turnScreenOn in
 * the manifest) for incoming calls via the notification's full-screen intent, and
 * started directly by [ExperienceInCallService] for outgoing calls.
 */
class InCallActivity : ExperienceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            intent.getStringExtra(EXTRA_CALL_ID)?.let { CallManager.answer(it) }
        }
    }

    @Composable
    override fun Content() {
        InCallScreen(onFinished = { finishAndRemoveTask() })
    }

    companion object {
        const val EXTRA_CALL_ID = "com.experience.dialer.extra.CALL_ID"
        const val ACTION_ANSWER = "com.experience.dialer.action.ANSWER"
    }
}
