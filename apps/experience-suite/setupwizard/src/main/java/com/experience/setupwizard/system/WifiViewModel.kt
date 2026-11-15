package com.experience.setupwizard.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import com.experience.setupwizard.model.WifiNetwork
import com.experience.setupwizard.model.WifiNetworks
import com.experience.setupwizard.model.WifiSecurity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WifiUiState(
    val enabled: Boolean = false,
    val enabling: Boolean = false,
    val scanning: Boolean = false,
    val networks: List<WifiNetwork> = emptyList(),
    val connectedSsid: String? = null,
    val connectingSsid: String? = null,
    val error: String? = null,
)

/**
 * Wi-Fi for the setup wizard through [WifiManager]. On Android 11 the deprecated
 * setWifiEnabled / addNetwork / enableNetwork calls still work for privileged system
 * apps, and NETWORK_SETTINGS / NETWORK_SETUP_WIZARD give scan results without the
 * location permission. Elsewhere the page falls back to the Wi-Fi settings panel.
 */
@Suppress("DEPRECATION")
class WifiViewModel(app: Application) : AndroidViewModel(app) {

    private val wifi = app.getSystemService(WifiManager::class.java)
    private val _state = MutableStateFlow(WifiUiState())
    val state: StateFlow<WifiUiState> = _state.asStateFlow()

    /** True when scan results are visible to us (privileged, or location granted). */
    val canScan: Boolean
        get() = listOf(PERMISSION_NETWORK_SETTINGS, PERMISSION_SETUP_WIZARD, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { getApplication<Application>().checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> _state.update { it.copy(scanning = false) }
                WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                    val error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1)
                    if (error == WifiManager.ERROR_AUTHENTICATING) {
                        _state.update { it.copy(connectingSsid = null, error = "Wrong password for ${it.connectingSsid ?: "this network"}") }
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    if (info?.isConnected == true) _state.update { it.copy(connectingSsid = null) }
                }
            }
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        }
        app.registerReceiver(receiver, filter)
        refresh()
        scan()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
    }

    // Scan results are read only when canScan confirmed a permission that grants them.
    @SuppressLint("MissingPermission")
    fun refresh() {
        val enabled = wifi.isWifiEnabled
        val info = wifi.connectionInfo
        val connected = if (info != null && info.supplicantState == SupplicantState.COMPLETED) WifiNetworks.unquote(info.ssid) else null
        val results = if (enabled && canScan) runCatching { wifi.scanResults }.getOrDefault(emptyList()) else emptyList()
        val networks = results.map { WifiNetwork(it.SSID.orEmpty(), it.level, WifiSecurity.fromCapabilities(it.capabilities), it.frequency) }
        _state.update {
            it.copy(
                enabled = enabled,
                enabling = it.enabling && !enabled,
                networks = WifiNetworks.dedupe(networks, connected),
                connectedSsid = connected,
            )
        }
    }

    fun setEnabled(on: Boolean) {
        // Returns false for non-system apps targeting Android 10+.
        if (wifi.setWifiEnabled(on)) {
            _state.update { it.copy(enabling = on, error = null) }
        } else {
            _state.update { it.copy(error = "Couldn't turn Wi-Fi ${if (on) "on" else "off"}. Use Wi-Fi settings instead.") }
        }
    }

    fun scan() {
        if (wifi.isWifiEnabled && wifi.startScan()) _state.update { it.copy(scanning = true) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Adds and connects to [network]; WPA2/WPA3-Personal, open and enhanced-open only. */
    fun connect(network: WifiNetwork, password: String) {
        val config = WifiConfiguration().apply {
            SSID = "\"${network.ssid}\""
            when (network.security) {
                WifiSecurity.OPEN -> setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN)
                WifiSecurity.OWE -> setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE)
                // Transition-mode networks accept WPA2 clients; WPA2 is supported by every device.
                WifiSecurity.PSK, WifiSecurity.PSK_SAE -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK)
                    preSharedKey = if (password.length == 64 && password.all { it.isLetterOrDigit() }) password else "\"$password\""
                }
                WifiSecurity.SAE -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                    preSharedKey = "\"$password\""
                }
                else -> {
                    _state.update { it.copy(error = "${network.security.label} networks can be added in Wi-Fi settings.") }
                    return
                }
            }
        }
        val id = runCatching { wifi.addNetwork(config) }.getOrDefault(-1)
        if (id == -1) {
            _state.update { it.copy(error = "Couldn't add ${network.ssid}. Use Wi-Fi settings instead.") }
            return
        }
        val ok = runCatching { wifi.enableNetwork(id, true) && wifi.reconnect() }.getOrDefault(false)
        _state.update {
            if (ok) it.copy(connectingSsid = network.ssid, error = null)
            else it.copy(error = "Couldn't connect to ${network.ssid}.")
        }
    }

    private companion object {
        const val PERMISSION_NETWORK_SETTINGS = "android.permission.NETWORK_SETTINGS"
        const val PERMISSION_SETUP_WIZARD = "android.permission.NETWORK_SETUP_WIZARD"
    }
}
