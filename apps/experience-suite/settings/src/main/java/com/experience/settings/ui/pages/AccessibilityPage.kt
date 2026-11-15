package com.experience.settings.ui.pages

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.SwitchRow
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue

// Settings.Secure keys of AOSP accessibility toggles (hidden constants on Android 11).
private const val INVERSION = "accessibility_display_inversion_enabled"
private const val HIGH_CONTRAST_TEXT = "high_text_contrast_enabled"

@Composable
fun AccessibilityPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val cr = context.contentResolver
    val am = context.getSystemService(AccessibilityManager::class.java)
    val services = rememberSystemValue {
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).map { it.resolveInfo.loadLabel(context.packageManager).toString() }
    }
    val animationsOff = rememberSystemValue { Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val inversion = rememberSystemValue { Settings.Secure.getInt(cr, INVERSION, 0) == 1 }
    val contrast = rememberSystemValue { Settings.Secure.getInt(cr, HIGH_CONTRAST_TEXT, 0) == 1 }
    val canWrite = SystemSettings.hasSecureWrite(context)

    PageScaffold("Accessibility", onBack) {
        Group("Services") {
            row { i, n -> InfoRow("Active services", services.value.joinToString().ifEmpty { "None" }, i, n) }
            link(context, "Accessibility settings", "TalkBack, Select to Speak, shortcuts", Icons.Outlined.Accessibility, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (canWrite) {
            Group("Display") {
                row { i, n ->
                    SwitchRow("Remove animations", animationsOff.value, { on ->
                        val v = if (on) 0f else 1f
                        val ok = listOf(Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE)
                            .all { SystemSettings.putGlobalFloat(context, it, v) }
                        if (!ok) msg(SystemSettings.NEEDS_SYSTEM)
                        animationsOff.refresh()
                    }, i, n, icon = Icons.Outlined.Animation)
                }
                row { i, n ->
                    SwitchRow("Colour inversion", inversion.value, { on ->
                        if (!SystemSettings.putSecureInt(context, INVERSION, if (on) 1 else 0)) msg(SystemSettings.NEEDS_SYSTEM)
                        inversion.refresh()
                    }, i, n, summary = "Light screens turn dark and dark screens turn light", icon = Icons.Outlined.InvertColors)
                }
                row { i, n ->
                    SwitchRow("High-contrast text", contrast.value, { on ->
                        if (!SystemSettings.putSecureInt(context, HIGH_CONTRAST_TEXT, if (on) 1 else 0)) msg(SystemSettings.NEEDS_SYSTEM)
                        contrast.refresh()
                    }, i, n, icon = Icons.Outlined.Contrast)
                }
            }
        }
        Group("More") {
            link(context, "Captions", null, Icons.Outlined.ClosedCaption, Intent(Settings.ACTION_CAPTIONING_SETTINGS))
            link(context, "Font and display size", null, Icons.Outlined.TextFields, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }
}
