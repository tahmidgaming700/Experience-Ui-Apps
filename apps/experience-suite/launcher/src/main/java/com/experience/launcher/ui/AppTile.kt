package com.experience.launcher.ui

import android.app.ActivityOptions
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.experience.launcher.data.IconLoader
import com.experience.launcher.data.LauncherApp

/** Text style for labels drawn directly on the wallpaper (white with a soft shadow). */
@Composable
fun wallpaperTextStyle(base: TextStyle = MaterialTheme.typography.labelMedium) =
    base.copy(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 8f))

/** Holds [initial] and replaces it with the result of [load] whenever [keys] change. */
@Composable
fun <T> rememberLoaded(initial: T, vararg keys: Any?, load: suspend () -> T): T {
    var state by remember(*keys) { mutableStateOf(initial) }
    LaunchedEffect(*keys) { state = load() }
    return state
}

/** Accessible description of an app, including states otherwise shown only visually. */
fun describe(app: LauncherApp): String = buildString {
    append(app.label)
    if (app.isWork) append(", work")
    if (app.isSuspended) append(", paused")
}

@Composable
fun AppIconImage(app: LauncherApp, loader: IconLoader, size: Dp, modifier: Modifier = Modifier) {
    val version by loader.version.collectAsState()
    val bitmap: ImageBitmap? = rememberLoaded(loader.peek(app.key), app.key, version) { loader.peek(app.key) ?: loader.load(app) }
    val shape = RoundedCornerShape(percent = (IconLoader.CORNER_FRACTION * 100).toInt())
    val current = bitmap
    if (current != null) {
        Image(
            current,
            contentDescription = null,
            modifier = modifier.size(size),
            // Paused / suspended apps are greyed out (and also described as "paused").
            colorFilter = if (app.isSuspended) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        )
    } else {
        Box(modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.2f)))
    }
}

/**
 * One app: icon plus optional label. Tap launches with a scale-up animation from the
 * icon's bounds; long-press (also offered as an accessibility action) opens options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: LauncherApp,
    loader: IconLoader,
    showLabel: Boolean,
    onWallpaper: Boolean,
    onLaunch: (android.graphics.Rect?, Bundle?) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
) {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    Column(
        modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                bounds = android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            .combinedClickable(
                onClickLabel = "Open",
                onLongClickLabel = "App options",
                onClick = {
                    val b = bounds
                    val options = b?.let {
                        ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
                    }
                    onLaunch(b, options)
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = describe(app) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(app, loader, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = if (onWallpaper) wallpaperTextStyle() else MaterialTheme.typography.labelMedium,
                color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
