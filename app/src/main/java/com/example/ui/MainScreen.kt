package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppNavTab
import com.example.data.model.DownloadState
import com.example.ui.components.DDFlasherConfirmationDialog
import com.example.ui.components.DDFlasherScreen
import com.example.ui.components.DeviceHardwareScreen
import com.example.ui.components.DeviceStatusCard
import com.example.ui.components.DownloadSection
import com.example.ui.components.FlashingDisclaimerCard
import com.example.ui.components.InstallGuideSheet
import com.example.ui.components.ManifestInspectorDialog
import com.example.ui.components.UpdateInfoCard
import com.example.ui.components.UrlConfigDialog
import com.example.ui.theme.TechCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: UpdaterViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Experience UI Updater",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(TechCyan)
                            )
                        }
                        Text(
                            text = when (uiState.currentTab) {
                                AppNavTab.OTA_UPDATER -> "${uiState.selectedChannel.replace('_', ' ').uppercase()} • ${uiState.selectedPreset.title}"
                                AppNavTab.DD_FLASHER -> "In-App DD Flasher • Partition Block Writer"
                                AppNavTab.DEVICE_PARTITIONS -> "Device Environment & Recovery Guides"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (uiState.currentTab) {
                                AppNavTab.OTA_UPDATER -> Icons.Default.SystemUpdate
                                AppNavTab.DD_FLASHER -> Icons.Default.FlashOn
                                AppNavTab.DEVICE_PARTITIONS -> Icons.Default.PhoneAndroid
                            },
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showGuideSheet(true) },
                        modifier = Modifier.testTag("open_guide_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Installation Guide"
                        )
                    }

                    if (uiState.currentTab == AppNavTab.OTA_UPDATER) {
                        IconButton(
                            onClick = { viewModel.showRawJsonDialog(true) },
                            modifier = Modifier.testTag("open_inspector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Manifest Inspector"
                            )
                        }

                        IconButton(
                            onClick = { viewModel.showUrlDialog(true) },
                            modifier = Modifier.testTag("open_url_config_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configure Manifest Source"
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (uiState.currentTab == AppNavTab.DD_FLASHER) {
                                viewModel.refreshDdFlasherState()
                            } else {
                                viewModel.refreshManifest()
                            }
                        },
                        enabled = !uiState.isChecking,
                        modifier = Modifier.testTag("check_updates_button")
                    ) {
                        if (uiState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == AppNavTab.OTA_UPDATER,
                    onClick = { viewModel.selectTab(AppNavTab.OTA_UPDATER) },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "OTA Updates") },
                    label = { Text("OTA Updates") },
                    modifier = Modifier.testTag("nav_ota_updates")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppNavTab.DD_FLASHER,
                    onClick = { viewModel.selectTab(AppNavTab.DD_FLASHER) },
                    icon = { Icon(Icons.Default.FlashOn, contentDescription = "DD Flasher") },
                    label = { Text("DD Flasher") },
                    modifier = Modifier.testTag("nav_dd_flasher")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppNavTab.DEVICE_PARTITIONS,
                    onClick = { viewModel.selectTab(AppNavTab.DEVICE_PARTITIONS) },
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "Hardware") },
                    label = { Text("Hardware") },
                    modifier = Modifier.testTag("nav_hardware_safety")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                AppNavTab.OTA_UPDATER -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            FlashingDisclaimerCard(
                                onOpenGuide = { viewModel.showGuideSheet(true) }
                            )
                        }

                        item {
                            UpdateInfoCard(
                                manifest = uiState.manifest,
                                validation = uiState.manifestValidation,
                                compatibility = uiState.compatibility,
                                onViewRawManifest = { viewModel.showRawJsonDialog(true) }
                            )
                        }

                        item {
                            DownloadSection(
                                manifest = uiState.manifest,
                                compatibility = uiState.compatibility,
                                downloadState = uiState.downloadState,
                                onStartDownload = { viewModel.startDownload() },
                                onPauseDownload = { viewModel.pauseDownload() },
                                onCancelDownload = { viewModel.cancelDownload() },
                                onDeleteFile = { viewModel.deleteDownloadedFile() },
                                onRecomputeHash = { viewModel.recomputeHash() },
                                onShareFile = { viewModel.shareOrExportFile(context) },
                                onOpenDDFlasher = { viewModel.navigateToDdFlasherWithVerifiedImage() }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                AppNavTab.DD_FLASHER -> {
                    DDFlasherScreen(
                        availablePartitions = uiState.availablePartitions,
                        selectedPartition = uiState.selectedDdPartition,
                        onSelectPartition = { part -> viewModel.selectDdPartition(part) },
                        selectedImage = uiState.selectedDdImage,
                        onRefreshImages = { viewModel.refreshDdFlasherState() },
                        blockSize = uiState.ddBlockSize,
                        onSelectBlockSize = { bs -> viewModel.selectDdBlockSize(bs) },
                        hasRootAccess = uiState.hasRootAccess,
                        onRefreshPrivileges = { viewModel.refreshDdFlasherState() },
                        flasherStatus = uiState.ddFlasherStatus,
                        consoleLogs = uiState.consoleLogs,
                        onExecuteFlash = { viewModel.executeInAppDdFlash() }
                    )
                }

                AppNavTab.DEVICE_PARTITIONS -> {
                    DeviceHardwareScreen(
                        deviceInfo = uiState.deviceInfo,
                        onRefreshTelemetry = { viewModel.refreshDeviceState() },
                        onOpenGuide = { viewModel.showGuideSheet(true) }
                    )
                }
            }
        }
    }

    // Installation Guide Bottom Sheet
    if (uiState.showGuideSheet) {
        InstallGuideSheet(
            onDismiss = { viewModel.showGuideSheet(false) }
        )
    }

    // Manifest Inspector Dialog
    if (uiState.showRawJsonDialog) {
        ManifestInspectorDialog(
            manifest = uiState.manifest,
            validation = uiState.manifestValidation,
            onDismiss = { viewModel.showRawJsonDialog(false) }
        )
    }

    // Channel & URL Configuration Dialog
    if (uiState.showUrlDialog) {
        UrlConfigDialog(
            currentPreset = uiState.selectedPreset,
            currentChannel = uiState.selectedChannel,
            currentUrl = uiState.customUrl,
            onSelectPreset = { preset -> viewModel.selectPreset(preset) },
            onSelectChannel = { channel -> viewModel.selectChannel(channel) },
            onSaveCustomUrl = { url -> viewModel.saveCustomUrl(url) },
            onDismiss = { viewModel.showUrlDialog(false) }
        )
    }
}
