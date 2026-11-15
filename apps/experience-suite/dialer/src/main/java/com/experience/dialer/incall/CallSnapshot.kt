package com.experience.dialer.incall

import android.telecom.Call
import android.telecom.PhoneAccountHandle

/** Immutable view of a telecom [Call] for the UI. */
data class CallSnapshot(
    val id: String,
    val state: Int,
    val number: String?,
    val numberHidden: Boolean,
    val name: String?,
    val photoUri: String?,
    val numberLabel: String?,
    val connectTimeMillis: Long,
    val disconnectReason: String?,
    val isIncoming: Boolean,
    val isConference: Boolean,
    val parentId: String?,
    val childIds: List<String>,
    val conferenceableIds: List<String>,
    val canHold: Boolean,
    val canMute: Boolean,
    val canMergeConference: Boolean,
    val canSwapConference: Boolean,
    val canSeparate: Boolean,
    val canDisconnectFromConference: Boolean,
    val cannedResponses: List<String>,
    val selectableAccounts: List<PhoneAccountHandle>,
) {
    /** What the UI shows as the main line: contact name, number or "Private number". */
    val title: String get() = name ?: number?.takeIf { it.isNotBlank() && !numberHidden } ?: if (isConference) "Conference call" else "Private number"

    val isRinging get() = state == Call.STATE_RINGING || state == STATE_SIMULATED_RINGING
    val isActive get() = state == Call.STATE_ACTIVE
    val isHeld get() = state == Call.STATE_HOLDING
    val isDialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_NEW || state == STATE_PULLING
    val isEnded get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
    val needsAccount get() = state == Call.STATE_SELECT_PHONE_ACCOUNT

    companion object {
        // Inlined values of Call.STATE_PULLING_CALL (API 25), STATE_SIMULATED_RINGING (API 29+ hidden until 30).
        const val STATE_PULLING = 11
        const val STATE_SIMULATED_RINGING = 13
        const val STATE_AUDIO_PROCESSING = 12

        fun stateLabel(state: Int): String = when (state) {
            Call.STATE_NEW, Call.STATE_CONNECTING -> "Connecting…"
            Call.STATE_DIALING -> "Calling…"
            Call.STATE_RINGING, STATE_SIMULATED_RINGING -> "Incoming call"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_ACTIVE -> "Active"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Choose a SIM"
            Call.STATE_DISCONNECTING -> "Ending…"
            Call.STATE_DISCONNECTED -> "Call ended"
            STATE_PULLING -> "Transferring…"
            STATE_AUDIO_PROCESSING -> "Connecting…"
            else -> ""
        }
    }
}
