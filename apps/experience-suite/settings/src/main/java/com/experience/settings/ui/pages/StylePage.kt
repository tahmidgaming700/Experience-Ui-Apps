package com.experience.settings.ui.pages

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceColors
import com.experience.design.theme.ExperienceShape
import com.experience.design.theme.ExperienceShapes
import com.experience.design.theme.ExperienceTypography
import com.experience.settings.system.ColorMath
import com.experience.settings.system.OverlayEntry
import com.experience.settings.system.Overlays
import com.experience.settings.system.ThemeController
import com.experience.settings.ui.Group
import com.experience.settings.ui.LocalMessenger
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.canStart
import com.experience.settings.ui.startSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SeedChoice {
    data object System : SeedChoice
    data object Wallpaper : SeedChoice
    data class Fixed(val argb: Int) : SeedChoice
}

private data class OverlayState(
    val accents: List<Pair<OverlayEntry, Int>> = emptyList(),
    val shapes: List<Pair<OverlayEntry, String?>> = emptyList(),
    val fonts: List<OverlayEntry> = emptyList(),
    val defaultMask: String? = null,
    val available: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StylePage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val msg = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val theme = remember { ThemeController(context) }
    val dark = isSystemInDarkTheme()

    var choice by remember {
        mutableStateOf(
            when {
                theme.fromWallpaper() -> SeedChoice.Wallpaper
                theme.storedSeed() == null -> SeedChoice.System
                else -> SeedChoice.Fixed(theme.storedSeed()!! or 0xFF000000.toInt())
            },
        )
    }
    var style by remember { mutableStateOf(theme.style()) }
    val wallpaperSeed = remember { theme.wallpaperSeed() }
    var overlays by remember { mutableStateOf(OverlayState()) }
    val canWrite = remember { theme.canWrite() }

    suspend fun loadOverlays() {
        overlays = withContext(Dispatchers.IO) {
            val o = theme.overlays
            OverlayState(
                accents = theme.accentOverlays(),
                shapes = o.list(Overlays.CATEGORY_ICON_SHAPE).map { it to o.iconMask(it.packageName) },
                fonts = o.list(Overlays.CATEGORY_FONT),
                defaultMask = o.systemIconMask(),
                available = o.available && o.list().isNotEmpty(),
            )
        }
    }
    LaunchedEffect(Unit) { loadOverlays() }

    val previewSeed = when (val c = choice) {
        SeedChoice.Wallpaper -> wallpaperSeed ?: theme.effectiveSeed()
        SeedChoice.System -> com.experience.design.theme.ExperienceThemeSettings.systemAccent(context) ?: ExperienceColors.BRAND_SEED
        is SeedChoice.Fixed -> c.argb
    }

    fun applyChoice(c: SeedChoice) {
        choice = c
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                when (c) {
                    SeedChoice.System -> if (theme.clearSeed()) "Apps follow the system accent" else null
                    SeedChoice.Wallpaper -> wallpaperSeed?.let { seed ->
                        if (theme.applySeed(seed, fromWallpaper = true)) accentMessage(theme, seed) else null
                    } ?: if (wallpaperSeed == null) "This wallpaper doesn't report colours" else null
                    is SeedChoice.Fixed -> if (theme.applySeed(c.argb, fromWallpaper = false)) accentMessage(theme, c.argb) else null
                }
            }
            msg(result ?: SystemSettings.NEEDS_SYSTEM)
            loadOverlays()
        }
    }

    PageScaffold("Style & wallpaper", onBack) {
        PalettePreview(previewSeed, style, dark)

        SectionHeader("Colour")
        Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (wallpaperSeed != null) {
                        Swatch(wallpaperSeed, "Match wallpaper", choice == SeedChoice.Wallpaper, badge = true) { applyChoice(SeedChoice.Wallpaper) }
                    }
                    ThemeController.PALETTE.forEach { s ->
                        Swatch(s.argb, s.name, (choice as? SeedChoice.Fixed)?.argb == s.argb) { applyChoice(SeedChoice.Fixed(s.argb)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilterChip(
                    selected = choice == SeedChoice.System,
                    onClick = { applyChoice(SeedChoice.System) },
                    label = { Text("Use system accent colour") },
                )
                Text(
                    when (choice) {
                        SeedChoice.Wallpaper -> "Colours follow your wallpaper and update when it changes."
                        SeedChoice.System -> "Apps use the accent of the active system theme overlay."
                        is SeedChoice.Fixed -> "Seed ${ColorMath.hex(previewSeed)}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionHeader("Palette style")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ExperienceColors.Style.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = style == s,
                    onClick = {
                        style = s
                        scope.launch { if (!withContext(Dispatchers.IO) { theme.applyStyle(s) }) msg(SystemSettings.NEEDS_SYSTEM) }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, ExperienceColors.Style.entries.size),
                    label = { Text(s.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        if (overlays.available) {
            if (overlays.accents.isNotEmpty()) {
                SectionHeader("System UI accent")
                Text(
                    "Choosing a colour above enables the nearest installed accent overlay, so quick settings and system dialogs match. You can also pick one directly.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.surfaceContainer) {
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        overlays.accents.forEach { (entry, color) ->
                            Swatch(color, entry.label, entry.enabled) {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { theme.overlays.enableExclusive(entry.packageName) }
                                    msg(if (ok) "System accent: ${entry.label}" else SystemSettings.NEEDS_SYSTEM)
                                    loadOverlays()
                                }
                            }
                        }
                    }
                }
            }
            if (overlays.shapes.isNotEmpty()) {
                SectionHeader("Icon shape")
                Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.surfaceContainer) {
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShapeOption("Default", overlays.defaultMask, overlays.shapes.none { it.first.enabled }) {
                            scope.launch { withContext(Dispatchers.IO) { theme.overlays.disableCategory(Overlays.CATEGORY_ICON_SHAPE) }; loadOverlays() }
                        }
                        overlays.shapes.forEach { (entry, mask) ->
                            ShapeOption(entry.label, mask, entry.enabled) {
                                scope.launch {
                                    if (!withContext(Dispatchers.IO) { theme.overlays.enableExclusive(entry.packageName) }) msg(SystemSettings.NEEDS_SYSTEM)
                                    loadOverlays()
                                }
                            }
                        }
                    }
                }
            }
            if (overlays.fonts.isNotEmpty()) {
                Group("Font") {
                    row { i, n ->
                        GroupedRow("Default (Roboto)", i, n, onClick = {
                            scope.launch { withContext(Dispatchers.IO) { theme.overlays.disableCategory(Overlays.CATEGORY_FONT) }; loadOverlays() }
                        }, trailing = { if (overlays.fonts.none { it.enabled }) Icon(Icons.Filled.Check, "Selected") })
                    }
                    overlays.fonts.forEach { f ->
                        row { i, n ->
                            GroupedRow(f.label, i, n, summary = f.packageName, onClick = {
                                scope.launch {
                                    if (!withContext(Dispatchers.IO) { theme.overlays.enableExclusive(f.packageName) }) msg(SystemSettings.NEEDS_SYSTEM)
                                    loadOverlays()
                                }
                            }, trailing = { if (f.enabled) Icon(Icons.Filled.Check, "Selected") })
                        }
                    }
                }
            }
        } else if (!canWrite) {
            Text(
                "System-wide accent, icon shape and font overlays are applied when Experience Settings runs as part of Experience UI.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        val wallpaper = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
        if (context.canStart(Intent(Intent.ACTION_SET_WALLPAPER))) {
            Group("Wallpaper") {
                row { i, n -> GroupedRow("Wallpaper", i, n, summary = "Change home and lock screen wallpaper", icon = Icons.Outlined.Wallpaper, onClick = { context.startSafely(wallpaper) }) }
            }
        }
    }
}

private fun accentMessage(theme: ThemeController, seed: Int): String {
    val pkg = theme.syncAccentOverlay(seed) ?: return "Theme colour updated"
    val label = theme.accentOverlays().firstOrNull { it.first.packageName == pkg }?.first?.label ?: pkg
    return "Theme colour updated · system accent: $label"
}

@Composable
private fun Swatch(argb: Int, label: String, selected: Boolean, badge: Boolean = false, onClick: () -> Unit) {
    val color = Color(argb)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Surface(
            shape = CircleShape,
            color = color,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            modifier = Modifier
                .size(52.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                val onColor = if (ExperienceColors.contrastRatio(argb, 0xFFFFFFFF.toInt()) > 3) Color.White else Color.Black
                when {
                    selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = onColor)
                    badge -> Icon(Icons.Outlined.Wallpaper, contentDescription = null, tint = onColor)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ShapeOption(label: String, mask: String?, selected: Boolean, onClick: () -> Unit) {
    val path = remember(mask) { mask?.let { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() } }
    val fill = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).semantics { contentDescription = label + if (selected) ", selected" else "" },
    ) {
        Canvas(Modifier.size(52.dp)) {
            val p = path
            if (p != null) {
                // Icon masks are defined in a 100 x 100 viewport.
                scale(size.width / 100f, size.height / 100f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawIntoCanvas { c -> c.nativeCanvas.drawPath(p, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((fill.alpha * 255).toInt(), (fill.red * 255).toInt(), (fill.green * 255).toInt(), (fill.blue * 255).toInt()) }) }
                }
            } else {
                drawCircle(fill)
            }
        }
        Text(label + if (selected) " ✓" else "", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Swatches and sample components rendered with the palette that would be applied. */
@Composable
private fun PalettePreview(seed: Int, style: ExperienceColors.Style, dark: Boolean) {
    val scheme = remember(seed, style, dark) { ExperienceColors.scheme(seed, dark, style) }
    MaterialTheme(colorScheme = scheme, typography = ExperienceTypography, shapes = ExperienceShapes) {
        Surface(shape = ExperienceShape.hero, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Preview", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        scheme.primary to "Primary", scheme.secondary to "Secondary", scheme.tertiary to "Tertiary",
                        scheme.primaryContainer to "Primary container", scheme.surfaceContainerHighest to "Surface",
                    ).forEach { (c, name) ->
                        Surface(shape = MaterialTheme.shapes.medium, color = c, modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "$name swatch" }) {}
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Button") }
                    FilledTonalButton(onClick = {}) { Text("Tonal") }
                    OutlinedButton(onClick = {}) { Text("Outline") }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = true, onCheckedChange = null, modifier = Modifier.semantics { contentDescription = "Sample switch" })
                    Spacer(Modifier.width(16.dp))
                    LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    FloatingActionButton(onClick = {}, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Add, contentDescription = "Sample button") }
                }
            }
        }
    }
}
