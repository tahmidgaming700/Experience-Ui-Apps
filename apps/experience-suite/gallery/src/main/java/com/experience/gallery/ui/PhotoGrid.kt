package com.experience.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import com.experience.design.theme.ExperienceMotion
import com.experience.gallery.data.Thumbnails
import com.experience.gallery.model.MediaItem
import com.experience.gallery.model.Timeline
import java.time.LocalDate

/** A MediaStore thumbnail, loaded off the main thread and cached. */
@Composable
fun MediaThumb(item: MediaItem, sizeDp: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }.coerceIn(96, 512)
    var bitmap by remember(item.uri) { mutableStateOf(Thumbnails.cached(item.uri, px)) }
    LaunchedEffect(item.uri, px) {
        if (bitmap == null) bitmap = Thumbnails.load(context.contentResolver, item.uri, px)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Photos grouped by day with sticky date headers. LazyVerticalGrid has no sticky
 * headers, so each day is laid out as rows of [columns] cells in a LazyColumn.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    items: List<MediaItem>,
    selection: Set<android.net.Uri>,
    contentPadding: PaddingValues,
    onOpen: (Int) -> Unit,
    onLongPress: (MediaItem) -> Unit,
) {
    val sections = remember(items) { Timeline.groupByDay(items) }
    val indexOf = remember(items) { items.withIndex().associate { it.value.uri to it.index } }
    val today = LocalDate.now()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = Timeline.columnsFor(maxWidth.value.toInt())
        val cell = maxWidth / columns
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            sections.forEach { section ->
                stickyHeader(key = "h${section.day}") {
                    Text(
                        Timeline.dayLabel(section.day, today),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics { contentDescription = "${Timeline.dayLabel(section.day, today)}, ${section.items.size} items" },
                    )
                }
                val rows = section.items.chunked(columns)
                items(rows, key = { row -> "r" + row.first().uri }) { row ->
                    Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { m ->
                            GridCell(m, m.uri in selection, selection.isNotEmpty(), cell, Modifier.weight(1f),
                                onClick = { if (selection.isNotEmpty()) onLongPress(m) else onOpen(indexOf.getValue(m.uri)) },
                                onLongClick = { onLongPress(m) })
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.size(2.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(m: MediaItem, selected: Boolean, selecting: Boolean, cell: Dp, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 0.85f else 1f, ExperienceMotion.fastSpatial(), label = "sel")
    val label = (if (m.isVideo) "Video" else "Photo") + ", " + m.name + if (m.favorite) ", favourite" else ""
    Box(
        modifier
            .aspectRatio(1f)
            .semantics { this.selected = selected; contentDescription = label }
            .combinedClickable(role = Role.Image, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select"),
    ) {
        MediaThumb(m, cell, Modifier.fillMaxSize().scale(scale).clip(if (selected) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall))
        if (m.isVideo) {
            Row(Modifier.align(Alignment.BottomEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Timeline.duration(m.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (m.favorite) Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp))
        if (selecting) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp)) {
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
