package com.experience.dialer.incall

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Telecom binds this service while there are calls (the app holds the DIALER role and
 * declares IN_CALL_SERVICE_UI). It feeds [CallManager], keeps the call notifications
 * and the proximity wake lock in sync, and brings up [InCallActivity].
 */
class ExperienceInCallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifier: CallNotifier
    private lateinit var proximity: ProximityController

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        notifier = CallNotifier(this).also { it.ensureChannels() }
        proximity = ProximityController(this)
        scope.launch { CallManager.calls.collect { notifier.update(it) } }
        scope.launch {
            combine(CallManager.calls, CallManager.audioState) { c, a -> c to a }.collect { (c, a) -> proximity.update(c, a) }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        lookupCaller(call)
        // Outgoing calls (and calls waiting for a SIM choice) open the in-call screen
        // directly; incoming calls use the full-screen notification so that the
        // system decides between heads-up and full screen.
        if (call.state != Call.STATE_RINGING && call.state != CallSnapshot.STATE_SIMULATED_RINGING) showUi()
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.setAudioState(audioState)
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        CallManager.setCanAddCall(canAddCall)
    }

    override fun onBringToForeground(showDialpad: Boolean) = showUi()

    private fun showUi() {
        runCatching {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun lookupCaller(call: Call) {
        val number = CallManager.numberOf(call) ?: return
        scope.launch {
            val info = withContext(Dispatchers.IO) { CallerInfoLookup.lookup(this@ExperienceInCallService, number) }
            if (info != null) CallManager.putCallerInfo(number, info)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        proximity.release()
        notifier.cancelAll()
        if (CallManager.service === this) CallManager.service = null
        CallManager.setAudioState(null)
        super.onDestroy()
    }
}
