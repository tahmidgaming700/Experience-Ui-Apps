package com.experience.settings.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.HeroCard
import com.experience.design.components.SectionHeader
import com.experience.design.system.SystemInfo
import com.experience.design.theme.ExperienceShape
import com.experience.settings.Page
import com.experience.settings.SettingsSearch

val Page.icon: ImageVector
    get() = when (this) {
        Page.NETWORK -> Icons.Outlined.Wifi
        Page.CONNECTED -> Icons.Outlined.Bluetooth
        Page.DISPLAY -> Icons.Outlined.BrightnessMedium
        Page.STYLE -> Icons.Outlined.Palette
        Page.SOUND -> Icons.AutoMirrored.Outlined.VolumeUp
        Page.NOTIFICATIONS -> Icons.Outlined.Notifications
        Page.APPS -> Icons.Outlined.Apps
        Page.BATTERY -> Icons.Outlined.BatteryFull
        Page.STORAGE -> Icons.Outlined.Storage
        Page.SECURITY -> Icons.Outlined.Security
        Page.ACCESSIBILITY -> Icons.Outlined.Accessibility
        Page.LOCATION -> Icons.Outlined.LocationOn
        Page.SYSTEM -> Icons.Outlined.SettingsSuggest
        Page.UPDATES -> Icons.Outlined.SystemUpdate
        Page.ABOUT -> Icons.Outlined.Info
    }

private val sections = listOf(
    "Connectivity" to listOf(Page.NETWORK, Page.CONNECTED),
    "Personalise" to listOf(Page.STYLE, Page.DISPLAY, Page.SOUND, Page.NOTIFICATIONS),
    "Device" to listOf(Page.APPS, Page.BATTERY, Page.STORAGE),
    "Privacy & safety" to listOf(Page.SECURITY, Page.LOCATION, Page.ACCESSIBILITY),
    "System" to listOf(Page.SYSTEM, Page.UPDATES, Page.ABOUT),
)

/** Searchable hub listing every section; [selected] highlights the open page on tablets. */
@Composable
fun Hub(query: String, onQuery: (String) -> Unit, selected: Page?, onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    ExperienceScaffold(title = "Settings") { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear search") } }) else null,
                singleLine = true,
                shape = ExperienceShape.pill,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            if (query.isNotBlank()) {
                val results = SettingsSearch.search(query)
                if (results.isEmpty()) EmptyState(Icons.Outlined.SearchOff, "No results", "Try another word, such as “brightness” or “Bluetooth”.")
                results.forEachIndexed { i, r ->
                    GroupedRow(title = r.title, summary = r.page.title, icon = r.page.icon, index = i, count = results.size, onClick = { onOpen(r.page) })
                }
                return@Column
            }
            HeroCard(
                title = SystemInfo.experienceDisplayVersion,
                body = if (SystemInfo.isExperienceUi) "Build ${SystemInfo.experienceBuildNumber} · Android ${SystemInfo.androidVersion}" else "Android ${SystemInfo.androidVersion} · tap for device details",
                icon = Icons.Outlined.Info,
                onClick = { onOpen(Page.ABOUT) },
                modifier = Modifier.padding(top = 8.dp),
            )
            sections.forEach { (header, pages) ->
                SectionHeader(header)
                pages.forEachIndexed { i, p ->
                    GroupedRow(
                        title = p.title, summary = p.summary, icon = p.icon, index = i, count = pages.size,
                        onClick = { onOpen(p) },
                        modifier = Modifier.semantics { this.selected = p == selected },
                    )
                }
            }
            val aosp = Intent(Settings.ACTION_SETTINGS).setPackage("com.android.settings")
            if (context.canStart(aosp)) {
                SectionHeader("More")
                LinkRow("All Android settings", "Advanced pages in the system Settings app", Icons.Outlined.Tune, aosp, 0, 1)
            }
            Text(
                "Long-press a value on About to copy it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
