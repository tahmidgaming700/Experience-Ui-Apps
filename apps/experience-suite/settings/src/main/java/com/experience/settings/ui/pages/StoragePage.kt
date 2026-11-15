package com.experience.settings.ui.pages

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.GroupedRow
import com.experience.design.theme.ExperienceShape
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.link
import com.experience.settings.ui.startSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VolumeUsage(val label: String, val total: Long, val free: Long, val removable: Boolean)
private data class StorageInfo(val volumes: List<VolumeUsage>, val breakdown: List<Pair<String, Long>>)

private fun readStorage(context: Context): StorageInfo {
    val sm = context.getSystemService(StorageManager::class.java)
    val ssm = context.getSystemService(StorageStatsManager::class.java)
    val volumes = sm.storageVolumes.mapNotNull { v ->
        val dir = v.directory ?: return@mapNotNull null
        val stat = runCatching { StatFs(dir.path) }.getOrNull() ?: return@mapNotNull null
        var total = stat.totalBytes
        var free = stat.availableBytes
        if (v.isPrimary) runCatching {
            // Whole-device figures (including the system partition), as system Settings shows them.
            total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        }
        VolumeUsage(v.getDescription(context), total, free, v.isRemovable)
    }
    // Per-category usage needs PACKAGE_USAGE_STATS (granted to the privileged app in the ROM).
    val breakdown = runCatching {
        val user = Process.myUserHandle()
        val ext = ssm.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, user)
        val apps = ssm.queryStatsForUser(StorageManager.UUID_DEFAULT, user)
        listOf(
            "Apps" to (apps.appBytes + apps.dataBytes),
            "Images" to ext.imageBytes,
            "Videos" to ext.videoBytes,
            "Audio" to ext.audioBytes,
        )
    }.getOrDefault(emptyList())
    return StorageInfo(volumes, breakdown)
}

@Composable
fun StoragePage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<StorageInfo?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { readStorage(context) } }
    fun fmt(b: Long) = Formatter.formatShortFileSize(context, b)

    PageScaffold("Storage", onBack) {
        info?.let { data ->
            data.volumes.forEach { v ->
                val used = (v.total - v.free).coerceAtLeast(0)
                val fraction = if (v.total > 0) used.toFloat() / v.total else 0f
                Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text(v.label, style = MaterialTheme.typography.titleMedium)
                        Text("${fmt(used)} used", style = MaterialTheme.typography.headlineMedium)
                        Text("${fmt(v.free)} free of ${fmt(v.total)} · ${(fraction * 100).toInt()}% full", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(12.dp), strokeCap = StrokeCap.Round, trackColor = MaterialTheme.colorScheme.surface)
                    }
                }
            }
            if (data.breakdown.isNotEmpty()) {
                Group("Internal storage by type") {
                    data.breakdown.forEach { (label, bytes) -> row { i, n -> InfoRow(label, fmt(bytes), i, n) } }
                }
            }
        }
        Group("Manage") {
            val files = context.packageManager.getLaunchIntentForPackage("com.experience.files")
            if (files != null) row { i, n ->
                GroupedRow("Browse files", i, n, summary = "Open Experience Files", icon = Icons.Outlined.Folder, onClick = { context.startSafely(files) })
            }
            link(context, "Free up space", null, Icons.Outlined.CleaningServices, Intent(StorageManager.ACTION_MANAGE_STORAGE))
            link(context, "SD card & USB", null, Icons.Outlined.SdCard, Intent(Settings.ACTION_MEMORY_CARD_SETTINGS))
            link(context, "Storage settings", null, Icons.Outlined.Tune, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        }
    }
}
