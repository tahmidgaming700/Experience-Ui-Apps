package com.experience.virtualos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.images.CatalogImage
import com.experience.virtualos.core.images.SupportLevel
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.CatalogState
import com.experience.virtualos.ui.components.EmptyState
import com.experience.virtualos.ui.components.InfoRow
import com.experience.virtualos.ui.components.Notice
import com.experience.virtualos.ui.components.ProgressBlock
import com.experience.virtualos.ui.components.SectionCard
import com.experience.virtualos.ui.components.StatusPill
import com.experience.virtualos.ui.components.Tone

private fun SupportLevel.tone() = when (this) {
    SupportLevel.EXPERIMENTAL_TESTED -> Tone.WARN
    SupportLevel.EXPERIMENTAL_UNTESTED -> Tone.NEUTRAL
    SupportLevel.UNSUPPORTED -> Tone.BAD
}

@Composable
fun ImagesScreen(vm: AppViewModel, padding: PaddingValues) {
    val catalog by vm.catalog.collectAsState()
    val installed by vm.installedImages.collectAsState()
    val tasks by vm.imageTasks.collectAsState()
    var showUnsupported by rememberSaveable { mutableStateOf(false) }
    var licence by remember { mutableStateOf<CatalogImage?>(null) }
    LaunchedEffect(Unit) { if (catalog is CatalogState.Idle) vm.loadCatalog() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("Guest images", style = MaterialTheme.typography.headlineMedium)
                Text("Linux guests boot from an ISO you attach to a device. Android images come from Google's SDK repository.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Notice("Android images on this runtime are experimental and console-only: the kernel and system services start, but no screen appears because the images' graphics require the Android Emulator's goldfish pipe. Only ARM64 images are listed; nothing is substituted.", Tone.WARN)
        }

        if (installed.isNotEmpty()) {
            item { Text("Installed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
            items(installed, key = { "i-" + it.id }) { img ->
                SectionCard(title = img.title) {
                    StatusPill(img.support.label, img.support.tone())
                    InfoRow("Revision", "r${img.revision}")
                    InfoRow("Size on disk", FileUtils.formatBytes(img.sizeBytes))
                    InfoRow("Layout", "Dynamic partitions (super), kernel ${if (img.layout.kernelCompressed) "gzip" else "raw"}")
                    InfoRow("SHA-1", img.sha1.take(20) + "…")
                    TextButton(onClick = { vm.deleteImage(img.id) }) { Text("Delete image") }
                }
            }
        }

        item {
            Row(Modifier.padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Google ARM64 catalogue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = showUnsupported, onClick = { showUnsupported = !showUnsupported }, label = { Text("Show incompatible") })
            }
        }
        when (val c = catalog) {
            CatalogState.Idle, CatalogState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is CatalogState.Error -> item {
                EmptyState(Icons.Outlined.CloudOff, "Catalogue unavailable", c.message) { OutlinedButton(onClick = vm::loadCatalog) { Text("Retry") } }
            }
            is CatalogState.Loaded -> {
                val visible = c.images.filter { (showUnsupported || it.support != SupportLevel.UNSUPPORTED) && !it.obsolete }
                items(visible, key = { "c-" + it.id }) { img ->
                    val task = tasks[img.id]
                    val isInstalled = installed.any { it.id == img.id }
                    SectionCard(title = img.title) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(img.support.label, img.support.tone())
                            StatusPill(img.tagDisplay, Tone.NEUTRAL)
                        }
                        Text(img.support.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        InfoRow("Download", "${FileUtils.formatBytes(img.sizeBytes)} · r${img.revision}")
                        when {
                            task != null && task.running -> {
                                ProgressBlock(task.stage, task.done, task.total)
                                TextButton(onClick = { vm.cancelImage(img.id) }) { Text("Cancel") }
                            }
                            isInstalled -> Text("Installed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                            img.support == SupportLevel.UNSUPPORTED -> Text("Not installable on this runtime", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            else -> {
                                task?.error?.let { Notice(it, Tone.BAD) }
                                Button(onClick = { licence = img }) { Text("Download") }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) item { Text("No matching images.", modifier = Modifier.padding(8.dp)) }
            }
        }
    }

    licence?.let { img ->
        AlertDialog(
            onDismissRequest = { licence = null },
            title = { Text("Android SDK licence") },
            text = {
                Text("${img.title} (${FileUtils.formatBytes(img.sizeBytes)}) is distributed by Google under the Android Software Development Kit License Agreement (developer.android.com/studio/terms). " +
                    "By continuing you accept those terms. The download is checked against Google's published SHA-1 and size.\n\n" +
                    "Reminder: this image will not show a screen on this runtime.")
            },
            confirmButton = { TextButton(onClick = { vm.installImage(img); licence = null }) { Text("Accept and download") } },
            dismissButton = { TextButton(onClick = { licence = null }) { Text("Cancel") } },
        )
    }
}
