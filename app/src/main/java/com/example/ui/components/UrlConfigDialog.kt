package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.config.ExperienceOtaConfig
import com.example.data.network.ManifestRepository
import com.example.data.network.PresetManifestType
import com.example.ui.theme.TechCyan

@Composable
fun UrlConfigDialog(
    currentPreset: PresetManifestType,
    currentChannel: String,
    currentUrl: String,
    onSelectPreset: (PresetManifestType) -> Unit,
    onSelectChannel: (String) -> Unit,
    onSaveCustomUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf(currentPreset) }
    var selectedChannel by remember { mutableStateOf(currentChannel) }
    var inputUrl by remember { mutableStateOf(currentUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("url_config_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TechCyan
                )
                Text(
                    text = "OTA Manifest & Release Channel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Channel Selection Tab
                Text(
                    text = "Release Channel:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                TabRow(
                    selectedTabIndex = if (selectedChannel == "early_access") 1 else 0,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Tab(
                        selected = selectedChannel == "stable",
                        onClick = {
                            selectedChannel = "stable"
                            onSelectChannel("stable")
                        },
                        text = { Text("Stable Channel") }
                    )
                    Tab(
                        selected = selectedChannel == "early_access",
                        onClick = {
                            selectedChannel = "early_access"
                            onSelectChannel("early_access")
                        },
                        text = { Text("Early Access") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Manifest Source (Single Source of Truth):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                PresetManifestType.values().forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedPreset = preset }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedPreset = preset }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (selectedPreset == PresetManifestType.CUSTOM_URL) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Configured HTTPS URL:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = {
                            inputUrl = it
                            urlError = when {
                                it.isBlank() -> "URL cannot be empty"
                                !it.trim().startsWith("https://", ignoreCase = true) ->
                                    "URL must strictly start with https://"
                                else -> null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_manifest_url_input"),
                        label = { Text("Manifest URL (HTTPS)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null)
                        },
                        isError = urlError != null,
                        supportingText = {
                            if (urlError != null) {
                                Text(text = urlError ?: "", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Must return Experience UI JSON schema.")
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            inputUrl = ExperienceOtaConfig.OFFICIAL_MANIFEST_URL
                            urlError = null
                        }
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset to Official GitHub URL")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPreset == PresetManifestType.CUSTOM_URL) {
                        val clean = inputUrl.trim()
                        if (!clean.startsWith("https://", ignoreCase = true)) {
                            urlError = "Strict HTTPS protocol is required"
                            return@Button
                        }
                        onSaveCustomUrl(clean)
                    } else {
                        onSelectPreset(selectedPreset)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_manifest_config_button")
            ) {
                Text("Apply & Check")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
