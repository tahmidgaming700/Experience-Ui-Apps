package com.experience.setupwizard.ui.pages

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.SettingsAccessibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import com.experience.design.theme.ExperienceShape
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.model.DisplaySize
import com.experience.setupwizard.ui.PageLayout
import com.experience.setupwizard.ui.tryStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepSelector(steps: List<DisplaySize.Step>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, step ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, steps.size),
            ) { Text(step.label, maxLines = 1) }
        }
    }
}

@Composable
fun AccessibilityPage(vm: SetupViewModel) {
    val context = LocalContext.current
    val s by vm.systemState.collectAsState()
    PageLayout(Icons.Outlined.Accessibility, "Make it comfortable", "Adjust text and display size and contrast. You can change these later in Settings.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SectionHeader("Font size")
            Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "The quick brown fox jumps over the lazy dog.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(20.dp),
                )
            }
            Column(Modifier.padding(top = 8.dp)) {
                StepSelector(DisplaySize.fontSteps, DisplaySize.closestIndex(DisplaySize.fontSteps, s.fontScale), true) {
                    vm.setFontScale(DisplaySize.fontSteps[it].scale)
                }
            }
            SectionHeader("Display size")
            val base = s.defaultDensity
            val densityIndex = if (base != null) DisplaySize.closestIndex(DisplaySize.densitySteps, s.density.toFloat() / base) else 1
            StepSelector(DisplaySize.densitySteps, densityIndex, base != null) { vm.setDensityStep(it) }
            if (base == null) {
                Text(
                    "Display size can't be changed on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            SectionHeader("Vision")
            SwitchRow("Colour inversion", s.inversion, vm::setInversion, 0, 2, summary = "Swap light and dark colours everywhere", icon = Icons.Outlined.InvertColors)
            SwitchRow("High-contrast text", s.highContrast, vm::setHighContrast, 1, 2, summary = "Outline text for better legibility", icon = Icons.Outlined.Contrast)
            SectionHeader("More")
            GroupedRow(
                "Accessibility settings", 0, 1,
                summary = "Screen reader, magnification, captions and more",
                icon = Icons.Outlined.SettingsAccessibility,
                onClick = { context.tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
        }
    }
}
