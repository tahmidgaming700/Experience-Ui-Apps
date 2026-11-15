package com.experience.settings.ui.pages

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.EmptyState
import com.experience.design.components.GroupedRow
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.BroadcastEffect
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import com.experience.settings.ui.startSafely

private fun deviceIcon(d: BluetoothDevice): ImageVector = when (d.bluetoothClass?.majorDeviceClass) {
    BluetoothClass.Device.Major.AUDIO_VIDEO -> Icons.Outlined.Headphones
    BluetoothClass.Device.Major.PHONE -> Icons.Outlined.Smartphone
    BluetoothClass.Device.Major.COMPUTER -> Icons.Outlined.Computer
    BluetoothClass.Device.Major.WEARABLE -> Icons.Outlined.Watch
    BluetoothClass.Device.Major.PERIPHERAL -> Icons.Outlined.Keyboard
    else -> if (d.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) Icons.Outlined.DirectionsCar else Icons.Outlined.DevicesOther
}

@SuppressLint("MissingPermission") // BLUETOOTH / BLUETOOTH_ADMIN are install-time permissions on Android 11.
@Composable
fun ConnectedPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    @Suppress("DEPRECATION")
    val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val state = rememberSystemValue { adapter?.state ?: BluetoothAdapter.STATE_OFF }
    val bonded = rememberSystemValue { runCatching { adapter?.bondedDevices?.toList() }.getOrNull().orEmpty().sortedBy { it.name ?: it.address } }
    BroadcastEffect(BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothDevice.ACTION_BOND_STATE_CHANGED, BluetoothDevice.ACTION_NAME_CHANGED) {
        state.refresh(); bonded.refresh()
    }
    val nfc = NfcAdapter.getDefaultAdapter(context)

    PageScaffold("Connected devices", onBack) {
        if (adapter == null) {
            EmptyState(Icons.Outlined.BluetoothDisabled, "No Bluetooth", "This device has no Bluetooth radio.")
        } else {
            val on = state.value == BluetoothAdapter.STATE_ON || state.value == BluetoothAdapter.STATE_TURNING_ON
            val summary = when (state.value) {
                BluetoothAdapter.STATE_TURNING_ON -> "Turning on…"
                BluetoothAdapter.STATE_TURNING_OFF -> "Turning off…"
                BluetoothAdapter.STATE_ON -> "On · device name “${adapter.name ?: "unknown"}”"
                else -> "Off"
            }
            Group("Bluetooth") {
                row { i, n ->
                    SwitchRow(
                        title = "Bluetooth", summary = summary, icon = Icons.Outlined.Bluetooth, checked = on, index = i, count = n,
                        enabled = state.value == BluetoothAdapter.STATE_ON || state.value == BluetoothAdapter.STATE_OFF,
                        onCheckedChange = { want ->
                            @Suppress("DEPRECATION")
                            val ok = runCatching { if (want) adapter.enable() else adapter.disable() }.getOrDefault(false)
                            if (!ok) msg("Couldn't change Bluetooth")
                            state.refresh()
                        },
                    )
                }
                link(context, "Pair new device", null, Icons.Outlined.Add, Intent("android.settings.BLUETOOTH_PAIRING_SETTINGS"), Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            if (bonded.value.isNotEmpty()) {
                Group("Saved devices") {
                    bonded.value.forEach { d ->
                        row { i, n ->
                            GroupedRow(
                                title = d.name ?: d.address, summary = "Paired", icon = deviceIcon(d), index = i, count = n,
                                onClick = { context.startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                            )
                        }
                    }
                }
            }
        }
        Group("Connection preferences") {
            if (nfc != null) link(context, "NFC", if (nfc.isEnabled) "On" else "Off", Icons.Outlined.Nfc, Intent(Settings.ACTION_NFC_SETTINGS))
            link(context, "Cast", "Mirror the screen to a display", Icons.Outlined.Cast, Intent(Settings.ACTION_CAST_SETTINGS))
            link(context, "Printing", null, Icons.Outlined.Print, Intent(Settings.ACTION_PRINT_SETTINGS))
            link(context, "Bluetooth settings", null, Icons.Outlined.Bluetooth, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }
}
