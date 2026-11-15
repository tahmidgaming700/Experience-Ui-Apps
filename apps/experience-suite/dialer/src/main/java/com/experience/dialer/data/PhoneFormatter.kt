package com.experience.dialer.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/** Formats numbers for display using the network/SIM country (falls back to the locale). */
class PhoneFormatter(context: Context) {
    val countryIso: String = run {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = runCatching { tm?.networkCountryIso }.getOrNull().takeUnless { it.isNullOrBlank() }
            ?: runCatching { tm?.simCountryIso }.getOrNull().takeUnless { it.isNullOrBlank() }
            ?: Locale.getDefault().country
        iso.uppercase(Locale.ROOT)
    }

    fun format(number: String): String {
        if (number.isBlank()) return number
        // Partial input with service codes (*#) is left untouched.
        if (number.any { it == '*' || it == '#' || it == ',' || it == ';' }) return number
        return runCatching { PhoneNumberUtils.formatNumber(number, countryIso) }.getOrNull() ?: number
    }
}
