package com.experience.setupwizard.model

/** Wi-Fi security as advertised in a scan result's capabilities string. */
enum class WifiSecurity(val label: String, val needsPassword: Boolean, val supported: Boolean) {
    OPEN("Open", false, true),
    OWE("Enhanced open", false, true),
    WEP("WEP", true, false),
    PSK("WPA/WPA2", true, true),
    PSK_SAE("WPA2/WPA3", true, true),
    SAE("WPA3", true, true),
    EAP("Enterprise", true, false);

    companion object {
        /** Parses e.g. "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]". */
        fun fromCapabilities(capabilities: String?): WifiSecurity {
            val c = capabilities.orEmpty().uppercase()
            return when {
                "EAP" in c -> EAP
                "SAE" in c && "PSK" in c -> PSK_SAE
                "SAE" in c -> SAE
                "PSK" in c -> PSK
                "OWE" in c -> OWE
                "WEP" in c -> WEP
                else -> OPEN
            }
        }
    }
}

data class WifiNetwork(
    val ssid: String,
    val rssi: Int,
    val security: WifiSecurity,
    val frequencyMhz: Int = 0,
) {
    /** Signal bars 0..4. */
    val level: Int get() = WifiNetworks.level(rssi)
    val is5GHz: Boolean get() = frequencyMhz >= 4900
}

object WifiNetworks {
    /** Maps RSSI to 0..4 bars (-100 dBm or weaker = 0, -55 dBm or stronger = 4). */
    fun level(rssi: Int): Int = when {
        rssi <= -100 -> 0
        rssi >= -55 -> 4
        else -> ((rssi + 100) * 4 / 45).coerceIn(0, 4)
    }

    /**
     * One entry per (SSID, security): access points of the same network collapse to
     * the strongest one. Hidden networks (empty SSID) are dropped. Sorted with the
     * connected network first, then by signal, then by name.
     */
    fun dedupe(networks: List<WifiNetwork>, connectedSsid: String? = null): List<WifiNetwork> =
        networks.filter { it.ssid.isNotBlank() }
            .groupBy { it.ssid to it.security }
            .map { (_, aps) -> aps.maxBy { it.rssi } }
            .sortedWith(compareBy<WifiNetwork>({ it.ssid != connectedSsid }, { -it.level }, { it.ssid.lowercase() }))

    /** WPA passphrases are 8-63 characters, or exactly 64 hex digits (raw PSK). */
    fun isValidPassword(security: WifiSecurity, password: String): Boolean = when (security) {
        WifiSecurity.OPEN, WifiSecurity.OWE -> true
        WifiSecurity.SAE -> password.isNotEmpty() && password.length <= 128
        WifiSecurity.PSK, WifiSecurity.PSK_SAE ->
            password.length in 8..63 || (password.length == 64 && password.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        else -> false
    }

    /** WifiManager reports SSIDs quoted ("\"Home\""); strips quotes and the unknown marker. */
    fun unquote(ssid: String?): String? {
        if (ssid == null || ssid == "<unknown ssid>") return null
        return if (ssid.length >= 2 && ssid.startsWith('"') && ssid.endsWith('"')) ssid.substring(1, ssid.length - 1) else ssid
    }
}
