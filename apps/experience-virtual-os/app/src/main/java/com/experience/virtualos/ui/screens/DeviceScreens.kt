package com.experience.virtualos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.experience.virtualos.GuestDisplayActivity
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.images.SupportLevel
import com.experience.virtualos.core.vm.CpuModel
import com.experience.virtualos.core.vm.GuestKind
import com.experience.virtualos.core.vm.VirtualDevice
import com.experience.virtualos.core.vm.VmPhase
import com.experience.virtualos.core.vm.VmStatus
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.components.EmptyState
import com.experience.virtualos.ui.components.InfoRow
import com.experience.virtualos.ui.components.Notice
import com.experience.virtualos.ui.components.SectionCard
import com.experience.virtualos.ui.components.StatusPill
import com.experience.virtualos.ui.components.Tone
import com.experience.virtualos.ui.theme.CodeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun phaseTone(s: VmStatus) = when (s.phase) {
    VmPhase.RUNNING -> Tone.GOOD
    VmPhase.FAILED -> Tone.BAD
    VmPhase.PREPARING, VmPhase.STOPPING -> Tone.INFO
    VmPhase.STOPPED -> Tone.NEUTRAL
}

fun phaseLabel(s: VmStatus) = when (s.phase) {
    VmPhase.RUNNING -> "QEMU running · ${s.accelerator}"
    VmPhase.FAILED -> "Failed"
    VmPhase.PREPARING -> "Preparing"
    VmPhase.STOPPING -> "Stopping"
    VmPhase.STOPPED -> "Stopped"
}

@Composable
fun DevicesScreen(vm: AppViewModel, padding: PaddingValues, openDevice: (String) -> Unit, newDevice: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val status by vm.vmStatus.collectAsState()
    val runtime by vm.runtime.collectAsState()
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importDevice) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Virtual devices", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { import.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Import")
                    }
                }
            }
            if (runtime.installed == null) item { Notice("Install the runtime before starting devices. You can still create and configure them now.", Tone.WARN) }
            if (devices.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.Computer, "No devices", "A device is a saved machine configuration with its own disks. Create one, attach an ARM64 ISO, and start it.") {
                        Button(onClick = newDevice) { Text("Create device") }
                    }
                }
            }
            items(devices, key = { it.id }) { d ->
                val s = status[d.id] ?: VmStatus(VmPhase.STOPPED)
                Card(
                    onClick = { openDevice(d.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = if (s.phase == VmPhase.RUNNING) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (d.kind == GuestKind.ANDROID_AOSP) Icons.Outlined.Android else Icons.Outlined.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text("${d.kind.label} · ${d.cpuCores} vCPU · ${d.memoryMb} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusPill(phaseLabel(s), phaseTone(s))
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = newDevice,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New device") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = padding.calculateBottomPadding() + 20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditorScreen(vm: AppViewModel, deviceId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val existing = deviceId?.let { id -> vm.devices.collectAsState().value.firstOrNull { it.id == id } }
    val images by vm.installedImages.collectAsState()
    val host by vm.host.collectAsState()
    val now = remember { System.currentTimeMillis() }
    var d by remember(existing?.id) {
        mutableStateOf(existing ?: VirtualDevice(id = "", name = "Linux ARM64", kind = GuestKind.LINUX_ARM64, createdAtMillis = now, updatedAtMillis = now,
            cpuCores = minOf(2, host.cpuCores)))
    }
    val maxMem = ((host.totalMemoryBytes / (1024 * 1024)) * 0.6).toInt().coerceIn(512, VirtualDevice.MAX_MEMORY_MB) / 256 * 256
    val problems = d.validate()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "New device" else "Edit ${existing.name}") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(enabled = problems.isEmpty(), onClick = { vm.saveDevice(d, existing == null, onSaved) }) { Text("Save") }
            },
        )
    }) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(eyebrow = "Identity") {
                OutlinedTextField(d.name, { d = d.copy(name = it.take(60)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existing == null) {
                    Text("Guest type", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        GuestKind.entries.forEachIndexed { i, k ->
                            SegmentedButton(
                                selected = d.kind == k,
                                onClick = {
                                    d = if (k == GuestKind.ANDROID_AOSP) d.copy(kind = k, memoryMb = maxOf(d.memoryMb, 2048), name = if (d.name == "Linux ARM64") "Android" else d.name,
                                        systemImageId = images.firstOrNull { it.support != SupportLevel.UNSUPPORTED }?.id, cpuModel = CpuModel.CORTEX_A57)
                                    else d.copy(kind = k, systemImageId = null, name = if (d.name == "Android") "Linux ARM64" else d.name)
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, GuestKind.entries.size),
                            ) { Text(if (k == GuestKind.LINUX_ARM64) "Linux ARM64" else "Android (exp.)") }
                        }
                    }
                }
                if (d.kind == GuestKind.ANDROID_AOSP) {
                    Notice("Experimental. Android images start their kernel and system services but cannot display a screen on this runtime (no goldfish pipe / host GPU). Progress is visible in the serial console only.", Tone.WARN)
                    if (images.isEmpty()) Notice("No Android image is installed. Install one from the Images tab first.", Tone.BAD)
                    images.forEach { img ->
                        FilterChip(selected = d.systemImageId == img.id, onClick = { d = d.copy(systemImageId = img.id) }, label = { Text("${img.title} r${img.revision}") })
                    }
                }
            }
            SectionCard(eyebrow = "Hardware") {
                LabeledSlider("Memory", "${d.memoryMb} MB", d.memoryMb.toFloat(), 512f..maxOf(1024, maxMem).toFloat(), steps = (maxOf(1024, maxMem) - 512) / 256 - 1) {
                    d = d.copy(memoryMb = (it / 256).toInt() * 256)
                }
                LabeledSlider("vCPUs", d.cpuCores.toString(), d.cpuCores.toFloat(), 1f..minOf(VirtualDevice.MAX_CPUS, host.cpuCores).toFloat().coerceAtLeast(2f), steps = minOf(VirtualDevice.MAX_CPUS, host.cpuCores).coerceAtLeast(2) - 2) {
                    d = d.copy(cpuCores = it.toInt())
                }
                LabeledSlider(if (d.kind == GuestKind.ANDROID_AOSP) "Data partition" else "System disk", "${d.diskSizeGb} GB", d.diskSizeGb.toFloat(), 2f..32f, steps = 29) {
                    d = d.copy(diskSizeGb = it.toInt())
                }
                Text("CPU model", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpuModel.entries.forEach { c -> FilterChip(selected = d.cpuModel == c, onClick = { d = d.copy(cpuModel = c) }, label = { Text(c.label) }) }
                }
                if (d.memoryMb > host.availableMemoryBytes / (1024 * 1024) - 256) {
                    Notice("More memory than is currently free on this phone (${FileUtils.formatBytes(host.availableMemoryBytes)}). Starting may fail or other apps may be closed.", Tone.WARN)
                }
            }
            SectionCard(eyebrow = "Display and network") {
                Text("Resolution", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1024 to 768, 1280 to 720, 1280 to 800, 720 to 1280, 1920 to 1080).forEach { (w, h) ->
                        FilterChip(selected = d.displayWidth == w && d.displayHeight == h, onClick = { d = d.copy(displayWidth = w, displayHeight = h) }, label = { Text("$w×$h") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Network", style = MaterialTheme.typography.titleSmall)
                        Text("User-mode NAT (outbound only, no root needed)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(d.networkEnabled, { d = d.copy(networkEnabled = it) })
                }
            }
            SectionCard(eyebrow = "Notes") {
                OutlinedTextField(d.notes, { d = d.copy(notes = it.take(500)) }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
            }
            problems.forEach { Notice(it, Tone.BAD) }
            Button(onClick = { vm.saveDevice(d, existing == null, onSaved) }, enabled = problems.isEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (existing == null) "Create device" else "Save changes")
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(current.coerceIn(range), onChange, valueRange = range, steps = steps.coerceAtLeast(0), modifier = Modifier.semantics { contentDescription = "$label $value" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(vm: AppViewModel, deviceId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val device = devices.firstOrNull { it.id == deviceId }
    val statusMap by vm.vmStatus.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var copying by remember { mutableStateOf<Long?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.exportDevice(deviceId, uri) } }
    val media = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { copying = 0; vm.attachMedia(deviceId, uri) { copying = it }.invokeOnCompletion { copying = null } }
    }
    if (device == null) { LaunchedEffect(Unit) { onBack() }; return }
    val s = statusMap[deviceId] ?: VmStatus(VmPhase.STOPPED)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(device.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onEdit, enabled = s.phase != VmPhase.RUNNING) { Icon(Icons.Outlined.Edit, contentDescription = "Edit configuration") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More actions") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Duplicate (configuration only)") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; vm.duplicateDevice(deviceId, false) })
                        DropdownMenuItem(text = { Text("Duplicate with disks") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; vm.duplicateDevice(deviceId, true) })
                        DropdownMenuItem(text = { Text("Export configuration") }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = { menu = false; export.launch("${device.name}.evos.json") })
                        DropdownMenuItem(text = { Text("Erase disks") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; confirm = "erase" })
                        DropdownMenuItem(text = { Text("Delete device") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirm = "delete" })
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(phaseLabel(s), phaseTone(s))
                    Spacer(Modifier.weight(1f))
                    Text("${device.cpuCores} vCPU · ${device.memoryMb} MB · ${device.diskSizeGb} GB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (s.phase) {
                        VmPhase.RUNNING -> {
                            Button(onClick = { context.startActivity(GuestDisplayActivity.intent(context, s.vncPort, device.name, settings.keepScreenOnInDisplay)) }) {
                                Icon(Icons.Outlined.Tv, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Display")
                            }
                            FilledTonalButton(onClick = { vm.stopDevice(deviceId) }) { Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Shut down") }
                        }
                        VmPhase.STOPPING -> OutlinedButton(onClick = { vm.stopDevice(deviceId, force = true) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Force stop") }
                        VmPhase.PREPARING -> Text("Preparing…", style = MaterialTheme.typography.titleSmall)
                        else -> Button(onClick = { vm.startDevice(deviceId) }) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Start") }
                    }
                }
                if (device.kind == GuestKind.LINUX_ARM64) {
                    val c = copying
                    when {
                        c != null -> Text("Copying installation media… ${FileUtils.formatBytes(c)}", style = MaterialTheme.typography.bodyMedium)
                        device.installMedia != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Installation media attached${if (device.bootFromMedia) " · boots first" else ""}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.detachMedia(deviceId) }, enabled = s.phase != VmPhase.RUNNING) { Text("Remove") }
                        }
                        else -> OutlinedButton(onClick = { media.launch(arrayOf("application/x-iso9660-image", "application/octet-stream", "*/*")) }, enabled = s.phase != VmPhase.RUNNING) {
                            Text("Attach installer ISO")
                        }
                    }
                } else {
                    Notice("Android guests have no display on this runtime. Watch the Serial tab to follow the boot.", Tone.WARN)
                }
            }
            ConsoleTabs(vm, deviceId, s.phase == VmPhase.RUNNING, Modifier.weight(1f))
        }
    }

    when (confirm) {
        "delete" -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Delete ${device.name}?") },
            text = { Text("The configuration and all of its disks are removed permanently.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.deleteDevice(deviceId); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
        "erase" -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Erase disks?") },
            text = { Text("Everything installed inside ${device.name} is lost. The configuration is kept and fresh disks are created on the next start.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.resetDisks(deviceId) }) { Text("Erase") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ConsoleTabs(vm: AppViewModel, deviceId: String, live: Boolean, modifier: Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val (serial, qemu, launch) = vm.deviceFiles(deviceId)
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(tab, live) {
        while (true) {
            text = withContext(Dispatchers.IO) { vm.readLog(listOf(serial, qemu, launch)[tab]) }
            if (!live) break
            delay(1500)
        }
    }
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
            listOf("Serial console", "QEMU log", "Command").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (live) "Live · refreshes every 1.5 s" else "Last run", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("log", text))
            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy log") }
        }
        val scroll = rememberScrollState()
        LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
        SelectionContainer(Modifier.fillMaxSize().verticalScroll(scroll).horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(text.ifBlank { "No output yet. Start the device to see QEMU and guest messages here." }, style = CodeStyle)
        }
    }
}
