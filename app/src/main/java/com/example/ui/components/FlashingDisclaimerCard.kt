package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WarningOrange

@Composable
fun FlashingDisclaimerCard(
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("flashing_disclaimer_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Safety Policy",
                    tint = WarningOrange,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Android Sandbox & GSI Flashing Notice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "• Automatic Flashing Restricted: Standard Android applications execute within an isolated SELinux sandbox. An ordinary APK cannot directly overwrite or flash device partitions (/system, /vendor) without privileged vendor integration, custom recovery, or root access.\n" +
                        "• External Installation Required: Flashing a Generic System Image (GSI) must be conducted externally via Fastboot / Fastbootd from a computer, an unlocked bootloader, a custom recovery (TWRP), or the Android Dynamic System Update (DSU) service.\n" +
                        "• Integrity Guarantee: This updater verifies manifest structure, cryptographic SHA-256 signatures, and architecture compatibility so you can safely export and install the verified image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onOpenGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_installation_guide_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = WarningOrange.copy(alpha = 0.18f),
                    contentColor = WarningOrange
                )
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View GSI Installation Guide (Fastboot & DSU)",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
