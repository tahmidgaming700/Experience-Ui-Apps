package com.experience.settings.ui.pages

import android.os.Build
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.experience.design.components.HeroCard
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.BroadcastEffect
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SettingsObserverEffect
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import com.experience.settings.ui.startSafely

private data class NetworkStatus(val title: String, val detail: String, val online: Boolean)

@Suppress("DEPRECATION") // WifiInfo via connectionInfo is the Android 11 API for the current SSID.
private fun readStatus(context: Context): NetworkStatus {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        ?: return NetworkStatus("Not connected", "No active network", false)
    val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val suffix = if (validated) "" else " · no internet access"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val wm = context.getSystemService(WifiManager::class.java)
            val info = wm.connectionInfo
            // The SSID is only readable with location access (pre-granted in the ROM).
            val ssid = info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
            val numLevels = 5
            val level = info?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.calculateSignalLevel(it.rssi)
                else @Suppress("DEPRECATION") WifiManager.calculateSignalLevel(it.rssi, numLevels)
            }
            val maxLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.maxSignalLevel else numLevels - 1
            val bars = level?.let { "signal ${it}/$maxLevel" }
            val speed = info?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" }
            NetworkStatus(ssid ?: "Wi-Fi", listOfNotNull("Connected via Wi-Fi", bars, speed).joinToString(" · ") + suffix, validated)
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
            val op = context.getSystemService(TelephonyManager::class.java)?.networkOperatorName?.takeIf { it.isNotBlank() }
            NetworkStatus(op ?: "Mobile data", "Connected via mobile network$suffix", validated)
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus("Ethernet", "Connected via cable$suffix", validated)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkStatus("VPN", "Connected through a VPN$suffix", validated)
        else -> NetworkStatus("Connected", "Active network$suffix", validated)
    }
}

@Composable
fun NetworkPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val wm = context.getSystemService(WifiManager::class.java)
    val wifiOn = rememberSystemValue { wm?.isWifiEnabled == true }
    val status = rememberSystemValue { readStatus(context) }
    val airplane = rememberSystemValue { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }
    @Suppress("DEPRECATION")
    BroadcastEffect(WifiManager.WIFI_STATE_CHANGED_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION) {
        wifiOn.refresh(); status.refresh()
    }
    SettingsObserverEffect(listOf(Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON))) { airplane.refresh() }

    PageScaffold("Network & internet", onBack) {
        val s = status.value
        HeroCard(s.title, s.detail, icon = if (s.online) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, modifier = Modifier.padding(top = 8.dp))
        Group("Wi-Fi") {
            if (wm != null) row { i, n ->
                SwitchRow(
                    title = "Wi-Fi", summary = if (wifiOn.value) "On" else "Off", icon = Icons.Outlined.Wifi,
                    checked = wifiOn.value, index = i, count = n,
                    onCheckedChange = { on ->
                        // System apps may still toggle Wi-Fi on Android 11; others get the system panel.
                        @Suppress("DEPRECATION")
                        val ok = runCatching { wm.setWifiEnabled(on) }.getOrDefault(false)
                        if (!ok && !context.startSafely(Intent(Settings.Panel.ACTION_WIFI))) msg("Couldn't change Wi-Fi")
                        wifiOn.refresh()
                    },
                )
            }
            link(context, "Wi-Fi networks", "Choose a network, saved networks, preferences", Icons.Outlined.Router, Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        Group("Mobile & more") {
            link(context, "Mobile network", "SIMs, data, roaming", Icons.Outlined.NetworkCell, Intent(Settings.ACTION_DATA_ROAMING_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            link(context, "Data usage", null, Icons.Outlined.DataUsage, Intent("android.settings.DATA_USAGE_SETTINGS"))
            link(context, "Airplane mode", if (airplane.value) "On" else "Off", Icons.Outlined.AirplanemodeActive, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            link(context, "Hotspot & tethering", null, Icons.Outlined.WifiTethering, Intent("android.settings.TETHER_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            link(context, "VPN", null, Icons.Outlined.VpnKey, Intent(Settings.ACTION_VPN_SETTINGS))
            link(context, "Internet connectivity", "Quick panel", Icons.Outlined.Wifi, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        }
    }
}
