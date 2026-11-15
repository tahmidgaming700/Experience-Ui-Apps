package com.experience.files.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.experience.files.data.Mime
import com.experience.files.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

enum class Kind { FOLDER, IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, ARCHIVE, APK, OTHER }

fun FileItem.kind(): Kind {
    if (isDirectory) return Kind.FOLDER
    val mime = Mime.of(name)
    return when {
        extension == "apk" -> Kind.APK
        extension in setOf("zip", "7z", "rar", "tar", "gz", "xz", "bz2") -> Kind.ARCHIVE
        mime.startsWith("image/") -> Kind.IMAGE
        mime.startsWith("video/") -> Kind.VIDEO
        mime.startsWith("audio/") -> Kind.AUDIO
        mime == "application/pdf" -> Kind.PDF
        mime.startsWith("text/") || mime.contains("document") || mime.contains("msword") || mime.contains("sheet") || mime.contains("presentation") -> Kind.DOCUMENT
        else -> Kind.OTHER
    }
}

fun Kind.icon(): ImageVector = when (this) {
    Kind.FOLDER -> Icons.Outlined.Folder
    Kind.IMAGE -> Icons.Outlined.Image
    Kind.VIDEO -> Icons.Outlined.VideoFile
    Kind.AUDIO -> Icons.Outlined.AudioFile
    Kind.PDF -> Icons.Outlined.PictureAsPdf
    Kind.DOCUMENT -> Icons.Outlined.Description
    Kind.ARCHIVE -> Icons.Outlined.FolderZip
    Kind.APK -> Icons.Outlined.Android
    Kind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
fun formatDate(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun FileItem.summary(context: Context): String = if (isDirectory) {
    val n = childCount
    (if (n < 0) "Folder" else "$n item${if (n == 1) "" else "s"}") + " · " + formatDate(lastModified)
} else formatSize(context, size) + " · " + formatDate(lastModified)

/** Small in-memory cache of decoded thumbnails, sized to 1/16 of the heap. */
private object ThumbCache : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/**
 * Icon or real thumbnail (images/videos/audio art) for [item]. Thumbnails are made
 * off the main thread with ThumbnailUtils (API 29+) and cancelled when scrolled away.
 */
@Composable
fun FileThumb(item: FileItem, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val kind = item.kind()
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.coerceAtLeast(64)
    val key = "${item.path}:${item.lastModified}:${item.size}:$px"
    var bitmap by remember(key) { mutableStateOf(ThumbCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null && kind in setOf(Kind.IMAGE, Kind.VIDEO, Kind.AUDIO)) {
            val made = withContext(Dispatchers.IO) { makeThumb(File(item.path), kind, px) }
            if (made != null) ThumbCache.put(key, made)
            bitmap = made
        }
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.size(size)) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size / 2))
            }
        }
    }
}

private suspend fun makeThumb(file: File, kind: Kind, px: Int): Bitmap? = suspendCancellableCoroutine { cont ->
    val signal = CancellationSignal()
    cont.invokeOnCancellation { signal.cancel() }
    val bmp = runCatching {
        val s = Size(px, px)
        when (kind) {
            Kind.IMAGE -> ThumbnailUtils.createImageThumbnail(file, s, signal)
            Kind.VIDEO -> ThumbnailUtils.createVideoThumbnail(file, s, signal)
            Kind.AUDIO -> ThumbnailUtils.createAudioThumbnail(file, s, signal)
            else -> null
        }
    }.getOrNull()
    if (cont.isActive) cont.resumeWith(Result.success(bmp))
}
