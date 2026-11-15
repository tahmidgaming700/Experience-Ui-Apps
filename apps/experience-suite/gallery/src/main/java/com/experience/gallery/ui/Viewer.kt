package com.experience.gallery.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.text.format.Formatter
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.experience.design.components.EmptyState
import com.experience.gallery.MediaActions
import com.experience.gallery.data.Thumbnails
import com.experience.gallery.model.MediaItem
import com.experience.gallery.model.Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen viewer: horizontal pager, pinch/double-tap zoom and pan for photos,
 * inline playback for videos, and the action bar (share, edit, favourite, delete…).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(
    items: List<MediaItem>,
    startIndex: Int,
    onPageChange: (Int) -> Unit,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onFavorite: (MediaItem) -> Unit,
    onPick: ((MediaItem) -> Unit)?,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<MediaItem?>(null) }
    BackHandler(onBack = onClose)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { zoomed = false; onPageChange(it) } }
    ImmersiveBars(visible = chrome)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { items[it].uri.toString() }, beyondViewportPageCount = 1) { page ->
            val item = items[page]
            if (item.isVideo) VideoPage(item, active = pager.settledPage == page, onTap = { chrome = !chrome })
            else ZoomableImage(item, onTap = { chrome = !chrome }, onZoomChanged = { if (pager.settledPage == page) zoomed = it })
        }
        val current = items.getOrNull(pager.currentPage) ?: return@Box
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close viewer", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current.inMediaStore) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(current.timestamp)), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
                Text("${pager.currentPage + 1} / ${items.size}", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPick != null) {
                    Button(onClick = { onPick(current) }) { Text("Select") }
                } else {
                    val tint = Color.White
                    IconButton(onClick = { MediaActions.share(context, listOf(current)) }) { Icon(Icons.Filled.Share, "Share", tint = tint) }
                    IconButton(onClick = { MediaActions.edit(context, current) }) { Icon(Icons.Filled.Edit, "Edit", tint = tint) }
                    if (current.inMediaStore) {
                        IconButton(onClick = { onFavorite(current) }) {
                            Icon(if (current.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (current.favorite) "Remove from favourites" else "Add to favourites", tint = tint)
                        }
                    }
                    IconButton(onClick = { info = current }) { Icon(Icons.Filled.Info, "Details", tint = tint) }
                    if (current.inMediaStore) IconButton(onClick = { onDelete(current) }) { Icon(Icons.Filled.Delete, "Delete", tint = tint) }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options", tint = tint) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            if (!current.isVideo) DropdownMenuItem(text = { Text("Set as wallpaper / Use as") }, onClick = { menu = false; MediaActions.setAs(context, current) })
                            DropdownMenuItem(text = { Text("Open with another app") }, onClick = {
                                menu = false
                                val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(current.uri, current.mime)
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(android.content.Intent.createChooser(view, "Open with")) }
                            })
                        }
                    }
                }
            }
        }
    }
    info?.let { item -> ModalBottomSheet(onDismissRequest = { info = null }) { InfoSheet(item) } }
}

@Composable
private fun InfoSheet(item: MediaItem) {
    val context = LocalContext.current
    SelectionContainer {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Details", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            InfoRow("Name", item.name)
            if (item.inMediaStore) InfoRow(if (item.isVideo) "Recorded" else "Taken", DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(item.timestamp)))
            if (item.size > 0) InfoRow("Size", "${Formatter.formatFileSize(context, item.size)} (${item.size} bytes)")
            if (item.width > 0 && item.height > 0) InfoRow("Resolution", "${item.width} × ${item.height} (${"%.1f".format(item.width * item.height / 1_000_000f)} MP)")
            if (item.isVideo && item.durationMs > 0) InfoRow("Duration", Timeline.duration(item.durationMs))
            InfoRow("Type", item.mime)
            if (item.bucketName.isNotBlank()) InfoRow("Album", item.bucketName)
            InfoRow("Path", item.path ?: item.uri.toString())
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Hides the status and navigation bars while the viewer chrome is hidden. */
@Composable
private fun ImmersiveBars(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(visible) {
        val ctl = WindowCompat.getInsetsController(window, view)
        ctl.isAppearanceLightStatusBars = false
        ctl.isAppearanceLightNavigationBars = false
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) ctl.show(WindowInsetsCompat.Type.systemBars()) else ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctl.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Decodes a display-sized bitmap (longest side ≤ 4096 px; EXIF rotation applied by ImageDecoder). */
private suspend fun decodeFull(context: android.content.Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val w = info.size.width; val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > 4096) {
                val f = 4096f / longest
                decoder.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()
}

@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var full by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    val placeholder = remember(item.uri) { Thumbnails.cached(item.uri, 512) ?: Thumbnails.cached(item.uri, 256) }
    LaunchedEffect(item.uri) {
        val b = decodeFull(context, item.uri)
        if (b == null) failed = true else full = b
    }
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1) / 2f
        val maxY = size.height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale; val startOffset = offset
        scope.launch {
            animate(0f, 1f, animationSpec = com.experience.design.theme.ExperienceMotion.defaultSpatial()) { t, _ ->
                scale = startScale + (targetScale - startScale) * t
                offset = startOffset + (targetOffset - startOffset) * t
            }
            onZoomChanged(targetScale > 1f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(item.uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.05f) animateTo(1f, Offset.Zero)
                        else {
                            val target = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            animateTo(target, clamp((center - tap) * (target - 1f), target))
                        }
                    },
                )
            }
            .pointerInput(item.uri) {
                // Custom transform detector: only consumes events while pinching or zoomed,
                // so a one-finger swipe at 1x still pages through the HorizontalPager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val k = newScale / scale
                            val c = if (centroid == Offset.Unspecified) Offset.Zero else centroid - Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((offset - c) * k + c + pan, newScale)
                            scale = newScale
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.02f) { scale = 1f; offset = Offset.Zero; onZoomChanged(false) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = full ?: placeholder
        when {
            bmp != null -> Image(
                bmp.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
            )
            failed -> EmptyState(Icons.Outlined.BrokenImage, "Can't display this image", item.name)
        }
    }
}

@Composable
private fun VideoPage(item: MediaItem, active: Boolean, onTap: () -> Unit) {
    var playing by remember(item.uri) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) playing = false }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
        if (playing) {
            // Platform VideoView + MediaController: hardware-decoded playback with seek bar.
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(item.uri)
                        setOnPreparedListener { start(); controller.show(3000) }
                        setOnCompletionListener { playing = false }
                        setOnErrorListener { _, _, _ -> playing = false; true }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val poster = remember(item.uri) { Thumbnails.cached(item.uri, 512) ?: Thumbnails.cached(item.uri, 256) }
            var bmp by remember(item.uri) { mutableStateOf(poster) }
            val context = LocalContext.current
            LaunchedEffect(item.uri) { if (bmp == null) bmp = Thumbnails.load(context.contentResolver, item.uri, 512) }
            bmp?.let { Image(it.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            FilledIconButton(
                onClick = { playing = true },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", modifier = Modifier.size(48.dp)) }
        }
    }
}
