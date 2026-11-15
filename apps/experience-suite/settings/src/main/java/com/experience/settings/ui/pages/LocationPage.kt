package com.experience.settings.ui.pages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.GroupedRow
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.BroadcastEffect
import com.experience.settings.ui.Group
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import com.experience.settings.ui.startSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** LocationManager.setLocationEnabledForUser is @SystemApi (WRITE_SECURE_SETTINGS). */
private fun setLocation(context: Context, on: Boolean): Boolean = runCatching {
    val lm = context.getSystemService(LocationManager::class.java)
    lm.javaClass.getMethod("setLocationEnabledForUser", Boolean::class.javaPrimitiveType, UserHandle::class.java)
        .invoke(lm, on, Process.myUserHandle())
    lm.isLocationEnabled == on
}.getOrDefault(false)

private fun appsWithLocation(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return pm.getPackagesHoldingPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .mapNotNull { p -> p.applicationInfo?.let { p.packageName to it.loadLabel(pm).toString() } }
        .sortedBy { it.second.lowercase() }
}

@Composable
fun LocationPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val lm = context.getSystemService(LocationManager::class.java)
    val enabled = rememberSystemValue { lm.isLocationEnabled }
    BroadcastEffect(LocationManager.MODE_CHANGED_ACTION) { enabled.refresh() }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { appsWithLocation(context) } }

    PageScaffold("Location", onBack) {
        Group(null) {
            row { i, n ->
                SwitchRow("Use location", enabled.value, { on ->
                    if (!setLocation(context, on)) context.startSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    enabled.refresh()
                }, i, n, summary = if (enabled.value) "On" else "Off", icon = Icons.Outlined.LocationOn)
            }
            link(context, "Location services", "Wi-Fi and Bluetooth scanning, emergency location", Icons.Outlined.Radar,
                Intent("android.settings.LOCATION_SCANNING_SETTINGS"), Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        if (apps.isNotEmpty()) {
            Group("Apps allowed to use location") {
                apps.forEach { (pkg, label) ->
                    row { i, n ->
                        GroupedRow(label, i, n, onClick = {
                            context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
                        })
                    }
                }
            }
        }
    }
}
