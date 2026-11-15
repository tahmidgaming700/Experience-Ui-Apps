package com.experience.clock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Expiry alarms and notification actions for timers. */
class TimerReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_EXPIRE = "com.experience.clock.action.TIMER_EXPIRE"
        const val ACTION_PAUSE = "com.experience.clock.action.TIMER_PAUSE"
        const val ACTION_RESUME = "com.experience.clock.action.TIMER_RESUME"
        const val ACTION_ADD_MINUTE = "com.experience.clock.action.TIMER_ADD_MINUTE"
        const val ACTION_STOP = "com.experience.clock.action.TIMER_STOP"
        const val EXTRA_ID = "timer_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ACTION_EXPIRE -> TimerRepository.expire(context, id)
                    ACTION_PAUSE -> TimerRepository.pause(context, id)
                    ACTION_RESUME -> TimerRepository.start(context, id)
                    ACTION_ADD_MINUTE -> TimerRepository.addMinute(context, id)
                    ACTION_STOP -> TimerRepository.reset(context, id)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
