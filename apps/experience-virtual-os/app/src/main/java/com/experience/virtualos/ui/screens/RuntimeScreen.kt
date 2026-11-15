package com.experience.virtualos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.RemoteManifestState
import com.experience.virtualos.ui.components.InfoRow
import com.experience.virtualos.ui.components.Notice
import com.experience.virtualos.ui.components.ProgressBlock
import com.experience.virtualos.ui.components.SectionCard
import com.experience.virtualos.ui.components.StatusPill
import com.experience.virtualos.ui.components.Tone
import com.experience.virtualos.ui.theme.CodeStyle
import java.text.DateFormat
import java.util.Date

@Composable
fun RuntimeScreen(vm: AppViewModel, padding: PaddingValues) {
    val runtime by vm.runtime.collectAsState()
    val task by vm.runtimeTask.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickManifest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { m -> val a = archive; if (a != null && m != null) vm.installRuntimeFromFiles(a, m) }
    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { a -> if (a != null) { archive = a; pickManifest.launch(arrayOf("application/json", "*/*")) } }
    LaunchedEffect(Unit) { vm.refreshRuntime(); if (runtime.remote is RemoteManifestState.Unknown) vm.checkForRuntime() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("Runtime", style = MaterialTheme.typography.headlineMedium)
                Text("Ubuntu ARM64 userspace with QEMU, executed through PRoot from the APK. Installed atomically into app-private storage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val t = task
        if (t != null && t.running) item {
            SectionCard(eyebrow = "Installing") {
                ProgressBlock(t.stage, t.done, t.total)
                TextButton(onClick = vm::cancelRuntimeInstall) { Text("Cancel") }
                Text("Downloads resume where they stopped. The current runtime stays usable until the new one passes verification.", style = MaterialTheme.typography.bodySmall)
            }
        } else if (t?.error != null) item { Notice("Last installation failed: ${t.error}", Tone.BAD) }

        item {
            val installed = runtime.installed
            SectionCard(eyebrow = "Installed") {
                if (installed == null) {
                    Text("Not installed", style = MaterialTheme.typography.titleLarge)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Version ${installed.runtimeVersion}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        StatusPill(if (runtime.healthy) "Verified" else "Problems found", if (runtime.healthy) Tone.GOOD else Tone.BAD)
                    }
                    InfoRow("Release", installed.releaseTag)
                    installed.manifest.components.forEach { (k, v) -> if (v != null) InfoRow(k.replaceFirstChar { it.uppercase() }, v) }
                    InfoRow("Installed", DateFormat.getDateTimeInstance().format(Date(installed.installedAtMillis)))
                    InfoRow("Source", if (installed.source == "local-file") "Local file" else "GitHub Releases")
                    InfoRow("Size", FileUtils.formatBytes(runtime.sizeBytes))
                    SelectionContainer { Text("SHA-256 ${installed.archiveSha256}", style = CodeStyle) }
                    installed.manifest.capabilities.forEach { (k, v) -> InfoRow(k, v) }
                    runtime.problems.forEach { Notice(it, Tone.BAD) }
                }
                runtime.prootProblems.forEach { Notice(it, Tone.BAD) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshRuntime() }) { Text("Verify files") }
                    if (installed != null) OutlinedButton(onClick = { vm.runSelfTest() }) { Text("Run self-test") }
                    if (installed != null) TextButton(onClick = { confirmRemove = true }) { Text("Remove") }
                }
            }
        }

        vm.bundledRuntime?.let { b ->
            item {
                SectionCard(eyebrow = "Included with the app") {
                    Text("Runtime ${b.runtimeVersion}", style = MaterialTheme.typography.titleLarge)
                    b.components["qemu"]?.let { InfoRow("QEMU", it) }
                    InfoRow("Installed size", FileUtils.formatBytes(b.archive.unpackedSizeBytes))
                    val same = runtime.installed?.archiveSha256 == b.archive.sha256 && runtime.problems.isEmpty()
                    Button(onClick = vm::installBundledRuntime, enabled = t?.running != true) {
                        Text(if (runtime.installed == null) "Install included runtime" else if (same) "Reinstall included runtime" else "Install included runtime (replaces current)")
                    }
                    Text("Works offline. Verified with SHA-256 and an on-device self-test before it is activated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SectionCard(eyebrow = "GitHub Releases") {
                when (val r = runtime.remote) {
                    RemoteManifestState.Unknown, RemoteManifestState.Checking -> ProgressBlock("Checking the release manifest…", 0, 0)
                    is RemoteManifestState.Error -> {
                        Notice(r.message, Tone.WARN)
                        OutlinedButton(onClick = vm::checkForRuntime) { Text("Check again") }
                    }
                    is RemoteManifestState.Available -> {
                        Text("Runtime ${r.manifest.runtimeVersion}", style = MaterialTheme.typography.titleLarge)
                        InfoRow("Download", FileUtils.formatBytes(r.manifest.archive.sizeBytes))
                        InfoRow("Installed size", FileUtils.formatBytes(r.manifest.archive.unpackedSizeBytes))
                        r.manifest.components["qemu"]?.let { InfoRow("QEMU", it) }
                        if (r.manifest.notes.isNotBlank()) Text(r.manifest.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val label = when {
                            runtime.installed == null -> "Download and install"
                            r.isUpdate && runtime.problems.isNotEmpty() -> "Repair"
                            r.isUpdate -> "Update"
                            else -> "Reinstall"
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::installRuntime, enabled = t?.running != true) { Text(label) }
                            TextButton(onClick = vm::checkForRuntime) { Text("Refresh") }
                        }
                        if (!r.isUpdate) Text("You already have this version.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            SectionCard(eyebrow = "Offline install") {
                Text("Install a runtime you downloaded yourself (for example a CI artifact). Pick base.tar.gz, then its manifest.json. Size and SHA-256 are verified exactly as for a download.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { pickArchive.launch(arrayOf("*/*")) }, enabled = t?.running != true, modifier = Modifier.fillMaxWidth()) { Text("Choose files") }
            }
        }
    }

    val selfTest by vm.selfTest.collectAsState()
    selfTest?.let { out ->
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = vm::dismissSelfTest,
            title = { Text("Runtime self-test") },
            text = { SelectionContainer { Text(out, style = CodeStyle, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = vm::dismissSelfTest) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("self-test", out))
                }) { Text("Copy") }
            },
        )
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove runtime?") },
        text = { Text("Devices and images are kept, but nothing can start until a runtime is installed again.") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; vm.uninstallRuntime() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}
