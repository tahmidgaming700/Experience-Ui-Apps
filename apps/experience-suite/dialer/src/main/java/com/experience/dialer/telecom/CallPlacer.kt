package com.experience.dialer.telecom

import android.os.Build
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/** A call-capable account (usually one per SIM) as shown in the SIM chooser. */
data class AccountChoice(val handle: PhoneAccountHandle, val label: String, val detail: String?)

/**
 * Places calls through Telecom. TelecomManager.placeCall routes emergency numbers to
 * the emergency path regardless of the account chosen, so they always work.
 */
class CallPlacer(private val context: Context) {
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    fun hasCallPermission() =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun isEmergency(number: String): Boolean {
        val n = number.filter { it.isDigit() || it == '+' }
        if (n.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { telephony?.isEmergencyNumber(n) == true }.getOrNull()?.let { return it }
        }
        @Suppress("DEPRECATION")
        return runCatching { PhoneNumberUtils.isEmergencyNumber(n) }.getOrDefault(false)
    }

    /** Call-capable accounts (needs READ_PHONE_STATE; empty without it). */
    @SuppressLint("MissingPermission")
    fun accounts(): List<AccountChoice> = runCatching {
        telecom?.callCapablePhoneAccounts.orEmpty().mapNotNull { handle ->
            val account = telecom?.getPhoneAccount(handle) ?: return@mapNotNull null
            AccountChoice(handle, account.label?.toString() ?: "SIM", account.address?.schemeSpecificPart?.takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    /** The user's default outgoing account for tel: calls, or null when Telecom should ask. */
    @SuppressLint("MissingPermission")
    fun defaultAccount(): PhoneAccountHandle? =
        runCatching { telecom?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) }.getOrNull()

    /** True when the UI should let the user pick a SIM before calling [number]. */
    fun needsAccountChoice(number: String): Boolean =
        !isEmergency(number) && defaultAccount() == null && accounts().size > 1

    /**
     * Stores [handle] as the default outgoing account. TelecomManager
     * .setUserSelectedOutgoingPhoneAccount is a @SystemApi guarded by MODIFY_PHONE_STATE,
     * which the ROM grants to this priv-app; it is reached reflectively and simply
     * fails (returns false) on other builds.
     */
    fun setDefaultAccount(handle: PhoneAccountHandle): Boolean = runCatching {
        TelecomManager::class.java.getMethod("setUserSelectedOutgoingPhoneAccount", PhoneAccountHandle::class.java)
            .invoke(telecom, handle)
        true
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun place(number: String, account: PhoneAccountHandle? = null): Boolean {
        val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
        if (hasCallPermission()) {
            val extras = Bundle()
            if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            return runCatching { telecom?.placeCall(uri, extras); telecom != null }.getOrDefault(false)
        }
        // Without CALL_PHONE an emergency call still has to go through. CALL_PRIVILEGED
        // (granted to Experience Dialer in priv-app) lets us use the privileged action.
        if (isEmergency(number)) {
            return runCatching {
                context.startActivity(
                    Intent("android.intent.action.CALL_PRIVILEGED", uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
        return false
    }

    /** Calls voicemail if a voicemail number is configured; returns false otherwise. */
    @SuppressLint("MissingPermission")
    fun callVoicemail(): Boolean {
        val vm = runCatching {
            telecom?.getVoiceMailNumber(defaultAccount() ?: telecom.callCapablePhoneAccounts.firstOrNull())
        }.getOrNull()
        if (vm.isNullOrBlank() || !hasCallPermission()) return false
        return runCatching {
            telecom?.placeCall(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), Bundle()); true
        }.getOrDefault(false)
    }
}
