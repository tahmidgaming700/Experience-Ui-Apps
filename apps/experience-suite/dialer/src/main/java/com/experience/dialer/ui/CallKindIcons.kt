package com.experience.dialer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.experience.dialer.data.CallKind

val CallKind.icon: ImageVector
    get() = when (this) {
        CallKind.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallKind.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallKind.MISSED -> Icons.AutoMirrored.Filled.CallMissed
        CallKind.VOICEMAIL -> Icons.Filled.Voicemail
        CallKind.REJECTED -> Icons.Filled.CallEnd
        CallKind.BLOCKED -> Icons.Filled.Block
        CallKind.ANSWERED_ELSEWHERE -> Icons.Filled.PhoneForwarded
        CallKind.UNKNOWN -> Icons.Filled.Call
    }

/** Tint for the type icon. The type is always also spelled out in text. */
val CallKind.tint: Color
    @Composable get() = when (this) {
        CallKind.MISSED, CallKind.REJECTED, CallKind.BLOCKED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
