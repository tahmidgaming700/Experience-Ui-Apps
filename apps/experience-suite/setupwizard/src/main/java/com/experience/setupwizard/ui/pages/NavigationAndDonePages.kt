package com.experience.setupwizard.ui.pages

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experience.design.components.GroupedRow
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceShape
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.system.NavMode
import com.experience.setupwizard.system.WifiViewModel
import com.experience.setupwizard.ui.PageLayout
import java.util.TimeZone

/** Animated phone illustration: a swipe-up gesture, or three pulsing buttons. */
@Composable
private fun NavIllustration(mode: NavMode, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val screen = MaterialTheme.colorScheme.surfaceContainerHighest
    val t by rememberInfiniteTransition(label = "nav").animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "t")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(screen, cornerRadius = CornerRadius(w * 0.16f))
        drawRoundRect(outline, cornerRadius = CornerRadius(w * 0.16f), style = Stroke(width = 3.dp.toPx()))
        val barY = h * 0.9f
        if (mode == NavMode.GESTURE) {
            drawRoundRect(outline, topLeft = Offset(w * 0.32f, barY), size = Size(w * 0.36f, 4.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
            // A finger dot travelling up from the handle and fading out.
            val y = barY - t * h * 0.45f
            drawCircle(accent.copy(alpha = 1f - t), radius = 9.dp.toPx(), center = Offset(w / 2, y))
        } else {
            val r = 6.dp.toPx()
            listOf(0.25f, 0.5f, 0.75f).forEachIndexed { i, x ->
                val active = (t * 3).toInt() == i
                val c = Offset(w * x, barY)
                when (i) {
                    0 -> drawPath(androidx.compose.ui.graphics.Path().apply {
                        moveTo(c.x + r, c.y - r); lineTo(c.x - r, c.y); lineTo(c.x + r, c.y + r); close()
                    }, if (active) accent else outline)
                    1 -> drawCircle(if (active) accent else outline, radius = r, center = c)
                    else -> drawRect(if (active) accent else outline, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
                }
            }
        }
    }
}

@Composable
fun NavigationPage(vm: SetupViewModel) {
    val s by vm.systemState.collectAsState()
    val wide = currentWidthClass() != WidthClass.COMPACT
    PageLayout(Icons.Outlined.Gesture, "System navigation", "Choose how you go home, go back and switch apps.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            val options = listOf(
                Triple(NavMode.GESTURE, "Gesture navigation", "Swipe up to go home, swipe from an edge to go back."),
                Triple(NavMode.THREE_BUTTON, "3-button navigation", "Back, Home and Overview buttons at the bottom."),
            )
            val content: @Composable (Modifier) -> Unit = { m ->
                options.forEach { (mode, title, body) ->
                    val selected = s.navMode == mode
                    Surface(
                        shape = ExperienceShape.hero,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = m.padding(6.dp).selectable(selected, role = Role.RadioButton) { vm.setNavMode(mode) },
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            NavIllustration(mode, Modifier.size(width = 110.dp, height = 190.dp))
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(Modifier.size(8.dp))
                                Text(title, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.Center) { content(Modifier.weight(1f)) }
            else content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DonePage(vm: SetupViewModel) {
    val s by vm.systemState.collectAsState()
    val accounts by vm.accountsAdded.collectAsState()
    val wifi: WifiViewModel = viewModel()
    val wifiState by wifi.state.collectAsState()
    val locale = vm.chosenLocale()
    val zone = TimeZone.getTimeZone(s.timeZoneId)
    val rows = listOf(
        Triple("Language", locale.getDisplayName(locale), Icons.Outlined.Language),
        Triple("Wi-Fi", wifiState.connectedSsid?.let { "Connected to $it" } ?: "Not connected", Icons.Outlined.Wifi),
        Triple("Time zone", zone.getDisplayName(false, TimeZone.LONG) + if (s.autoTimeZone) " (automatic)" else "", Icons.Outlined.Public),
        Triple("Location", if (s.location) "On" else "Off", Icons.Outlined.LocationOn),
        Triple("Navigation", when (s.navMode) { NavMode.GESTURE -> "Gestures"; NavMode.THREE_BUTTON -> "3 buttons"; null -> "Device default" }, Icons.Outlined.Gesture),
        Triple("Accounts", if (accounts.isEmpty()) "None added" else accounts.joinToString(), Icons.Outlined.AccountCircle),
    )
    PageLayout(Icons.Outlined.CheckCircle, "You're all set", "Here's a summary. Everything can be changed later in Settings.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            rows.forEachIndexed { i, (title, value, icon) -> GroupedRow(title, i, rows.size, summary = value, icon = icon) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("Tap Finish to start using Experience UI.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
