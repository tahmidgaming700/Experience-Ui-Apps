package com.experience.settings.ui.pages

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.BroadcastEffect
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SettingsObserverEffect
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import com.experience.settings.ui.startSafely

/** Settings.Secure key behind "Notification dots" (not a public constant). */
private const val NOTIFICATION_BADGING = "notification_badging"

@Composable
fun NotificationsPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val nm = context.getSystemService(NotificationManager::class.java)
    val dnd = rememberSystemValue { nm.currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } }
    val access = rememberSystemValue { nm.isNotificationPolicyAccessGranted }
    val dots = rememberSystemValue { Settings.Secure.getInt(context.contentResolver, NOTIFICATION_BADGING, 1) == 1 }
    BroadcastEffect(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED, NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED) { dnd.refresh(); access.refresh() }
    SettingsObserverEffect(listOf(Settings.Secure.getUriFor(NOTIFICATION_BADGING))) { dots.refresh() }

    PageScaffold("Notifications", onBack) {
        Group("Do Not Disturb") {
            row { i, n ->
                SwitchRow(
                    "Do Not Disturb", dnd.value,
                    { on ->
                        if (!access.value) {
                            msg("Allow Do Not Disturb access first")
                            context.startSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        } else {
                            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
                        dnd.refresh()
                    },
                    i, n, summary = if (dnd.value) "On · only priority interruptions" else "Off", icon = Icons.Outlined.DoNotDisturbOn,
                )
            }
            link(context, "Do Not Disturb schedules & exceptions", null, Icons.Outlined.Tune, Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
            if (!access.value) link(context, "Allow Do Not Disturb access", "Lets Experience Settings switch Do Not Disturb", Icons.Outlined.VpnKey, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        Group("General") {
            if (SystemSettings.hasSecureWrite(context)) row { i, n ->
                SwitchRow("Notification dots on app icons", dots.value, { on ->
                    if (!SystemSettings.putSecureInt(context, NOTIFICATION_BADGING, if (on) 1 else 0)) msg(SystemSettings.NEEDS_SYSTEM)
                    dots.refresh()
                }, i, n, icon = Icons.Outlined.Circle)
            }
            link(context, "App notifications", "Choose which apps can notify", Icons.Outlined.Apps, Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            link(context, "Notification history", null, Icons.Outlined.History, Intent("android.settings.NOTIFICATION_HISTORY"))
            link(context, "Notification settings", "Lock screen, bubbles, conversations", Icons.Outlined.NotificationsActive, Intent("android.settings.NOTIFICATION_SETTINGS"))
            link(context, "Notification access", "Apps that can read notifications", Icons.Outlined.VpnKey, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}
