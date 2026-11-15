package com.experience.files.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.files.FilesViewModel
import com.experience.files.Location
import com.experience.files.data.FileCategory
import com.experience.files.data.Volume
import com.experience.files.model.FileItem
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape

fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.IMAGES -> Icons.Outlined.Image
    FileCategory.VIDEOS -> Icons.Outlined.VideoLibrary
    FileCategory.AUDIO -> Icons.Outlined.AudioFile
    FileCategory.DOCUMENTS -> Icons.Outlined.Description
    FileCategory.DOWNLOADS -> Icons.Outlined.Download
    FileCategory.APKS -> Icons.Outlined.Android
}

/** Storage overview: volumes with usage, categories and recent files. */
@Composable
fun HomeContent(vm: FilesViewModel, padding: PaddingValues, onFile: (FileItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Storage") }
        items(vm.volumes, span = { GridItemSpan(maxLineSpan) }, key = { it.root.path }) { v ->
            VolumeCard(v) { vm.navigate(Location.Folder(v.root)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Categories") }
        items(FileCategory.entries, key = { it.name }) { c ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button) { vm.navigate(Location.Category(c)) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(c.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(c.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (vm.recentPreview.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Recent files", Modifier.weight(1f))
                    TextButton(onClick = { vm.navigate(Location.Recent) }, modifier = Modifier.padding(top = 16.dp)) { Text("See all") }
                }
            }
            val list = vm.recentPreview.take(6)
            list.forEachIndexed { i, f ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent:${f.path}") {
                    val context = LocalContext.current
                    GroupedRow(
                        title = f.name, summary = f.summary(context), index = i, count = list.size,
                        onClick = { onFile(f) },
                        icon = f.kind().icon(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(v: Volume, onClick: () -> Unit) {
    val context = LocalContext.current
    val fraction = if (v.totalBytes > 0) v.usedBytes.toFloat() / v.totalBytes else 0f
    Surface(
        shape = ExperienceShape.hero,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (v.removable) Icons.Outlined.SdCard else Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(v.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            Text("${formatSize(context, v.usedBytes)} used", style = MaterialTheme.typography.headlineSmall)
            Text("${formatSize(context, v.freeBytes)} free of ${formatSize(context, v.totalBytes)} · ${(fraction * 100).toInt()}% full", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}
