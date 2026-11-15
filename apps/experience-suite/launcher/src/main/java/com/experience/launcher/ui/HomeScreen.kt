package com.experience.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceShape
import com.experience.launcher.data.IconLoader
import com.experience.launcher.data.LauncherApp
import com.experience.launcher.data.LauncherPrefs
import com.experience.launcher.model.GridSpec

/** Callbacks shared by the home grid and the dock. */
class HomeActions(
    val onLaunch: (LauncherApp, android.graphics.Rect?, Bundle?) -> Unit,
    val onAppLongPress: (LauncherApp) -> Unit,
    val onHomeLongPress: () -> Unit,
    val onOpenDrawer: () -> Unit,
)

/**
 * Expands the notification shade (swipe down on home). StatusBarManager is a hidden
 * service; the call needs only the normal EXPAND_STATUS_BAR permission.
 */
@SuppressLint("WrongConstant")
fun expandNotifications(context: Context) {
    runCatching {
        val sbm = context.getSystemService("statusbar") ?: return
        sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeApps: List<LauncherApp>,
    dockApps: List<LauncherApp>,
    prefs: LauncherPrefs,
    columns: Int,
    pagerState: PagerState,
    onPageCount: (Int) -> Unit,
    loader: IconLoader,
    actions: HomeActions,
) {
    val context = LocalContext.current
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Long-press on empty space: home options. Icons consume their own presses.
            .pointerInput(Unit) { detectTapGestures(onLongPress = { actions.onHomeLongPress() }) }
            // Swipe up: app drawer. Swipe down: notification shade.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -threshold) actions.onOpenDrawer()
                        else if (total > threshold) expandNotifications(context)
                    },
                    onVerticalDrag = { change, amount -> total += amount; change.consume() },
                )
            },
    ) {
        if (prefs.showClock) ClockHeader()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rows = GridSpec.homeRows(maxHeight.value.toInt())
            val pages = GridSpec.pages(homeApps, rows * columns)
            val state = pagerState
            SideEffect { onPageCount(pages.size) }
            if (homeApps.isEmpty()) {
                EmptyHomeHint(Modifier.align(Alignment.Center))
            }
            HorizontalPager(state = state, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                HomePage(pages.getOrElse(page) { emptyList() }, rows, columns, prefs.showLabels, loader, actions)
            }
            if (pages.size > 1) {
                PageIndicator(state.currentPage, pages.size, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
            }
        }
        Dock(dockApps, prefs.dockEnabled, loader, actions)
    }
}

@Composable
private fun EmptyHomeHint(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(
            "Swipe up for all apps. Long-press an app to pin it here.",
            style = wallpaperTextStyle(androidx.compose.material3.MaterialTheme.typography.bodyLarge),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomePage(
    items: List<LauncherApp>,
    rows: Int,
    columns: Int,
    showLabels: Boolean,
    loader: IconLoader,
    actions: HomeActions,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until columns) {
                    val app = items.getOrNull(r * columns + c)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (app != null) {
                            AppTile(
                                app = app, loader = loader, showLabel = showLabels, onWallpaper = true,
                                onLaunch = { b, o -> actions.onLaunch(app, b, o) },
                                onLongPress = { actions.onAppLongPress(app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Page dots; the current page is a wider pill so the state does not rely on colour. */
@Composable
private fun PageIndicator(current: Int, count: Int, modifier: Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .animateContentSize()
                    .height(6.dp)
                    .width(if (i == current) 20.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (i == current) 1f else 0.55f), CircleShape),
            )
        }
    }
}

/** Translucent dock with the favourite apps and the all-apps button. */
@Composable
private fun Dock(apps: List<LauncherApp>, enabled: Boolean, loader: IconLoader, actions: HomeActions) {
    Surface(
        shape = ExperienceShape.hero,
        color = if (enabled) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 640.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enabled) {
                apps.forEach { app ->
                    AppTile(
                        app = app, loader = loader, showLabel = false, onWallpaper = true,
                        onLaunch = { b, o -> actions.onLaunch(app, b, o) },
                        onLongPress = { actions.onAppLongPress(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(
                    onClick = actions.onOpenDrawer,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.28f), contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = "All apps")
                }
            }
        }
    }
}
