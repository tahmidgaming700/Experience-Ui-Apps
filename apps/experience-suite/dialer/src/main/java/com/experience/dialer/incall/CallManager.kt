package com.experience.dialer.incall

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.IdentityHashMap

/**
 * Process-wide registry of the calls Telecom hands to [ExperienceInCallService].
 *
 * Telecom delivers every Call callback on the main thread, and all mutation here
 * happens there as well; the UI observes immutable [CallSnapshot]s through [calls].
 */
object CallManager {
    private val live = LinkedHashMap<String, Call>()
    private val ids = IdentityHashMap<Call, String>()
    private var nextId = 1
    private val callerInfo = HashMap<String, CallerInfo>()

    private val _calls = MutableStateFlow<List<CallSnapshot>>(emptyList())
    /** All calls Telecom currently knows about, including conference children. */
    val calls: StateFlow<List<CallSnapshot>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audio.asStateFlow()

    private val _canAddCall = MutableStateFlow(false)
    val canAddCall: StateFlow<Boolean> = _canAddCall.asStateFlow()

    /** Last call that ended, kept briefly so the UI can show "Call ended". */
    private val _lastEnded = MutableStateFlow<CallSnapshot?>(null)
    val lastEnded: StateFlow<CallSnapshot?> = _lastEnded.asStateFlow()

    internal var service: ExperienceInCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onChildrenChanged(call: Call, children: List<Call>) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = publish()
        override fun onCannedTextResponsesLoaded(call: Call, cannedTextResponses: List<String>) = publish()
        override fun onCallDestroyed(call: Call) = publish()
    }

    internal fun add(call: Call) {
        val id = "c${nextId++}"
        ids[call] = id
        live[id] = call
        call.registerCallback(callback)
        publish()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        val id = ids.remove(call) ?: return
        val snapshot = _calls.value.firstOrNull { it.id == id }
        live.remove(id)
        if (snapshot != null && snapshot.parentId == null) _lastEnded.value = snapshot.copy(state = Call.STATE_DISCONNECTED)
        publish()
    }

    internal fun setAudioState(state: CallAudioState?) { _audio.value = state }
    internal fun setCanAddCall(can: Boolean) { _canAddCall.value = can }

    internal fun putCallerInfo(number: String, info: CallerInfo) {
        callerInfo[number] = info
        publish()
    }

    fun clearLastEnded() { _lastEnded.value = null }

    fun numberOf(call: Call): String? = call.details.handle?.schemeSpecificPart

    internal fun publish() {
        _calls.value = live.map { (id, call) -> snapshot(id, call) }
    }

    @Suppress("DEPRECATION") // Call.getState(): Call.Details.getState() is API 31.
    private fun snapshot(id: String, call: Call): CallSnapshot {
        val d = call.details
        val number = d.handle?.schemeSpecificPart
        val info = number?.let { callerInfo[it] }
        val hidden = d.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val accounts: List<PhoneAccountHandle> = runCatching {
            d.intentExtras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
        }.getOrNull().orEmpty()
        return CallSnapshot(
            id = id,
            state = call.state,
            number = number,
            numberHidden = hidden,
            name = info?.name ?: d.callerDisplayName?.takeIf { it.isNotBlank() && d.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED }
                ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.contactDisplayName else null),
            photoUri = info?.photoUri,
            numberLabel = info?.label,
            connectTimeMillis = d.connectTimeMillis,
            disconnectReason = d.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            isIncoming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                d.callDirection == Call.Details.DIRECTION_INCOMING,
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            parentId = call.parent?.let { ids[it] },
            childIds = call.children.mapNotNull { ids[it] },
            conferenceableIds = call.conferenceableCalls.mapNotNull { ids[it] },
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMute = d.can(Call.Details.CAPABILITY_MUTE),
            canMergeConference = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwapConference = d.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            canSeparate = d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
            canDisconnectFromConference = d.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE),
            cannedResponses = call.cannedTextResponses.orEmpty(),
            selectableAccounts = accounts,
        )
    }

    private fun call(id: String) = live[id]

    // ---- Actions -------------------------------------------------------------------

    fun answer(id: String) { call(id)?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun reject(id: String, message: String? = null) {
        call(id)?.reject(message != null, message)
    }

    fun hangup(id: String) { call(id)?.disconnect() }

    /** Ends the active (and held) calls, then answers [ringingId]. */
    fun endOthersAndAnswer(ringingId: String) {
        live.filterKeys { it != ringingId }.values
            .filter { it.parent == null }
            .forEach { it.disconnect() }
        answer(ringingId)
    }

    fun hold(id: String, hold: Boolean) {
        val c = call(id) ?: return
        if (hold) c.hold() else c.unhold()
    }

    fun playDtmf(id: String, key: Char) { call(id)?.playDtmfTone(key) }
    fun stopDtmf(id: String) { call(id)?.stopDtmfTone() }

    fun conference(id: String, otherId: String) {
        val other = call(otherId) ?: return
        call(id)?.conference(other)
    }

    fun mergeConference(id: String) { call(id)?.mergeConference() }
    fun swapConference(id: String) { call(id)?.swapConference() }
    fun splitFromConference(id: String) { call(id)?.splitFromConference() }

    fun selectAccount(id: String, handle: PhoneAccountHandle, setDefault: Boolean) {
        call(id)?.phoneAccountSelected(handle, setDefault)
    }

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }
    fun setAudioRoute(route: Int) { service?.setAudioRoute(route) }
}
