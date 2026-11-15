package com.experience.settings.ui.pages

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.GroupedRow
import com.experience.design.components.SwitchRow
import com.experience.settings.Page
import com.experience.settings.system.OverlayEntry
import com.experience.settings.system.Overlays
import com.experience.settings.ui.ChoiceDialog
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Navigation modes, mapped to the SystemUI navbar overlays of Android 11. */
enum class NavMode(val label: String, val pkg: String, val summary: String) {
    GESTURE("Gesture navigation", Overlays.NAV_GESTURAL, "Swipe up to go home, swipe from an edge to go back"),
    THREE_BUTTON("3-button navigation", Overlays.NAV_THREE_BUTTON, "Back, Home and Overview buttons"),
    TWO_BUTTON("2-button navigation", Overlays.NAV_TWO_BUTTON, "Swipe up on Home for Overview");

    companion object {
        /** The enabled navbar overlay decides the mode; with none enabled AOSP uses 3-button. */
        fun current(entries: List<OverlayEntry>): NavMode? {
            if (entries.isEmpty()) return null
            val on = entries.firstOrNull { it.enabled }?.packageName
            return NavMode.entries.firstOrNull { it.pkg == on } ?: THREE_BUTTON
        }
    }
}

private fun imeLabel(context: Context): String? {
    val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return null
    val cn = ComponentName.unflattenFromString(id) ?: return null
    return runCatching { context.packageManager.getApplicationInfo(cn.packageName, 0).loadLabel(context.packageManager).toString() }.getOrNull()
}

@Composable
fun SystemPage(onBack: (() -> Unit)?, onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val overlays = remember { Overlays(context) }
    var navEntries by remember { mutableStateOf<List<OverlayEntry>>(emptyList()) }
    suspend fun loadNav() { navEntries = withContext(Dispatchers.IO) { overlays.list(Overlays.CATEGORY_NAVBAR) } }
    LaunchedEffect(Unit) { loadNav() }
    val use24 = rememberSystemValue { DateFormat.is24HourFormat(context) }
    val devEnabled = rememberSystemValue { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }
    var navDialog by remember { mutableStateOf(false) }
    val locales = context.resources.configuration.locales
    val nav = NavMode.current(navEntries)
    val available = NavMode.entries.filter { m -> navEntries.any { it.packageName == m.pkg } }

    PageScaffold("System", onBack) {
        Group("Language & input") {
            link(context, "Languages", (0 until locales.size()).joinToString { locales[it].displayName }, Icons.Outlined.Language, Intent(Settings.ACTION_LOCALE_SETTINGS))
            link(context, "Keyboard", imeLabel(context) ?: "On-screen keyboard and tools", Icons.Outlined.Keyboard, Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        Group("Date & time") {
            row { i, n ->
                SwitchRow("Use 24-hour format", use24.value, { on ->
                    SystemSettings.putSystemString(context, Settings.System.TIME_12_24, if (on) "24" else "12"); use24.refresh()
                }, i, n, summary = DateFormat.getTimeFormat(context).format(java.util.Date()), icon = Icons.Outlined.Schedule)
            }
            link(context, "Date, time & time zone", TimeZone.getDefault().getDisplayName(false, TimeZone.LONG), Icons.Outlined.AccessTime, Intent(Settings.ACTION_DATE_SETTINGS))
        }
        if (nav != null && available.size > 1) {
            Group("Gestures") {
                row { i, n -> GroupedRow("Navigation mode", i, n, summary = nav.label, icon = Icons.Outlined.Gesture, onClick = { navDialog = true }) }
            }
        }
        Group("Software") {
            row { i, n -> GroupedRow("System updates", i, n, summary = "Experience UI updates", icon = Icons.Outlined.SystemUpdate, onClick = { onOpen(Page.UPDATES) }) }
            if (devEnabled.value) link(context, "Developer options", null, Icons.Outlined.Code, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            link(context, "Reset options", "Reset network, app preferences, or erase all data", Icons.Outlined.RestartAlt,
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$ResetDashboardActivity")),
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$SystemDashboardActivity")))
        }
    }

    if (navDialog && nav != null) {
        ChoiceDialog("Navigation mode", available.map { it to "${it.label}\n${it.summary}" }, nav, onSelect = { mode ->
            scope.launch {
                // Same mechanism as AOSP SystemNavigationGestureSettings: exclusive overlay in the navbar category.
                val ok = withContext(Dispatchers.IO) { overlays.enableExclusive(mode.pkg) }
                if (!ok) msg(SystemSettings.NEEDS_SYSTEM)
                loadNav()
            }
        }, onDismiss = { navDialog = false })
    }
}
