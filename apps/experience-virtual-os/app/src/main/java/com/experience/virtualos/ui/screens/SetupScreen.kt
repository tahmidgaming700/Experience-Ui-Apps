package com.experience.virtualos.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.ui.AppViewModel
import com.experience.virtualos.ui.RemoteManifestState
import com.experience.virtualos.ui.components.InfoRow
import com.experience.virtualos.ui.components.Notice
import com.experience.virtualos.ui.components.ProgressBlock
import com.experience.virtualos.ui.components.SectionCard
import com.experience.virtualos.ui.components.Tone

private const val STEPS = 5

@Composable
fun SetupScreen(vm: AppViewModel, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val host by vm.host.collectAsState()
    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        StepIndicator(step, Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp))
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 3 * dir } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "setup-step",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (s) {
                    0 -> WelcomeStep()
                    1 -> DeviceStep(vm)
                    2 -> ExpectationsStep(host.isArm64)
                    3 -> RuntimeStep(vm)
                    else -> NotificationStep()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (step < STEPS - 1) step++ else onFinished() },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(if (step == STEPS - 1) "Start using Experience Virtual OS" else if (step == 0) "Get started" else "Next", style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun StepIndicator(step: Int, modifier: Modifier) {
    Row(modifier.fillMaxWidth().semantics { contentDescription = "Step ${step + 1} of $STEPS" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            Box(
                Modifier.weight(if (i == step) 2.2f else 1f).height(6.dp)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun Headline(title: String, body: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(40.dp))
    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(36.dp), modifier = Modifier.size(104.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DeveloperBoard, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(52.dp))
        }
    }
    Headline("Hello.", "Experience Virtual OS runs ARM64 virtual machines inside this app — no root, no bootloader unlock, and nothing on your phone's system is changed.")
    Text("Setup takes about a minute. You can change everything later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DeviceStep(vm: AppViewModel) {
    val host by vm.host.collectAsState()
    Headline("Checking this phone", "These checks only read information. Nothing is modified.")
    SectionCard(eyebrow = "Host", title = "${host.manufacturer} ${host.model}") {
        InfoRow("Android", "${host.androidRelease} (API ${host.sdkInt})")
        InfoRow("Architecture", host.supportedAbis.firstOrNull() ?: "unknown")
        InfoRow("CPU cores", host.cpuCores.toString())
        InfoRow("Memory", "${FileUtils.formatBytes(host.availableMemoryBytes)} free of ${FileUtils.formatBytes(host.totalMemoryBytes)}")
        InfoRow("Storage", "${FileUtils.formatBytes(host.storageFreeBytes)} free")
        InfoRow("Acceleration", host.accelerationLabel)
    }
    if (host.isArm64) Notice("ARM64 host detected. The runtime and PRoot shipped with this app are built for arm64-v8a.", Tone.GOOD)
    else Notice("This device is not ARM64. The runtime cannot run here.", Tone.BAD)
    Notice(host.accelerationDetail, if (host.kvm.name == "ACCESSIBLE") Tone.GOOD else Tone.WARN)
    if (host.storageFreeBytes < 3L * 1024 * 1024 * 1024) Notice("Less than 3 GB is free. The runtime needs about 300 MB; each virtual disk and system image needs several more GB.", Tone.WARN)
}

@Composable
private fun Capability(icon: ImageVector, tone: Tone, title: String, body: String) {
    val color = when (tone) {
        Tone.GOOD -> MaterialTheme.colorScheme.tertiary
        Tone.BAD -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpectationsStep(arm64: Boolean) {
    Headline("What to expect", "Straight answers before you download anything.")
    SectionCard {
        Capability(Icons.Outlined.CheckCircle, Tone.GOOD, "Linux ARM64 virtual machines",
            "Boot ARM64 installer or live ISOs (Alpine, Debian, Ubuntu Server…) with UEFI, a virtual disk, networking and a touch-controlled display. Verified with Alpine Linux; still experimental on phones.")
        Capability(Icons.Outlined.WarningAmber, Tone.WARN, "Android system images — experimental, console only",
            "Google's Android 11 image starts its kernel and system services, but cannot show a screen: its graphics need the Android Emulator's goldfish pipe, which QEMU on a phone cannot provide.")
        Capability(Icons.Outlined.Block, Tone.BAD, "Not a second Android with apps",
            "There is no Google Play, no Android home screen and no app compatibility layer. The app never pretends a guest has booted.")
        Capability(Icons.Outlined.WarningAmber, Tone.WARN, "Speed",
            if (arm64) "Without KVM, every guest instruction is translated in software (TCG). Expect minutes to boot a Linux installer." else "This device cannot run guests.")
    }
}

@Composable
private fun RuntimeStep(vm: AppViewModel) {
    val runtime by vm.runtime.collectAsState()
    val task by vm.runtimeTask.collectAsState()
    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickManifest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { m -> val a = picked; if (a != null && m != null) vm.installRuntimeFromFiles(a, m) }
    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { a -> if (a != null) { picked = a; pickManifest.launch(arrayOf("application/json", "*/*")) } }

    LaunchedEffect(Unit) { if (runtime.remote is RemoteManifestState.Unknown) vm.checkForRuntime() }
    Headline("Install the runtime", "The runtime is a verified Alpine Linux ARM64 environment with QEMU, downloaded separately from GitHub Releases (about 35 MB, 240 MB installed).")
    val t = task
    when {
        runtime.installed != null && runtime.problems.isEmpty() -> Notice("Runtime ${runtime.installed!!.runtimeVersion} is installed and verified.", Tone.GOOD)
        t != null && t.running -> SectionCard { ProgressBlock(t.stage, t.done, t.total) }
        else -> {
            if (t?.error != null) Notice(t.error, Tone.BAD)
            when (val r = runtime.remote) {
                RemoteManifestState.Unknown, RemoteManifestState.Checking -> SectionCard { ProgressBlock("Looking for the latest runtime release…", 0, 0) }
                is RemoteManifestState.Error -> Notice(r.message, Tone.WARN)
                is RemoteManifestState.Available -> SectionCard(eyebrow = "Available", title = "Runtime ${r.manifest.runtimeVersion}") {
                    InfoRow("Download", FileUtils.formatBytes(r.manifest.archive.sizeBytes))
                    InfoRow("Installed size", FileUtils.formatBytes(r.manifest.archive.unpackedSizeBytes))
                    r.manifest.components["qemu"]?.let { InfoRow("QEMU", it) }
                    InfoRow("SHA-256", r.manifest.archive.sha256.take(16) + "…")
                    Button(onClick = vm::installRuntime, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Download and install") }
                }
            }
            OutlinedButton(onClick = { pickArchive.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Install from files (base.tar.gz + manifest.json)")
            }
            Text("You can also skip this and install the runtime later from the Runtime tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotificationStep() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Headline("Stay informed", "A notification shows download progress and keeps a running virtual device alive while you use other apps.")
    if (Build.VERSION.SDK_INT >= 33) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Allow notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
            }
        }
    } else {
        Notice("Notifications are enabled by default on this Android version.", Tone.GOOD)
    }
    Notice("On Huawei/EMUI phones, also allow the app to run in the background (Settings › Battery › App launch › Experience Virtual OS › Manage manually) or long-running guests may be stopped by the system.", Tone.INFO)
}
