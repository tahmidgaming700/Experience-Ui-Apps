package com.experience.virtualos.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.experience.virtualos.BuildConfig
import com.experience.virtualos.core.ThemeMode
import com.experience.virtualos.core.log.LogLevel
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.components.InfoRow
import com.experience.virtualos.ui.components.Notice
import com.experience.virtualos.ui.components.SectionCard
import com.experience.virtualos.ui.components.Tone
import com.experience.virtualos.ui.theme.CodeStyle
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues, restartSetup: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val host by vm.host.collectAsState()
    val activity by vm.activity.collectAsState()
    val context = LocalContext.current
    var manifestUrl by remember(settings.runtimeManifestUrl) { mutableStateOf(settings.runtimeManifestUrl) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) }

        item {
            SectionCard(eyebrow = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = settings.themeMode == m, onClick = { vm.updateSettings { it.copy(themeMode = m) } },
                            label = { Text(when (m) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }) })
                    }
                }
                ToggleRow("Wallpaper colours", if (Build.VERSION.SDK_INT >= 31) "Use Material You dynamic colour" else "Requires Android 12 or newer", settings.dynamicColor, Build.VERSION.SDK_INT >= 31) {
                    v -> vm.updateSettings { it.copy(dynamicColor = v) }
                }
                ToggleRow("Keep screen on in guest display", "Prevents the phone from sleeping while the display is open", settings.keepScreenOnInDisplay) {
                    v -> vm.updateSettings { it.copy(keepScreenOnInDisplay = v) }
                }
            }
        }

        item {
            SectionCard(eyebrow = "Emulation") {
                Text("TCG threads", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 4, 8).forEach { n ->
                        FilterChip(selected = settings.tcgThreads == n, onClick = { vm.updateSettings { it.copy(tcgThreads = n) } }, label = { Text(if (n == 0) "Auto (${host.cpuCores})" else "$n") })
                    }
                }
                Text("One thread uses less power and heat; multi-threaded TCG lets several guest CPUs run in parallel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow("PRoot compatibility mode", "Disables PRoot's seccomp acceleration (PROOT_NO_SECCOMP). Try this if the runtime self-test fails on your kernel.", settings.disableProotSeccomp) {
                    v -> vm.updateSettings { it.copy(disableProotSeccomp = v) }
                }
            }
        }

        item {
            SectionCard(eyebrow = "Runtime source") {
                OutlinedTextField(manifestUrl, { manifestUrl = it }, label = { Text("Manifest URL (blank = official)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Official: ${BuildConfig.RUNTIME_MANIFEST_URL}", style = CodeStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.updateSettings { it.copy(runtimeManifestUrl = manifestUrl.trim()) }; vm.checkForRuntime() }) { Text("Save") }
                    TextButton(onClick = { manifestUrl = ""; vm.updateSettings { it.copy(runtimeManifestUrl = "") } }) { Text("Reset") }
                }
                Text("Only HTTPS GitHub release assets are accepted, and every archive is SHA-256 verified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard(eyebrow = "Diagnostics") {
                InfoRow("App", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Host", "${host.manufacturer} ${host.model}")
                InfoRow("Android", "${host.androidRelease} · API ${host.sdkInt}")
                InfoRow("SoC", host.socHint ?: "unknown")
                InfoRow("ABIs", host.supportedAbis.joinToString())
                InfoRow("KVM", host.kvm.label)
                Notice(host.accelerationDetail, Tone.INFO)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Experience Virtual OS diagnostics").putExtra(Intent.EXTRA_TEXT, vm.diagnosticsReport())
                        context.startActivity(Intent.createChooser(send, "Share diagnostics"))
                    }) { Text("Share report") }
                    TextButton(onClick = { vm.refreshAll() }) { Text("Re-check") }
                }
            }
        }

        item {
            SectionCard(eyebrow = "Activity log") {
                if (activity.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activity.take(40).forEach { e ->
                            Text(
                                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(e.timeMillis))}  ${e.category}  ${e.message}",
                                style = CodeStyle,
                                color = when (e.level) { LogLevel.ERROR -> MaterialTheme.colorScheme.error; LogLevel.WARNING -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.onSurface },
                            )
                        }
                    }
                }
                if (activity.isNotEmpty()) TextButton(onClick = vm::clearActivity) { Text("Clear log") }
            }
        }

        item {
            SectionCard(eyebrow = "About") {
                Text("Experience Virtual OS runs QEMU (GPL-2.0) inside an Alpine Linux userspace under PRoot (GPL-2.0, Termux build). Android system images are downloaded from Google under the Android SDK licence. Source and build scripts: github.com/tahmidgaming700/Experience-Ui-Apps.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = restartSetup) { Text("Run setup again") }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
