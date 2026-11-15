package com.experience.virtualos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.host.HostInfo
import com.experience.virtualos.core.log.LogLevel
import com.experience.virtualos.core.vm.VmPhase
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.components.Notice
import com.experience.virtualos.ui.components.ProgressBlock
import com.experience.virtualos.ui.components.SectionCard
import com.experience.virtualos.ui.components.StatTile
import com.experience.virtualos.ui.components.StatusPill
import com.experience.virtualos.ui.components.Tone
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    openRuntime: () -> Unit,
    openDevices: () -> Unit,
    openDevice: (String) -> Unit,
    openImages: () -> Unit,
    newDevice: () -> Unit,
) {
    val host by vm.host.collectAsState()
    val runtime by vm.runtime.collectAsState()
    val task by vm.runtimeTask.collectAsState()
    val devices by vm.devices.collectAsState()
    val status by vm.vmStatus.collectAsState()
    val images by vm.installedImages.collectAsState()
    val storage by vm.storage.collectAsState()
    val activity by vm.activity.collectAsState()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("${host.manufacturer} ${host.model}", style = MaterialTheme.typography.headlineMedium)
                Text("Android ${host.androidRelease} · ${host.supportedAbis.firstOrNull()} · rootless", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            val t = task
            SectionCard(eyebrow = "Runtime", container = if (runtime.healthy) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer) {
                when {
                    t != null && t.running -> ProgressBlock(t.stage, t.done, t.total)
                    runtime.installed == null -> {
                        Text("Runtime not installed", style = MaterialTheme.typography.titleLarge)
                        Text("Virtual devices need the Ubuntu + QEMU runtime (included in the app or from GitHub Releases).", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = openRuntime) { Text("Set up runtime") }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Runtime ${runtime.installed!!.runtimeVersion}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            StatusPill(if (runtime.healthy) "Verified" else "Needs repair", if (runtime.healthy) Tone.GOOD else Tone.BAD)
                        }
                        Text(
                            listOfNotNull(runtime.installed!!.manifest.components["userspace"], runtime.installed!!.manifest.components["qemu"]?.let { "QEMU $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!runtime.healthy) FilledTonalButton(onClick = openRuntime) { Text("Open runtime") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Acceleration", if (host.kvm == HostInfo.KvmStatus.ACCESSIBLE) "KVM" else "TCG (software)", Icons.Outlined.Speed,
                    if (host.kvm == HostInfo.KvmStatus.ACCESSIBLE) Tone.GOOD else Tone.WARN, Modifier.weight(1f))
                StatTile("Free memory", FileUtils.formatBytes(host.availableMemoryBytes), Icons.Outlined.Memory,
                    if (host.lowMemory) Tone.BAD else Tone.INFO, Modifier.weight(1f))
            }
        }

        item {
            val running = devices.firstOrNull { status[it.id]?.phase == VmPhase.RUNNING }
            SectionCard(eyebrow = "Virtual devices") {
                if (devices.isEmpty()) {
                    Text("No virtual devices yet", style = MaterialTheme.typography.titleLarge)
                    Text("Create a Linux ARM64 machine and attach an installer ISO to get started.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = newDevice, enabled = runtime.installed != null) { Text("Create device") }
                } else {
                    Text("${devices.size} device${if (devices.size == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge)
                    if (running != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(running.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill("QEMU running", Tone.GOOD)
                        }
                        FilledTonalButton(onClick = { openDevice(running.id) }) { Text("Open ${running.name}") }
                    } else {
                        Text("None running", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = openDevices) { Text("Manage devices") }
                    }
                }
            }
        }

        item {
            SectionCard(eyebrow = "Guest images") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Album, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (images.isEmpty()) "No Android images installed" else "${images.size} Android image${if (images.size == 1) "" else "s"} installed", style = MaterialTheme.typography.titleMedium)
                        Text("Linux guests boot from ISO files you attach to a device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledTonalButton(onClick = openImages) { Text("Browse catalogue") }
            }
        }

        item {
            val s = storage
            SectionCard(eyebrow = "Storage") {
                if (s == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    val used = s.runtime + s.images + s.devices
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("${FileUtils.formatBytes(used)} used by the app · ${FileUtils.formatBytes(s.free)} free", style = MaterialTheme.typography.titleMedium)
                    }
                    val fraction = if (s.total > 0) ((s.total - s.free).toFloat() / s.total) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "Phone storage ${(fraction * 100).toInt()} percent used" }, drawStopIndicator = {})
                    Text("Runtime ${FileUtils.formatBytes(s.runtime)} · Images ${FileUtils.formatBytes(s.images)} · Devices ${FileUtils.formatBytes(s.devices)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SectionCard(eyebrow = "Recent activity") {
                if (activity.isEmpty()) {
                    Text("Nothing yet. Installs, starts and errors appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    activity.take(6).forEach { e ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                            val icon = if (e.category == "Device") Icons.Outlined.Terminal else Icons.Outlined.Widgets
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp).padding(top = 2.dp),
                                tint = when (e.level) { LogLevel.ERROR -> MaterialTheme.colorScheme.error; LogLevel.SUCCESS -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(e.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.timeMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (host.lowMemory) item { Notice("Android reports low memory. Close other apps before starting a virtual device.", Tone.WARN) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
