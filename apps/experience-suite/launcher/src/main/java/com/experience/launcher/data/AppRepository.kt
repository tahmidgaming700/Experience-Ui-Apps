package com.experience.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.experience.launcher.model.AppKey
import com.experience.launcher.model.AppSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One launchable activity as shown by the launcher. */
data class LauncherApp(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** True for apps in a managed (work) profile; their icons carry the work badge. */
    val isWork: Boolean,
    /** System apps cannot be uninstalled from the launcher (only disabled in Settings). */
    val isSystem: Boolean,
    val isSuspended: Boolean,
    val section: String,
)

/** A profile the launcher shows apps for. */
data class Profile(val user: UserHandle, val serial: Long, val isWork: Boolean, val isQuiet: Boolean)

/**
 * Lists launchable activities of every profile through [LauncherApps] and keeps the
 * list current with [LauncherApps.Callback] (install / remove / update / suspend)
 * and the managed-profile broadcasts (work profile added, removed, paused, unlocked).
 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Emits (package, userSerial) when a package is uninstalled, to prune pins. */
    private val _removed = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)
    val removed: SharedFlow<Pair<String, Long>> = _removed

    /** Emits package names whose icons must be reloaded. */
    private val _changedPackages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val changedPackages: SharedFlow<String> = _changedPackages

    private var infos: Map<AppKey, LauncherActivityInfo> = emptyMap()
    private val reloadRequests = Channel<Unit>(Channel.CONFLATED)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _removed.tryEmit(packageName to serialOf(user))
            requestReload()
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = requestReload()
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            _changedPackages.tryEmit(packageName)
            requestReload()
        }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { _changedPackages.tryEmit(it) }
            requestReload()
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = requestReload()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = requestReload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = requestReload()
    }

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_LOCALE_CHANGED) _changedPackages.tryEmit("")
            requestReload()
        }
    }

    fun start() {
        launcherApps.registerCallback(callback, mainHandler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        // These are protected system broadcasts; on Android 11 no export flag exists.
        context.registerReceiver(profileReceiver, filter)
        scope.launch {
            for (request in reloadRequests) {
                reload()
                delay(250) // coalesce bursts of package events
            }
        }
        requestReload()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        runCatching { context.unregisterReceiver(profileReceiver) }
    }

    fun requestReload() {
        reloadRequests.trySend(Unit)
    }

    private suspend fun reload() = withContext(Dispatchers.Default) {
        val me = Process.myUserHandle()
        val profiles = userManager.userProfiles.map { user ->
            Profile(
                user = user,
                serial = serialOf(user),
                isWork = user != me,
                isQuiet = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false),
            )
        }
        val map = LinkedHashMap<AppKey, LauncherActivityInfo>()
        val list = ArrayList<LauncherApp>()
        for (profile in profiles) {
            val activities = runCatching { launcherApps.getActivityList(null, profile.user) }.getOrDefault(emptyList())
            for (info in activities) {
                val component = info.componentName
                if (component.packageName == context.packageName) continue
                val key = AppKey(component.packageName, component.className, profile.serial)
                if (key in map) continue
                map[key] = info
                val label = info.label?.toString()?.trim().orEmpty().ifEmpty { component.packageName }
                val flags = info.applicationInfo.flags
                list += LauncherApp(
                    key = key,
                    label = label,
                    component = component,
                    user = profile.user,
                    isWork = profile.isWork,
                    isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isSuspended = flags and ApplicationInfo.FLAG_SUSPENDED != 0 || profile.isQuiet,
                    section = AppSearch.sectionOf(label),
                )
            }
        }
        infos = map
        _profiles.value = profiles
        _apps.value = AppSearch.sorted(list) { it.label }
        _loaded.value = true
    }

    fun infoFor(key: AppKey): LauncherActivityInfo? = infos[key]

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun launch(app: LauncherApp, bounds: android.graphics.Rect? = null, options: android.os.Bundle? = null): Boolean = runCatching {
        launcherApps.startMainActivity(app.component, app.user, bounds, options)
    }.isSuccess

    fun openAppInfo(app: LauncherApp) = runCatching {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }.isSuccess

    /**
     * App shortcuts (static manifest + dynamic) for an activity. Only the default
     * home app holds the shortcut host permission; elsewhere this returns nothing.
     */
    fun shortcutsFor(app: LauncherApp): List<ShortcutInfo> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(query, app.user).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy<ShortcutInfo>({ !it.isDeclaredInManifest }, { it.rank }))
            .take(5)
    }

    fun startShortcut(shortcut: ShortcutInfo) = runCatching {
        launcherApps.startShortcut(shortcut, null, null)
    }.isSuccess

    fun shortcutIcon(shortcut: ShortcutInfo, densityDpi: Int) =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }.getOrNull()

    /** Pauses or resumes a work profile; allowed for the default launcher. */
    fun setQuietMode(profile: Profile, quiet: Boolean): Boolean =
        runCatching { userManager.requestQuietModeEnabled(quiet, profile.user) }.getOrDefault(false)
}
