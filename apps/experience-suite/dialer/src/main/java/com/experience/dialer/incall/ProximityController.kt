package com.experience.dialer.incall

import android.content.Context
import android.os.PowerManager
import android.telecom.CallAudioState

/**
 * Turns the screen off when the phone is held to the ear: a proximity wake lock is
 * held while a non-ringing call is in progress and audio goes to the earpiece.
 */
class ProximityController(context: Context) {
    private val wakeLock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)?.let { pm ->
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "experience:incall-proximity")
        } else null
    }

    fun update(calls: List<CallSnapshot>, audio: CallAudioState?) {
        val top = calls.filter { it.parentId == null && !it.isEnded }
        val inCall = top.isNotEmpty() && top.none { it.isRinging } && top.any { it.isActive || it.isDialing }
        val earpiece = audio == null || audio.route == CallAudioState.ROUTE_EARPIECE
        set(inCall && earpiece)
    }

    private fun set(on: Boolean) {
        val wl = wakeLock ?: return
        if (on && !wl.isHeld) {
            wl.acquire(4 * 60 * 60 * 1000L)
        } else if (!on && wl.isHeld) {
            wl.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    fun release() = set(false)
}
