package com.experience.messages.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceShape
import com.experience.messages.data.BubbleLayout
import com.experience.messages.data.Message
import com.experience.messages.data.MessageStatus
import com.experience.messages.data.MmsPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Expressive bubble: large radii, tight corners where bubbles of one group meet. */
fun bubbleShape(incoming: Boolean, pos: BubbleLayout.Position): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    val top = if (pos.firstInGroup) big else small
    val bottom = if (pos.lastInGroup) big else small
    return if (incoming) RoundedCornerShape(topStart = top, topEnd = big, bottomEnd = big, bottomStart = bottom)
    else RoundedCornerShape(topStart = big, topEnd = top, bottomEnd = bottom, bottomStart = big)
}

@Composable
fun DateSeparator(millis: Long) {
    val context = LocalContext.current
    val text = remember(millis) {
        when {
            DateUtils.isToday(millis) -> "Today"
            DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_MONTH)
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = ExperienceShape.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    m: Message,
    pos: BubbleLayout.Position,
    senderName: String?,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val container = if (m.incoming) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary
    val content = if (m.incoming) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier.fillMaxWidth().padding(top = if (pos.firstInGroup) 8.dp else 2.dp),
        horizontalAlignment = if (m.incoming) Alignment.Start else Alignment.End,
    ) {
        if (senderName != null && m.incoming && pos.firstInGroup) {
            Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
        }
        Box {
            Surface(
                shape = bubbleShape(m.incoming, pos),
                color = if (m.status == MessageStatus.FAILED) MaterialTheme.colorScheme.errorContainer else container,
                contentColor = if (m.status == MessageStatus.FAILED) MaterialTheme.colorScheme.onErrorContainer else content,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        onClickLabel = if (m.status == MessageStatus.FAILED) "Retry sending" else null,
                        onClick = { if (m.status == MessageStatus.FAILED && !m.isMms) onRetry() },
                        onLongClickLabel = "Message options",
                        onLongClick = { menu = true },
                    ),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (m.mmsNotDownloaded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Multimedia message" + (if (m.mmsSize > 0) " (${(m.mmsSize + 1023) / 1024} KB)" else "") +
                                    ". Downloading MMS isn't supported yet.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    m.parts.forEach { part ->
                        when (part) {
                            is MmsPart.Image -> MmsImage(part.uri)
                            is MmsPart.Other -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${part.name} (${part.contentType})", style = MaterialTheme.typography.bodySmall)
                            }
                            is MmsPart.Text -> Unit
                        }
                    }
                    if (m.body.isNotEmpty()) Text(m.body, style = MaterialTheme.typography.bodyLarge)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (m.body.isNotEmpty()) DropdownMenuItem(text = { Text("Copy text") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                if (m.status == MessageStatus.FAILED && !m.isMms) DropdownMenuItem(text = { Text("Retry") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = { menu = false; onRetry() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
        if (pos.lastInGroup || m.status == MessageStatus.FAILED || m.status == MessageStatus.SENDING) {
            StatusLine(context, m)
        }
    }
}

@Composable
private fun StatusLine(context: Context, m: Message) {
    val time = DateUtils.formatDateTime(context, m.dateMillis, DateUtils.FORMAT_SHOW_TIME)
    val (icon, label) = when (m.status) {
        MessageStatus.RECEIVED -> null to null
        MessageStatus.SENDING -> Icons.Filled.Schedule to m.status.label
        MessageStatus.SENT -> Icons.Filled.Done to m.status.label
        MessageStatus.DELIVERED -> Icons.Filled.DoneAll to m.status.label
        MessageStatus.NOT_DELIVERED -> Icons.Filled.ErrorOutline to m.status.label
        MessageStatus.FAILED -> Icons.Filled.Error to (m.status.label + if (m.isMms) "" else " · Tap to retry")
    }
    val color = if (m.status == MessageStatus.FAILED || m.status == MessageStatus.NOT_DELIVERED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(listOfNotNull(label, time).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.End)
    }
}

@Composable
private fun MmsImage(uri: String) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { bmp = withContext(Dispatchers.IO) { decodeSampled(context, uri, 1024) } }
    val b = bmp
    if (b != null) {
        Image(b, contentDescription = "Picture", contentScale = ContentScale.FillWidth,
            modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 292.dp).clip(RoundedCornerShape(16.dp)))
    } else {
        Box(Modifier.size(160.dp, 120.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Image, contentDescription = "Picture") }
    }
}

private fun decodeSampled(context: Context, uri: String, maxSide: Int): ImageBitmap? = runCatching {
    val u = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
}.getOrNull()
