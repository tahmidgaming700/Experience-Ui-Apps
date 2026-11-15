package com.experience.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import com.experience.launcher.data.LauncherPrefs
import com.experience.launcher.model.GridSpec

const val EXPERIENCE_SETTINGS_PACKAGE = "com.experience.settings"

/** Long-press on the home background: wallpaper, Experience Settings, launcher settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(onDismiss: () -> Unit, onLauncherSettings: () -> Unit) {
    val context = LocalContext.current
    val settingsIntent = remember { context.packageManager.getLaunchIntentForPackage(EXPERIENCE_SETTINGS_PACKAGE) }
    fun start(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) { }
        onDismiss()
    }
    val rows = buildList<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>> {
        add(Triple("Wallpaper", Icons.Outlined.Wallpaper) {
            start(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper from"))
        })
        if (settingsIntent != null) add(Triple("Experience Settings", Icons.Outlined.Settings) { start(Intent(settingsIntent)) })
        add(Triple("Home screen settings", Icons.Outlined.Tune) { onDismiss(); onLauncherSettings() })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Home screen", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            rows.forEachIndexed { i, (title, icon, run) -> GroupedRow(title = title, icon = icon, index = i, count = rows.size, onClick = run) }
        }
    }
}

/** Launcher preferences: grid columns, labels, clock and dock, layout reset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSettingsSheet(prefs: LauncherPrefs, onChange: ((LauncherPrefs) -> LauncherPrefs) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val width = LocalConfiguration.current.screenWidthDp
    val choices = GridSpec.homeColumnChoices(width)
    val current = GridSpec.effectiveHomeColumns(prefs.homeColumns, width)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Home screen settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
            SectionHeader("Grid columns")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                choices.forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = n == current,
                        onClick = { onChange { it.copy(homeColumns = n) } },
                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                        icon = { SegmentedButtonDefaults.Icon(n == current) { androidx.compose.material3.Icon(Icons.Outlined.GridView, null) } },
                    ) { Text("$n") }
                }
            }
            SectionHeader("Appearance")
            SwitchRow("Show app labels", prefs.showLabels, { v -> onChange { it.copy(showLabels = v) } }, 0, 3)
            SwitchRow("Show clock and date", prefs.showClock, { v -> onChange { it.copy(showClock = v) } }, 1, 3)
            SwitchRow("Show dock", prefs.dockEnabled, { v -> onChange { it.copy(dockEnabled = v) } }, 2, 3, summary = "Favourite apps at the bottom of the home screen")
            SectionHeader("Layout")
            GroupedRow("Reset home screen", 0, 1, summary = "Restore the default apps on home and dock", icon = Icons.Outlined.RestartAlt, onClick = { confirmReset = true })
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset home screen?") },
            text = { Text("Your pinned apps on the home screen and dock will be replaced by the default layout.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
