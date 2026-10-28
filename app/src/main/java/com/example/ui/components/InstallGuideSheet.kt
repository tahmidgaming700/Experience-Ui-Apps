package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TechCyan
import com.example.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallGuideSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Fastbootd (PC)", "DSU Sideloader", "TWRP Recovery", "Why APK Can't Flash")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("install_guide_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "GSI Flashing & Installation Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Privileged procedures for applying verified Treble images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close guide")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                when (selectedTab) {
                    0 -> FastbootGuide(context)
                    1 -> DsuGuide()
                    2 -> RecoveryGuide()
                    3 -> TechnicalExplanation()
                }
            }
        }
    }
}

@Composable
private fun FastbootGuide(context: Context) {
    Column {
        Text(
            text = "Method 1: Fastboot / Fastbootd (Recommended)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "For Android 10+ devices with Dynamic Partitions (super), flashing should be performed from fastbootd mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        StepCard(
            stepNumber = "1",
            title = "Reboot into Fastbootd",
            description = "Connect your device to a PC with platform-tools and execute:",
            command = "adb reboot fastboot",
            context = context
        )

        Spacer(modifier = Modifier.height(10.dp))

        StepCard(
            stepNumber = "2",
            title = "Verify Fastbootd Mode",
            description = "Confirm the device is recognized as a userspace fastboot device:",
            command = "fastboot getvar is-userspace",
            context = context
        )

        Spacer(modifier = Modifier.height(10.dp))

        StepCard(
            stepNumber = "3",
            title = "Flash Verified System Image",
            description = "Flash the verified GSI image into the system partition:",
            command = "fastboot flash system system.img",
            context = context
        )

        Spacer(modifier = Modifier.height(10.dp))

        StepCard(
            stepNumber = "4",
            title = "Format Data Partition (First-Time GSI)",
            description = "Wipe user data encryption to prevent bootloop on fresh GSI:",
            command = "fastboot -w",
            context = context
        )

        Spacer(modifier = Modifier.height(10.dp))

        StepCard(
            stepNumber = "5",
            title = "Reboot into System",
            description = "Reboot device into your newly installed GSI:",
            command = "fastboot reboot",
            context = context
        )
    }
}

@Composable
private fun DsuGuide() {
    Column {
        Text(
            text = "Method 2: Android Dynamic System Updates (DSU)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TechCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "DSU allows booting a GSI temporarily or side-by-side without erasing your original system or data. Supported on Android 10+ devices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        InfoCard(
            icon = Icons.Default.PhoneAndroid,
            title = "Using DSU Sideloader App",
            body = "1. Export or share the downloaded system.img to your device storage.\n" +
                    "2. Install the open-source 'DSU Sideloader' application.\n" +
                    "3. Select the verified image and configure User Data allocation (e.g. 10 GB).\n" +
                    "4. Tap 'Install'. The system Dynamic System Update service will partition virtual storage and prompt you to reboot.\n" +
                    "5. To return to your original stock ROM, simply reboot normally."
        )
    }
}

@Composable
private fun RecoveryGuide() {
    Column {
        Text(
            text = "Method 3: Custom Recovery (TWRP / OrangeFox)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WarningOrange
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "If your device possesses a custom recovery environment:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        InfoCard(
            icon = Icons.Default.FolderZip,
            title = "Flashing via TWRP",
            body = "1. Transfer the verified .img file to internal storage or USB OTG.\n" +
                    "2. In TWRP, go to 'Install' and tap 'Install Image' (bottom right).\n" +
                    "3. Select the system.img file.\n" +
                    "4. Choose target partition: 'System Image'. Swipe to confirm flash.\n" +
                    "5. Go to 'Wipe' -> 'Format Data' (type 'yes').\n" +
                    "6. Select 'Reboot System'."
        )
    }
}

@Composable
private fun TechnicalExplanation() {
    Column {
        Text(
            text = "Why an Ordinary APK Cannot Flash a GSI Automatically",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(10.dp))

        InfoCard(
            icon = Icons.Default.Info,
            title = "Android SELinux & Sandbox Isolation",
            body = "• Non-Root UID: Standard Android apps run under unprivileged user IDs (e.g., u0_a123) with restricted capabilities (CAP_DAC_OVERRIDE and CAP_SYS_ADMIN are denied).\n\n" +
                    "• SELinux neverallow rules: The Android security policy strictly forbids untrusted app domains (untrusted_app) from opening raw block devices in /dev/block/bootdevice/by-name/ for read/write operations.\n\n" +
                    "• Dynamic Partition Lock: Android 10+ uses a dynamic 'super' partition containing logical system, vendor, and product volumes. Modifying these partitions requires low-level kernel metadata manipulation which is only accessible inside bootloader, fastbootd, or recovery mode.\n\n" +
                    "• Safety Philosophy: Automatic flashing from an app without proper verification could permanently brick hardware. This app acts as a secure retrieval, validation, and SHA-256 verification tool."
        )
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    command: String,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Step $stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = TechCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Command", command))
                            Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy command",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TechCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
