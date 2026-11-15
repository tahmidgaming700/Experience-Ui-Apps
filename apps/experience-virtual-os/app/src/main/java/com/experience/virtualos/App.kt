package com.experience.virtualos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import com.experience.virtualos.core.SettingsStore
import com.experience.virtualos.core.host.HostInfo
import com.experience.virtualos.core.images.ImageRepository
import com.experience.virtualos.core.log.ActivityLog
import com.experience.virtualos.core.proot.ProotLauncher
import com.experience.virtualos.core.runtime.HostConstraints
import com.experience.virtualos.core.runtime.HttpDownloader
import com.experience.virtualos.core.runtime.RuntimeInstaller
import com.experience.virtualos.core.runtime.RuntimeLayout
import com.experience.virtualos.core.vm.DeviceStore
import com.experience.virtualos.core.vm.VmController
import com.experience.virtualos.core.vm.VmService
import java.io.File

class App : Application(), Configuration.Provider {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        createChannels()
        // A crash or process death during extraction can leave staging data.
        Thread({ graph.runtimeInstaller.cleanupInterrupted() }, "evos-cleanup").start()
    }

    // On-demand WorkManager initialisation (the default initializer is removed
    // in the manifest), so WorkManager never depends on provider start order.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TASKS, "Downloads and installation", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_VM, "Running virtual devices", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val CHANNEL_TASKS = "tasks"
        const val CHANNEL_VM = "vm"
    }
}

val Context.graph: AppGraph get() = (applicationContext as App).graph

/** Manual dependency container; every component is a process-wide singleton. */
class AppGraph(private val app: App) {
    val dataDir: File = app.filesDir
    val settings = SettingsStore(app, BuildConfig.RUNTIME_MANIFEST_URL)
    val activityLog = ActivityLog(File(dataDir, "logs/activity.json"))
    val downloader = HttpDownloader("ExperienceVirtualOS/${BuildConfig.VERSION_NAME} (Android)")
    val runtimeLayout = RuntimeLayout(File(dataDir, "runtime"))

    fun hostInfo(): HostInfo = HostInfo.probe(app)

    fun prootLauncher() = ProotLauncher(
        nativeLibraryDir = File(app.applicationInfo.nativeLibraryDir ?: File(app.filesDir, "missing-native-libs").path),
        tmpDir = File(app.cacheDir, "proot"),
        hostDataDir = dataDir,
        disableSeccomp = settings.state.value.disableProotSeccomp,
    )

    val runtimeInstaller = RuntimeInstaller(
        layout = runtimeLayout,
        downloader = downloader,
        host = HostConstraints(
            supportedAbis = android.os.Build.SUPPORTED_ABIS.toList(),
            sdkInt = android.os.Build.VERSION.SDK_INT,
            appVersionCode = BuildConfig.VERSION_CODE,
        ),
        smokeTest = { dir, manifest -> ProotLauncher.selfTest(prootLauncher(), dir, manifest) },
    )

    val deviceStore = DeviceStore(File(dataDir, "devices"))
    val imageRepository = ImageRepository(File(dataDir, "images"), downloader)

    val vmController = VmController(
        dataDir = dataDir,
        devices = deviceStore,
        images = imageRepository,
        runtime = runtimeInstaller,
        launcher = ::prootLauncher,
        host = ::hostInfo,
        log = activityLog,
        tcgThreads = { settings.state.value.tcgThreads },
        onRunningChanged = { running ->
            val intent = Intent(app, VmService::class.java)
            if (running) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
        },
    )
}
