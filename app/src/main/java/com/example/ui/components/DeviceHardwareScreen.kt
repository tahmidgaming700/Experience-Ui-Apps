package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.DeviceInfo

@Composable
fun DeviceHardwareScreen(
    deviceInfo: DeviceInfo,
    onRefreshTelemetry: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FlashingDisclaimerCard(onOpenGuide = onOpenGuide)
        }

        item {
            DeviceStatusCard(
                deviceInfo = deviceInfo,
                onRefresh = onRefreshTelemetry
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
