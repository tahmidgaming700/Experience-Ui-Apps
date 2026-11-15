package com.experience.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import com.experience.launcher.LauncherViewModel
import com.experience.launcher.data.LauncherApp
import com.experience.launcher.model.HomeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MenuAction(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val enabled: Boolean = true, val run: () -> Unit)

/**
 * Options for one app: its app shortcuts (when this launcher is the default home),
 * pin/unpin and reorder on home and dock, App info and Uninstall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(app: LauncherApp, layout: HomeLayout, viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shortcuts = rememberLoaded(emptyList<ShortcutInfo>(), app.key) {
        withContext(Dispatchers.IO) { viewModel.shortcuts(app) }
    }
    fun act(block: () -> Unit) = { block(); onDismiss() }

    val actions = buildList {
        if (layout.isOnHome(app.key)) {
            add(MenuAction("Remove from home", Icons.Outlined.RemoveCircleOutline, run = act { viewModel.unpinFromHome(app.key) }))
            add(MenuAction("Move earlier", Icons.AutoMirrored.Outlined.ArrowBack, enabled = layout.home.firstOrNull() != app.key, run = { viewModel.moveOnHome(app.key, -1) }))
            add(MenuAction("Move later", Icons.AutoMirrored.Outlined.ArrowForward, enabled = layout.home.lastOrNull() != app.key, run = { viewModel.moveOnHome(app.key, 1) }))
        } else {
            add(MenuAction("Add to home", Icons.Outlined.AddToHomeScreen, run = act { viewModel.pinToHome(app.key) }))
        }
        if (layout.isInDock(app.key)) {
            add(MenuAction("Remove from dock", Icons.Outlined.RemoveCircleOutline, run = act { viewModel.unpinFromDock(app.key) }))
            add(MenuAction("Move left in dock", Icons.AutoMirrored.Outlined.ArrowBack, enabled = layout.dock.firstOrNull() != app.key, run = { viewModel.moveInDock(app.key, -1) }))
        } else {
            val full = layout.dockIsFull()
            add(MenuAction(if (full) "Dock is full" else "Add to dock", Icons.Outlined.Dock, enabled = !full, run = act { viewModel.pinToDock(app.key) }))
        }
        add(MenuAction("App info", Icons.Outlined.Info, run = act { viewModel.repository.openAppInfo(app) }))
        if (!app.isSystem) {
            add(MenuAction("Uninstall", Icons.Outlined.Delete, run = act {
                val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
                    .putExtra(Intent.EXTRA_USER, app.user)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
            }))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                AppIconImage(app, viewModel.icons, 48.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (app.isWork) Text("Work profile", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shortcuts.isNotEmpty()) {
                SectionHeader("Shortcuts")
                shortcuts.forEachIndexed { i, sc -> ShortcutRow(sc, i, shortcuts.size, viewModel, onDismiss) }
            }
            SectionHeader("Options")
            actions.forEachIndexed { i, a ->
                GroupedRow(
                    title = a.title, icon = a.icon, index = i, count = actions.size,
                    onClick = if (a.enabled) a.run else null,
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: ShortcutInfo, index: Int, count: Int, viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val icon = rememberLoaded<androidx.compose.ui.graphics.ImageBitmap?>(null, shortcut.id) {
        viewModel.icons.loadDrawable(
            "shortcut:${shortcut.`package`}:${shortcut.id}",
            viewModel.repository.shortcutIcon(shortcut, context.resources.displayMetrics.densityDpi),
        )
    }
    val label = (shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString()
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button) {
                    viewModel.repository.startShortcut(shortcut)
                    viewModel.closeDrawer()
                    onDismiss()
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) } ?: Spacer(Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
