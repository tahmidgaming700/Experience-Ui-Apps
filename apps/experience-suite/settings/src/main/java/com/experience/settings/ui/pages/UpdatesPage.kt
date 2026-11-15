package com.experience.settings.ui.pages

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.experience.design.components.SectionHeader
import com.experience.design.system.SystemInfo
import com.experience.design.theme.ExperienceShape
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.PageScaffold
import com.experience.settings.update.ChannelBuild
import com.experience.settings.update.CheckState
import com.experience.settings.update.DownloadState
import com.experience.settings.update.UpdateLogic
import com.experience.settings.update.UpdateStatus
import com.experience.settings.update.Updater

private fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    var channel by remember { mutableStateOf(prefs(context).getString("channel", null) ?: Updater.defaultChannel()) }
    val check by Updater.check.collectAsState()
    val download by Updater.download.collectAsState()
    LaunchedEffect(channel) { Updater.check(channel) }

    PageScaffold("System updates", onBack) {
        Group("Installed") {
            row { i, n -> InfoRow("Current version", SystemInfo.experienceDisplayVersion, i, n) }
            row { i, n -> InfoRow("Build number", SystemInfo.experienceBuildNumber, i, n) }
            row { i, n -> InfoRow("Android security patch", SystemInfo.securityPatch.ifBlank { "Not reported" }, i, n) }
        }
        SectionHeader("Update channel")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            UpdateLogic.CHANNELS.forEachIndexed { i, c ->
                SegmentedButton(
                    selected = channel == c,
                    onClick = { channel = c; prefs(context).edit().putString("channel", c).apply() },
                    shape = SegmentedButtonDefaults.itemShape(i, UpdateLogic.CHANNELS.size),
                    label = { Text(c.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Text(
            if (channel == "beta") "Beta builds arrive sooner and may have bugs." else "Stable builds are recommended for everyday use.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Spacer(Modifier.height(8.dp))
        StatusCard(check, onCheck = { Updater.check(channel) })

        val build = when (val c = check) {
            is CheckState.Done -> when (val s = c.status) {
                is UpdateStatus.Available -> s.build
                is UpdateStatus.UpToDate -> s.build
                is UpdateStatus.NotPublished -> s.build
                is UpdateStatus.Error -> null
            }
            else -> null
        }
        val available = (check as? CheckState.Done)?.status as? UpdateStatus.Available

        if (build != null) {
            Group(if (available != null) "What's new" else "Latest ${channel} build") {
                row { i, n -> InfoRow(build.display, "Version ${build.version} · build ${build.buildNumber}" + (if (build.date.isNotBlank()) " · ${build.date}" else ""), i, n) }
                if (build.variant.isNotBlank()) row { i, n -> InfoRow("Variant", build.variant, i, n) }
                if (build.size > 0) row { i, n -> InfoRow("Download size", Formatter.formatFileSize(context, build.size), i, n) }
                if (build.changelog.isNotBlank()) row { i, n -> InfoRow("Changelog", build.changelog, i, n) }
            }
            if (!build.bootTested && build.url != null) {
                Notice(Icons.Outlined.Warning, "Not boot-tested", "The maintainers have not confirmed this build boots on real hardware yet. Keep a backup and a way to reflash.")
            }
        }

        if (available != null) {
            Spacer(Modifier.height(16.dp))
            DownloadSection(context, available.build, download)
        } else if (download is DownloadState.Ready) {
            Spacer(Modifier.height(16.dp))
            DownloadSection(context, (download as DownloadState.Ready).build, download)
        }
    }
}

@Composable
private fun StatusCard(check: CheckState, onCheck: () -> Unit) {
    val (icon, title, body) = when (check) {
        CheckState.Idle, CheckState.Checking -> Triple(Icons.Outlined.HourglassEmpty, "Checking for updates…", "Contacting ${java.net.URI(Updater.manifestUrl).host ?: "the update server"}")
        is CheckState.Done -> when (val s = check.status) {
            is UpdateStatus.UpToDate -> Triple(Icons.Outlined.CheckCircle, "Your system is up to date", "Build ${s.build.buildNumber} is the latest on this channel.")
            is UpdateStatus.Available -> Triple(Icons.Outlined.SystemUpdate, "Update available", "${s.build.display} (build ${s.build.buildNumber}) is ready to download.")
            is UpdateStatus.NotPublished -> Triple(Icons.Outlined.CloudOff, "No build published yet", "${s.build.display} is announced on this channel, but no image has been published for download.")
            is UpdateStatus.Error -> Triple(Icons.Outlined.ErrorOutline, "Couldn't check for updates", s.message)
        }
    }
    Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (check is CheckState.Checking || check is CheckState.Idle) CircularProgressIndicator(Modifier.size(32.dp))
                else Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (check is CheckState.Done) {
                Text("Last checked " + DateUtils.getRelativeTimeSpanString(check.checkedAt), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onCheck) { Text("Check again") }
            }
        }
    }
}

@Composable
private fun DownloadSection(context: Context, build: ChannelBuild, state: DownloadState) {
    Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                DownloadState.Idle -> {
                    Text("Downloads to this app's storage and is verified with SHA-256 before use.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { Updater.startDownload(context, build) }) { Text("Download") }
                }
                is DownloadState.Downloading -> {
                    val fraction = if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
                    Text("Downloading… " + Formatter.formatShortFileSize(context, state.done) + (if (state.total > 0) " of " + Formatter.formatShortFileSize(context, state.total) else ""))
                    if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = Updater::cancelDownload) { Text("Cancel") }
                }
                DownloadState.Verifying -> {
                    Text("Verifying checksum…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is DownloadState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { Updater.startDownload(context, build) }) { Text("Try again") }
                }
                is DownloadState.Ready -> InstallInstructions(state)
            }
        }
    }
}

@Composable
private fun InstallInstructions(state: DownloadState.Ready) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text("Downloaded and verified", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Experience UI is a generic system image: it is installed by flashing from a computer. This app never flashes the device itself.",
        style = MaterialTheme.typography.bodyMedium,
    )
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("File: ${state.file.absolutePath}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            state.build.sha256?.let { Text("SHA-256: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            UpdateLogic.installSteps(state.file.name).forEachIndexed { i, step ->
                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    TextButton(onClick = { Updater.deleteDownload(state.file) }) { Text("Delete download") }
}

@Composable
private fun Notice(icon: ImageVector, title: String, body: String) {
    Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
