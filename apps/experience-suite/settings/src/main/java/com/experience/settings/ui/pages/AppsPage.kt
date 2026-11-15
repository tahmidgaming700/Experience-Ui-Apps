package com.experience.settings.ui.pages

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.experience.design.components.GroupedRow
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.link
import com.experience.settings.ui.startSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?, val updated: Long)
private data class AppsInfo(val user: Int, val system: Int, val recent: List<AppEntry>, val defaults: List<Pair<String, String>>)

private fun label(context: Context, pkg: String?): String? = pkg?.let {
    runCatching { context.packageManager.getApplicationInfo(it, 0).loadLabel(context.packageManager).toString() }.getOrNull()
}

private fun loadApps(context: Context): AppsInfo {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(0)
    val system = apps.count { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
    val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName }.toSet()
    val recent = pm.getInstalledPackages(0)
        .filter { it.packageName in launchable && it.packageName != context.packageName }
        .sortedByDescending { it.lastUpdateTime }
        .take(5)
        .map { p ->
            val ai = p.applicationInfo
            AppEntry(
                p.packageName,
                ai?.loadLabel(pm)?.toString() ?: p.packageName,
                runCatching { ai?.loadIcon(pm)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull(),
                p.lastUpdateTime,
            )
        }
    val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val browser = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
    val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
    // "android" means the resolver chooser: no default set.
    fun name(pkg: String?) = if (pkg == null || pkg == "android") "Not set" else label(context, pkg) ?: pkg
    val defaults = buildList {
        add("Home app" to name(home))
        add("Browser" to name(browser))
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            add("Phone" to name(dialer))
            add("SMS" to name(sms))
        }
    }
    return AppsInfo(apps.size - system, system, recent, defaults)
}

@Composable
fun AppsPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<AppsInfo?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { loadApps(context) } }

    PageScaffold("Apps", onBack) {
        val data = info
        if (data != null) {
            Group("Installed") {
                row { i, n -> InfoRow("Apps", "${data.user} installed by you · ${data.system} system apps", i, n) }
                link(context, "See all apps", null, Icons.Outlined.Apps, Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS), Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            }
            if (data.recent.isNotEmpty()) {
                Group("Recently updated") {
                    data.recent.forEach { app ->
                        row { i, n ->
                            GroupedRow(
                                app.label, i, n,
                                summary = "Updated " + DateUtils.getRelativeTimeSpanString(app.updated),
                                onClick = { context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))) },
                                trailing = { app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) } },
                            )
                        }
                    }
                }
            }
            Group("Default apps") {
                data.defaults.forEach { (role, app) ->
                    row { i, n ->
                        val icon = when (role) { "Home app" -> Icons.Outlined.Home; "Browser" -> Icons.Outlined.Language; "Phone" -> Icons.Outlined.Phone; else -> Icons.AutoMirrored.Outlined.Message }
                        GroupedRow(role, i, n, summary = app, icon = icon, onClick = { context.startSafely(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) })
                    }
                }
                link(context, "Change default apps", null, Icons.Outlined.Star, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
        Group("Special access") {
            link(context, "Install unknown apps", null, Icons.Outlined.InstallMobile, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            link(context, "Usage access", null, Icons.Outlined.Tune, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            link(context, "Display over other apps", null, Icons.Outlined.Tune, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            link(context, "Modify system settings", null, Icons.Outlined.Tune, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
        }
    }
}
