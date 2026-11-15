package com.experience.settings.ui.pages

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.SwitchRow
import com.experience.design.theme.ExperienceShape
import com.experience.settings.ui.BroadcastEffect
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SettingsObserverEffect
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import com.experience.settings.ui.startSafely

data class BatteryInfo(
    val percent: Int, val status: String, val plugged: String?, val temperatureC: Float?, val health: String,
    val voltageMv: Int?, val technology: String?, val charging: Boolean,
)

/** Pure mapping of an ACTION_BATTERY_CHANGED intent's extras. */
object BatteryParser {
    fun parse(level: Int, scale: Int, status: Int, plugged: Int, temperatureTenths: Int, health: Int, voltage: Int, technology: String?): BatteryInfo {
        val percent = if (scale > 0 && level >= 0) (level * 100 / scale) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(
            percent = percent,
            status = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Fully charged"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            },
            plugged = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> null
            },
            temperatureC = if (temperatureTenths == Int.MIN_VALUE) null else temperatureTenths / 10f,
            health = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            },
            voltageMv = voltage.takeIf { it > 0 },
            technology = technology?.takeIf { it.isNotBlank() },
            charging = charging,
        )
    }

    fun fromIntent(i: Intent) = parse(
        i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
        i.getIntExtra(BatteryManager.EXTRA_STATUS, -1), i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE), i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
        i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1), i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
    )
}

/** PowerManager.setPowerSaveModeEnabled is @SystemApi (DEVICE_POWER or POWER_SAVER). */
private fun setPowerSave(context: Context, on: Boolean): Boolean = runCatching {
    val pm = context.getSystemService(PowerManager::class.java)
    pm.javaClass.getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType).invoke(pm, on) as Boolean
}.getOrDefault(false)

/** Hidden Settings.System key behind the status bar battery percentage. */
private const val SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent"

@Composable
fun BatteryPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val pm = context.getSystemService(PowerManager::class.java)
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }
    // ACTION_BATTERY_CHANGED is sticky: the current state arrives immediately on registration.
    BroadcastEffect(Intent.ACTION_BATTERY_CHANGED) { battery = BatteryParser.fromIntent(it) }
    val saver = rememberSystemValue { pm.isPowerSaveMode }
    BroadcastEffect(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) { saver.refresh() }
    val percentInBar = rememberSystemValue { Settings.System.getInt(context.contentResolver, SHOW_BATTERY_PERCENT, 0) == 1 }
    SettingsObserverEffect(listOf(Settings.System.getUriFor(SHOW_BATTERY_PERCENT))) { percentInBar.refresh() }
    val bm = context.getSystemService(BatteryManager::class.java)

    PageScaffold("Battery", onBack) {
        battery?.let { b ->
            Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text(if (b.percent >= 0) "${b.percent}%" else "—", style = MaterialTheme.typography.displayMedium)
                    val remaining = if (b.charging) bm?.computeChargeTimeRemaining()?.takeIf { it > 0 } else null
                    Text(
                        listOfNotNull(b.status, b.plugged?.let { "via $it" }, remaining?.let { "full in ${it / 60_000} min" }).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { b.percent.coerceAtLeast(0) / 100f }, modifier = Modifier.fillMaxWidth().height(12.dp), strokeCap = StrokeCap.Round, trackColor = MaterialTheme.colorScheme.surface)
                }
            }
            Group("Battery health") {
                row { i, n -> InfoRow("Health", b.health, i, n) }
                b.temperatureC?.let { t -> row { i, n -> InfoRow("Temperature", "%.1f °C".format(t), i, n) } }
                b.voltageMv?.let { v -> row { i, n -> InfoRow("Voltage", "%.2f V".format(v / 1000f), i, n) } }
                b.technology?.let { tech -> row { i, n -> InfoRow("Technology", tech, i, n) } }
            }
        }
        Group("Power") {
            row { i, n ->
                SwitchRow("Battery Saver", saver.value, { on ->
                    if (!setPowerSave(context, on)) {
                        msg(SystemSettings.NEEDS_SYSTEM)
                        context.startSafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    }
                    saver.refresh()
                }, i, n, summary = "Limits background activity and visual effects", icon = Icons.Outlined.BatterySaver)
            }
            row { i, n ->
                SwitchRow("Battery percentage", percentInBar.value, { on ->
                    // A non-public System key: writable by the platform-signed app only.
                    val ok = SystemSettings.canWriteSystem(context) &&
                        runCatching { Settings.System.putInt(context.contentResolver, SHOW_BATTERY_PERCENT, if (on) 1 else 0) }.getOrDefault(false)
                    if (!ok) msg(SystemSettings.NEEDS_SYSTEM)
                    percentInBar.refresh()
                }, i, n, summary = "Show percentage in the status bar", icon = Icons.Outlined.Percent)
            }
            link(context, "Battery usage", "Usage since last full charge", Icons.Outlined.Insights, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            link(context, "Battery Saver schedule", null, Icons.Outlined.BatteryStd, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
    }
}
