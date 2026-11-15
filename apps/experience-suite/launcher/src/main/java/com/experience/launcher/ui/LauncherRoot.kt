package com.experience.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import com.experience.design.theme.ExperienceMotion
import com.experience.launcher.LauncherViewModel
import com.experience.launcher.data.LauncherApp
import com.experience.launcher.model.AppKey
import com.experience.launcher.model.GridSpec

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val apps by viewModel.apps.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val drawerOpen by viewModel.drawerOpen.collectAsState()

    var menuApp by remember { mutableStateOf<LauncherApp?>(null) }
    var homeMenu by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(1) }
    val pagerState = rememberPagerState { pageCount }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    fun resolve(keys: List<AppKey>) = keys.mapNotNull { byKey[it] }
    val columns = GridSpec.effectiveHomeColumns(prefs.homeColumns, LocalConfiguration.current.screenWidthDp)

    LaunchedEffect(viewModel) {
        viewModel.homePressed.collect { animate ->
            menuApp = null; homeMenu = false; settings = false
            if (animate) pagerState.animateScrollToPage(0) else pagerState.scrollToPage(0)
        }
    }
    BackHandler(enabled = drawerOpen) { viewModel.closeDrawer() }

    val launch: (LauncherApp, android.graphics.Rect?, android.os.Bundle?) -> Unit = { app, bounds, options ->
        viewModel.launch(app, bounds, options)
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            homeApps = resolve(layout.home),
            dockApps = resolve(layout.dock),
            prefs = prefs,
            columns = columns,
            pagerState = pagerState,
            onPageCount = { pageCount = it },
            loader = viewModel.icons,
            actions = HomeActions(
                onLaunch = launch,
                onAppLongPress = { menuApp = it },
                onHomeLongPress = { homeMenu = true },
                onOpenDrawer = viewModel::openDrawer,
            ),
        )
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(ExperienceMotion.defaultSpatial<IntOffset>()) { it / 3 } + fadeIn(ExperienceMotion.defaultEffects()),
            exit = slideOutVertically(ExperienceMotion.fastSpatial<IntOffset>()) { it / 3 } + fadeOut(ExperienceMotion.fastEffects()),
        ) {
            AppDrawer(
                apps = apps,
                profiles = profiles,
                loader = viewModel.icons,
                showLabels = true,
                onLaunch = launch,
                onLongPress = { menuApp = it },
                onClose = viewModel::closeDrawer,
                onQuietMode = viewModel::setQuietMode,
            )
        }
    }

    menuApp?.let { app -> AppMenuSheet(app, layout, viewModel) { menuApp = null } }
    if (homeMenu) HomeMenuSheet(onDismiss = { homeMenu = false }, onLauncherSettings = { settings = true })
    if (settings) {
        LauncherSettingsSheet(
            prefs = prefs,
            onChange = viewModel::updatePrefs,
            onReset = viewModel::resetLayout,
            onDismiss = { settings = false },
        )
    }
}
