package com.experience.launcher.ui

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.experience.design.components.EmptyState
import com.experience.launcher.data.IconLoader
import com.experience.launcher.data.LauncherApp
import com.experience.launcher.data.Profile
import com.experience.launcher.model.AppSearch
import com.experience.launcher.model.GridSpec

private enum class ProfileFilter(val label: String) { ALL("All"), PERSONAL("Personal"), WORK("Work") }

/**
 * Full-screen app drawer: search, optional personal/work filter (with the work
 * profile pause switch) and an alphabetical grid with letter sections. Pulling down
 * at the top of the list, Back, or HOME closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    apps: List<LauncherApp>,
    profiles: List<Profile>,
    loader: IconLoader,
    showLabels: Boolean,
    onLaunch: (LauncherApp, android.graphics.Rect?, Bundle?) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
    onClose: () -> Unit,
    onQuietMode: (Profile, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ProfileFilter.ALL) }
    val workProfile = profiles.firstOrNull { it.isWork }
    val focus = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val closeDistance = with(LocalDensity.current) { 96.dp.toPx() }

    // Overscrolling downwards at the top of the grid closes the drawer.
    val pullToClose = remember(onClose) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > closeDistance) { pulled = 0f; onClose() }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { pulled = 0f; return Velocity.Zero }
        }
    }

    val visible = remember(apps, query, filter) {
        val byProfile = when (filter) {
            ProfileFilter.ALL -> apps
            ProfileFilter.PERSONAL -> apps.filter { !it.isWork }
            ProfileFilter.WORK -> apps.filter { it.isWork }
        }
        AppSearch.search(byProfile, query, label = { it.label }, packageName = { it.component.packageName })
    }
    LaunchedEffect(query, filter) { gridState.scrollToItem(0) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle doubling as an accessible "close" control.
            Box(
                Modifier
                    .size(width = 72.dp, height = 24.dp)
                    .clickable(role = Role.Button, onClickLabel = "Close app drawer", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    visible.firstOrNull()?.let { onLaunch(it, null, null) }
                    focus.clearFocus()
                }),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (workProfile != null) {
                SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    ProfileFilter.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = filter == f,
                            onClick = { filter = f },
                            shape = SegmentedButtonDefaults.itemShape(i, ProfileFilter.entries.size),
                        ) { Text(f.label) }
                    }
                }
                if (filter == ProfileFilter.WORK) {
                    WorkProfileSwitch(workProfile, onQuietMode)
                }
            }
            if (visible.isEmpty()) {
                EmptyState(
                    Icons.Outlined.SearchOff,
                    title = if (query.isEmpty()) "No apps" else "No apps match “$query”",
                )
            } else {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = GridSpec.drawerColumns(maxWidth.value.toInt())
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().nestedScroll(pullToClose),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val sectioned = query.isBlank()
                        var lastSection: String? = null
                        val rows = ArrayList<Any>()
                        visible.forEach { app ->
                            if (sectioned && app.section != lastSection) {
                                lastSection = app.section
                                rows += app.section
                            }
                            rows += app
                        }
                        items(
                            rows,
                            key = { if (it is LauncherApp) it.key.encode() else "section:$it" },
                            span = { if (it is String) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                            contentType = { if (it is String) 0 else 1 },
                        ) { row ->
                            if (row is LauncherApp) {
                                AppTile(
                                    app = row, loader = loader, showLabel = showLabels, onWallpaper = false,
                                    onLaunch = { b, o -> onLaunch(row, b, o) },
                                    onLongPress = { onLongPress(row) },
                                )
                            } else {
                                Text(
                                    row as String,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 12.dp, top = 12.dp, bottom = 2.dp)
                                        .semantics { heading() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkProfileSwitch(profile: Profile, onQuietMode: (Profile, Boolean) -> Unit) {
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(56.dp)
            .clickable(role = Role.Switch) { onQuietMode(profile, !profile.isQuiet) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Work apps", style = MaterialTheme.typography.titleMedium)
            Text(
                if (profile.isQuiet) "Paused: no notifications or background activity" else "On",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.width(8.dp))
        Switch(checked = !profile.isQuiet, onCheckedChange = { on -> onQuietMode(profile, !on) })
    }
}
