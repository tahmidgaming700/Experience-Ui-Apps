package com.experience.setupwizard.ui.pages

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experience.design.components.GroupedRow
import com.experience.design.components.PermissionGate
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.model.WifiNetwork
import com.experience.setupwizard.model.WifiNetworks
import com.experience.setupwizard.system.WifiViewModel
import com.experience.setupwizard.ui.PageLayout
import com.experience.setupwizard.ui.tryStart

/** Signal bars icon; security is stated in the row text, not by the icon alone. */
private fun signalIcon(level: Int): ImageVector = when (level) {
    0 -> Icons.Filled.SignalWifi0Bar
    1 -> Icons.Filled.NetworkWifi1Bar
    2 -> Icons.Filled.NetworkWifi2Bar
    3 -> Icons.Filled.NetworkWifi3Bar
    else -> Icons.Filled.SignalWifi4Bar
}

@Composable
fun WifiPage(setup: SetupViewModel) {
    val context = LocalContext.current
    val wifi: WifiViewModel = viewModel()
    PageLayout(Icons.Outlined.Wifi, "Connect to Wi-Fi", "Connect now to set the time automatically and get updates. You can skip this.") {
        val content = @Composable { WifiContent(wifi, setup) }
        if (wifi.canScan) {
            content()
        } else {
            // Outside Experience UI (no NETWORK_SETTINGS) scan results need location.
            Column {
                PermissionGate(
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    "Allow nearby network scanning",
                    "Android requires location access to list Wi-Fi networks.",
                ) { content() }
                GroupedRow("Wi-Fi settings", 0, 1, icon = Icons.Outlined.Settings, onClick = {
                    context.tryStart(Intent(Settings.Panel.ACTION_WIFI))
                })
            }
        }
    }
}

@Composable
private fun WifiContent(wifi: WifiViewModel, setup: SetupViewModel) {
    val context = LocalContext.current
    val state by wifi.state.collectAsState()
    var target by remember { mutableStateOf<WifiNetwork?>(null) }
    LaunchedEffect(state.error) {
        state.error?.let { setup.showMessage(it); wifi.clearError() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            SwitchRow("Wi-Fi", state.enabled, wifi::setEnabled, 0, 1, summary = state.connectedSsid?.let { "Connected to $it" })
        }
        if (state.enabling || state.scanning) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        }
        if (state.enabled) {
            item {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionHeader("Available networks", Modifier.weight(1f))
                    IconButton(onClick = { wifi.scan() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Scan again") }
                }
            }
            if (state.networks.isEmpty() && !state.scanning) {
                item {
                    Text("No networks found yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
            itemsIndexed(state.networks, key = { _, n -> n.ssid + n.security }) { i, network ->
                val status = when (network.ssid) {
                    state.connectedSsid -> "Connected"
                    state.connectingSsid -> "Connecting…"
                    else -> buildString {
                        append(network.security.label)
                        if (network.is5GHz) append(" · 5 GHz")
                        if (!network.security.supported) append(" · set up in Settings")
                    }
                }
                GroupedRow(
                    title = network.ssid,
                    summary = "$status · signal ${network.level} of 4",
                    icon = signalIcon(network.level),
                    index = i, count = state.networks.size,
                    onClick = {
                        when {
                            network.ssid == state.connectedSsid -> Unit
                            !network.security.supported -> context.tryStart(Intent(Settings.Panel.ACTION_WIFI))
                            network.security.needsPassword -> target = network
                            else -> wifi.connect(network, "")
                        }
                    },
                    trailing = if (network.ssid == state.connectingSsid) ({ CircularProgressIndicator(Modifier.size(24.dp)) }) else null,
                )
            }
        }
        item {
            SectionHeader("Other options")
            GroupedRow("Wi-Fi settings", 0, 1, summary = "Hidden and enterprise networks, more options", icon = Icons.Outlined.Settings, onClick = {
                context.tryStart(Intent(Settings.Panel.ACTION_WIFI))
            })
        }
    }
    target?.let { network ->
        PasswordDialog(network, onDismiss = { target = null }) { password ->
            target = null
            wifi.connect(network, password)
        }
    }
}

@Composable
private fun PasswordDialog(network: WifiNetwork, onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val valid = WifiNetworks.isValidPassword(network.security, password)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
        title = { Text(network.ssid) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                            )
                        }
                    },
                    supportingText = { Text("${network.security.label}. At least 8 characters.") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConnect(password) }, enabled = valid) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
