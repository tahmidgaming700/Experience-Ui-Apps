package com.experience.settings.ui.pages

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.HeroCard
import com.experience.design.system.SystemInfo
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.link
import java.text.DateFormat
import java.util.Date

/**
 * About Experience UI. Every value comes from build properties or Build fields;
 * values a build does not set are labelled as such instead of being guessed.
 */
@Composable
fun AboutPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val deviceName = remember { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL }
    fun orUnset(v: String) = v.ifBlank { "Not set in this build" }
    val buildDate = SystemInfo.experienceBuildDate.ifBlank { DateFormat.getDateInstance(DateFormat.LONG).format(Date(Build.TIME)) }

    PageScaffold("About Experience UI", onBack) {
        HeroCard(
            title = SystemInfo.experienceDisplayVersion,
            body = "$deviceName · Android ${SystemInfo.androidVersion}",
            icon = Icons.Outlined.PhoneAndroid,
            modifier = Modifier.padding(top = 8.dp),
        )
        Group("Experience UI") {
            row { i, n -> InfoRow("Experience UI version", SystemInfo.experienceVersion, i, n) }
            row { i, n -> InfoRow("Build number", SystemInfo.experienceBuildNumber, i, n) }
            row { i, n -> InfoRow("Release channel", SystemInfo.experienceReleaseChannel, i, n) }
            row { i, n -> InfoRow("Build date", buildDate, i, n) }
            row { i, n -> InfoRow("Base platform", orUnset(SystemInfo.baseRom), i, n) }
            row { i, n -> InfoRow("Generic system image (GSI)", if (SystemInfo.isGsi) "Yes" else "No", i, n) }
        }
        Group("Android") {
            row { i, n -> InfoRow("Android version", SystemInfo.androidVersion, i, n) }
            row { i, n -> InfoRow("API level", SystemInfo.sdkInt.toString(), i, n) }
            row { i, n -> InfoRow("Android security patch", SystemInfo.securityPatch.ifBlank { "Not reported" }, i, n) }
            row { i, n -> InfoRow("Vendor security patch", SystemInfo.vendorSecurityPatch.ifBlank { "Not reported" }, i, n) }
            row { i, n -> InfoRow("Kernel version", SystemInfo.kernel.ifBlank { "Unknown" }, i, n) }
            row { i, n -> InfoRow("Build fingerprint", SystemInfo.fingerprint, i, n) }
        }
        Group("Device") {
            row { i, n -> InfoRow("Device name", deviceName, i, n) }
            row { i, n -> InfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})", i, n) }
            row { i, n -> InfoRow("Hardware", Build.HARDWARE, i, n) }
            Build.getRadioVersion()?.takeIf { it.isNotBlank() }?.let { r -> row { i, n -> InfoRow("Baseband version", r, i, n) } }
            row { i, n -> InfoRow("Up time", DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000), i, n) }
        }
        Group("Legal") {
            link(context, "Open source licences", null, Icons.Outlined.Gavel, Intent("android.settings.LICENSE"))
            link(context, "Device information", "Status, IMEI, IP and MAC addresses", Icons.Outlined.Info, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }
}
