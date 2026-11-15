package com.experience.dialer.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager

/** Dialer service codes typed on the keypad. */
object SpecialCodes {
    private val secretCode = Regex("^\\*#\\*#([0-9]+)#\\*#\\*$")

    /** "*#*#4636#*#*" → "4636". */
    fun secretCodeOf(input: String): String? = secretCode.find(input)?.groupValues?.get(1)

    fun isDeviceIdCode(input: String) = input == "*#06#"

    /**
     * Broadcasts android.provider.Telephony.SECRET_CODE via TelephonyManager, which is
     * permitted for the default dialer. Returns false when not allowed.
     */
    fun sendSecretCode(context: Context, code: String): Boolean = runCatching {
        context.getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(code); true
    }.getOrDefault(false)

    /**
     * IMEI/MEID per modem. Requires READ_PRIVILEGED_PHONE_STATE (priv-app in the ROM);
     * returns an empty list elsewhere.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun deviceIds(context: Context): List<String> {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val count = runCatching { tm.activeModemCount }.getOrDefault(1)
        return (0 until count).mapNotNull { slot ->
            runCatching { tm.getImei(slot) }.getOrNull()?.let { "IMEI${if (count > 1) " ${slot + 1}" else ""}: $it" }
                ?: runCatching { tm.getMeid(slot) }.getOrNull()?.let { "MEID${if (count > 1) " ${slot + 1}" else ""}: $it" }
        }
    }
}
