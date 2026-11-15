package com.experience.settings.ui.pages

import android.app.UiModeManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.GroupedRow
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.ChoiceDialog
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SettingsObserverEffect
import com.experience.settings.ui.SliderRow
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue
import kotlin.math.roundToInt

private val TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)
private fun timeoutLabel(ms: Int) = if (ms < 60_000) "${ms / 1000} seconds" else if (ms == 60_000) "1 minute" else "${ms / 60_000} minutes"
private val FONT_SCALES = listOf(0.85f to "Small", 1.0f to "Default", 1.15f to "Large", 1.3f to "Largest")

@Composable
fun DisplayPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val cr = context.contentResolver
    val ui = context.getSystemService(UiModeManager::class.java)
    val brightness = rememberSystemValue { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) }
    val adaptive = rememberSystemValue { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC }
    val night = rememberSystemValue { ui.nightMode }
    val timeout = rememberSystemValue { Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60_000) }
    val fontScale = rememberSystemValue { Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f) }
    val rotate = rememberSystemValue { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1 }
    SettingsObserverEffect(
        listOf(Settings.System.SCREEN_BRIGHTNESS, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_OFF_TIMEOUT, Settings.System.FONT_SCALE, Settings.System.ACCELEROMETER_ROTATION)
            .map { Settings.System.getUriFor(it) },
    ) { brightness.refresh(); adaptive.refresh(); timeout.refresh(); fontScale.refresh(); rotate.refresh() }
    val hasLightSensor = remember { context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_LIGHT) != null }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var dialog by remember { mutableStateOf<String?>(null) }

    PageScaffold("Display", onBack) {
        Group("Brightness") {
            row { i, n ->
                val shown = if (dragging) dragValue else brightness.value.toFloat()
                SliderRow(
                    title = "Brightness level", icon = Icons.Outlined.BrightnessMedium,
                    value = shown, valueRange = 1f..255f, index = i, count = n,
                    valueLabel = "${((shown - 1) / 254f * 100).roundToInt()}%",
                    onValueChange = { v ->
                        dragging = true; dragValue = v
                        SystemSettings.putSystemInt(context, Settings.System.SCREEN_BRIGHTNESS, v.roundToInt())
                    },
                    onValueChangeFinished = { dragging = false; brightness.refresh() },
                )
            }
            if (hasLightSensor) row { i, n ->
                SwitchRow("Adaptive brightness", adaptive.value, { on ->
                    SystemSettings.putSystemInt(context, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    adaptive.refresh()
                }, i, n, summary = "Adjusts to your surroundings", icon = Icons.Outlined.BrightnessAuto)
            }
        }
        Group("Appearance") {
            row { i, n ->
                val label = when (night.value) { UiModeManager.MODE_NIGHT_YES -> "On"; UiModeManager.MODE_NIGHT_AUTO -> "Automatic (sunset to sunrise)"; else -> "Off" }
                GroupedRow("Dark theme", i, n, summary = label, icon = Icons.Outlined.DarkMode, onClick = { dialog = "dark" })
            }
            row { i, n ->
                val label = FONT_SCALES.minBy { kotlin.math.abs(it.first - fontScale.value) }.second
                GroupedRow("Font size", i, n, summary = label, icon = Icons.Outlined.FormatSize, onClick = { dialog = "font" })
            }
            link(context, "Night Light", "Tint the screen amber at night", Icons.Outlined.ModeNight, Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS))
        }
        Group("Screen") {
            row { i, n -> GroupedRow("Screen timeout", i, n, summary = "After ${timeoutLabel(timeout.value)} of inactivity", icon = Icons.Outlined.Timer, onClick = { dialog = "timeout" }) }
            row { i, n ->
                SwitchRow("Auto-rotate screen", rotate.value, { on ->
                    SystemSettings.putSystemInt(context, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0); rotate.refresh()
                }, i, n, icon = Icons.Outlined.ScreenRotation)
            }
            link(context, "Screen saver", null, Icons.Outlined.Bedtime, Intent(Settings.ACTION_DREAM_SETTINGS))
            link(context, "Lock screen", null, Icons.Outlined.ScreenLockPortrait, Intent("android.settings.LOCK_SCREEN_SETTINGS"))
            link(context, "More display settings", "Display size, colours, refresh rate", Icons.Outlined.Tune, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    when (dialog) {
        "dark" -> ChoiceDialog(
            "Dark theme",
            listOf(UiModeManager.MODE_NIGHT_NO to "Off", UiModeManager.MODE_NIGHT_YES to "On", UiModeManager.MODE_NIGHT_AUTO to "Automatic (sunset to sunrise)"),
            night.value,
            onSelect = { mode ->
                // Requires MODIFY_DAY_NIGHT_MODE (privileged); unprivileged calls are ignored by the platform.
                runCatching { ui.nightMode = mode }
                night.refresh()
                if (night.value != mode) msg(SystemSettings.NEEDS_SYSTEM)
            },
            onDismiss = { dialog = null },
        )
        "timeout" -> ChoiceDialog("Screen timeout", TIMEOUTS.map { it to timeoutLabel(it) }, timeout.value,
            onSelect = { SystemSettings.putSystemInt(context, Settings.System.SCREEN_OFF_TIMEOUT, it); timeout.refresh() },
            onDismiss = { dialog = null })
        "font" -> ChoiceDialog("Font size", FONT_SCALES, FONT_SCALES.minBy { kotlin.math.abs(it.first - fontScale.value) }.first,
            onSelect = { SystemSettings.putSystemFloat(context, Settings.System.FONT_SCALE, it); fontScale.refresh() },
            onDismiss = { dialog = null })
    }
}
