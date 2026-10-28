package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.device.DeviceInspector
import com.example.data.download.DownloadManager
import com.example.data.flasher.DDFlasher
import com.example.data.model.AppNavTab
import com.example.data.model.DDFlasherStatus
import com.example.data.model.DeviceCompatibility
import com.example.data.model.DeviceInfo
import com.example.data.model.DownloadState
import com.example.data.model.ManifestValidation
import com.example.data.model.PartitionInfo
import com.example.data.model.UpdateManifest
import com.example.data.network.ManifestRepository
import com.example.data.network.PresetManifestType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdaterUiState(
    val currentTab: AppNavTab = AppNavTab.OTA_UPDATER,
    val deviceInfo: DeviceInfo,
    val selectedPreset: PresetManifestType,
    val selectedChannel: String = "stable",
    val customUrl: String,
    val isChecking: Boolean = false,
    val manifest: UpdateManifest? = null,
    val manifestValidation: ManifestValidation? = null,
    val compatibility: DeviceCompatibility? = null,
    val downloadState: DownloadState = DownloadState.Idle,

    // DD Flasher In-App State
    val availablePartitions: List<PartitionInfo> = emptyList(),
    val selectedDdPartition: String = "/dev/block/by-name/system",
    val selectedDdImage: File? = null,
    val ddBlockSize: String = "4M",
    val hasRootAccess: Boolean = false,
    val ddFlasherStatus: DDFlasherStatus = DDFlasherStatus.Idle,
    val consoleLogs: List<String> = emptyList(),

    val showGuideSheet: Boolean = false,
    val showUrlDialog: Boolean = false,
    val showRawJsonDialog: Boolean = false,
    val showDDFlasherDialog: Boolean = false,
    val statusMessage: String? = null
)

class UpdaterViewModel(application: Application) : AndroidViewModel(application) {

    private val manifestRepository = ManifestRepository(application)
    val downloadManager = DownloadManager(application)

    private val _uiState = MutableStateFlow(
        UpdaterUiState(
            deviceInfo = DeviceInspector.inspectDevice(application),
            selectedPreset = manifestRepository.getSelectedPreset(),
            selectedChannel = manifestRepository.getSelectedChannel(),
            customUrl = manifestRepository.getConfiguredUrl(),
            availablePartitions = DDFlasher.scanAvailablePartitions(),
            selectedDdPartition = DDFlasher.detectTargetPartition(),
            hasRootAccess = DDFlasher.checkRootAccess()
        )
    )
    val uiState: StateFlow<UpdaterUiState> = _uiState.asStateFlow()

    init {
        // Collect download states
        viewModelScope.launch {
            downloadManager.downloadState.collect { state ->
                val verifiedImage = (state as? DownloadState.Verified)?.file
                    ?: downloadManager.getVerifiedGsiImage()

                _uiState.update { current ->
                    current.copy(
                        downloadState = state,
                        selectedDdImage = verifiedImage ?: current.selectedDdImage
                    )
                }
            }
        }

        // Initial check
        refreshManifest()
        refreshDdFlasherState()
    }

    fun selectTab(tab: AppNavTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == AppNavTab.DD_FLASHER) {
            refreshDdFlasherState()
        }
    }

    fun refreshDeviceState() {
        _uiState.update {
            it.copy(deviceInfo = DeviceInspector.inspectDevice(getApplication()))
        }
    }

    fun refreshDdFlasherState() {
        viewModelScope.launch {
            val partitions = DDFlasher.scanAvailablePartitions()
            val hasRoot = DDFlasher.checkRootAccess()
            val verifiedImage = downloadManager.getVerifiedGsiImage()
                ?: (_uiState.value.downloadState as? DownloadState.Verified)?.file

            _uiState.update { current ->
                current.copy(
                    availablePartitions = partitions,
                    hasRootAccess = hasRoot,
                    selectedDdImage = verifiedImage ?: current.selectedDdImage
                )
            }
        }
    }

    fun selectDdPartition(partition: String) {
        _uiState.update { it.copy(selectedDdPartition = partition) }
    }

    fun selectDdBlockSize(blockSize: String) {
        _uiState.update { it.copy(ddBlockSize = blockSize) }
    }

    fun executeInAppDdFlash() {
        val image = _uiState.value.selectedDdImage
            ?: downloadManager.getVerifiedGsiImage()
            ?: return

        val partition = _uiState.value.selectedDdPartition
        val blockSize = _uiState.value.ddBlockSize

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    ddFlasherStatus = DDFlasherStatus.Flashing(0, 0L, "Starting...", "Initializing DD write..."),
                    consoleLogs = listOf("[START] Initiating in-app DD Flasher write...")
                )
            }

            val result = DDFlasher.executeDdFlash(
                imageFile = image,
                targetPartition = partition,
                blockSize = blockSize,
                onLog = { logLine ->
                    _uiState.update { current ->
                        current.copy(consoleLogs = current.consoleLogs + logLine)
                    }
                },
                onProgress = { percent, written, speed ->
                    _uiState.update { current ->
                        current.copy(
                            ddFlasherStatus = DDFlasherStatus.Flashing(percent, written, speed, "$speed - $percent%")
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    ddFlasherStatus = result,
                    statusMessage = if (result is DDFlasherStatus.Success) "DD Flashing completed successfully!" else "DD Flashing failed."
                )
            }
        }
    }

    fun navigateToDdFlasherWithVerifiedImage() {
        val verifiedFile = downloadManager.getVerifiedGsiImage()
            ?: (_uiState.value.downloadState as? DownloadState.Verified)?.file

        _uiState.update {
            it.copy(
                currentTab = AppNavTab.DD_FLASHER,
                selectedDdImage = verifiedFile ?: it.selectedDdImage
            )
        }
    }

    fun refreshManifest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, statusMessage = null) }
            val (manifest, validation) = manifestRepository.fetchManifest(
                preset = _uiState.value.selectedPreset,
                customUrl = _uiState.value.customUrl,
                channelKey = _uiState.value.selectedChannel
            )

            val device = DeviceInspector.inspectDevice(getApplication())
            val compatibility = manifest?.release?.let {
                DeviceInspector.checkCompatibility(device, it)
            }

            _uiState.update {
                it.copy(
                    isChecking = false,
                    deviceInfo = device,
                    manifest = manifest,
                    manifestValidation = validation,
                    compatibility = compatibility
                )
            }

            // Check if file already exists locally for this release
            manifest?.release?.let { release ->
                downloadManager.checkExistingFile(release)
            }
        }
    }

    fun selectChannel(channel: String) {
        manifestRepository.setSelectedChannel(channel)
        _uiState.update { it.copy(selectedChannel = channel) }
        refreshManifest()
    }

    fun selectPreset(preset: PresetManifestType) {
        manifestRepository.setSelectedPreset(preset)
        _uiState.update { it.copy(selectedPreset = preset) }
        refreshManifest()
    }

    fun saveCustomUrl(url: String) {
        manifestRepository.setConfiguredUrl(url)
        _uiState.update {
            it.copy(
                customUrl = url,
                selectedPreset = PresetManifestType.CUSTOM_URL,
                showUrlDialog = false
            )
        }
        manifestRepository.setSelectedPreset(PresetManifestType.CUSTOM_URL)
        refreshManifest()
    }

    fun startDownload() {
        val release = _uiState.value.manifest?.release ?: return
        viewModelScope.launch {
            downloadManager.startDownload(release)
        }
    }

    fun pauseDownload() {
        downloadManager.pauseDownload()
    }

    fun cancelDownload() {
        val release = _uiState.value.manifest?.release ?: return
        downloadManager.cancelDownload(release)
    }

    fun deleteDownloadedFile() {
        val release = _uiState.value.manifest?.release ?: return
        downloadManager.deleteDownloadedFile(release)
        _uiState.update { it.copy(selectedDdImage = null) }
    }

    fun recomputeHash() {
        val release = _uiState.value.manifest?.release ?: return
        val verified = downloadManager.getVerifiedGsiImage() ?: downloadManager.getDownloadedFile(release)
        viewModelScope.launch {
            downloadManager.verifyFileHash(verified, release.sha256)
        }
    }

    fun openDDFlasherReview() {
        navigateToDdFlasherWithVerifiedImage()
    }

    fun closeDDFlasherDialog() {
        _uiState.update {
            it.copy(
                showDDFlasherDialog = false,
                ddFlasherStatus = DDFlasherStatus.Idle
            )
        }
    }

    fun shareOrExportFile(context: Context) {
        val verifiedFile = downloadManager.getVerifiedGsiImage()
            ?: (uiState.value.downloadState as? DownloadState.Verified)?.file
            ?: _uiState.value.manifest?.release?.let { downloadManager.getDownloadedFile(it) }

        if (verifiedFile == null || !verifiedFile.exists()) {
            _uiState.update { it.copy(statusMessage = "No file available to export.") }
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                verifiedFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "GSI Image: ${verifiedFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Export or Share GSI Image").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(statusMessage = "Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun showGuideSheet(show: Boolean) {
        _uiState.update { it.copy(showGuideSheet = show) }
    }

    fun showUrlDialog(show: Boolean) {
        _uiState.update { it.copy(showUrlDialog = show) }
    }

    fun showRawJsonDialog(show: Boolean) {
        _uiState.update { it.copy(showRawJsonDialog = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
