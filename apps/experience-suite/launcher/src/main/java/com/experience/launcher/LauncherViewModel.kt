package com.experience.launcher

import android.app.Application
import android.content.pm.ShortcutInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.launcher.data.AppRepository
import com.experience.launcher.data.DefaultLayout
import com.experience.launcher.data.IconLoader
import com.experience.launcher.data.LauncherApp
import com.experience.launcher.data.LauncherPrefs
import com.experience.launcher.data.LauncherStore
import com.experience.launcher.data.Profile
import com.experience.launcher.model.AppKey
import com.experience.launcher.model.HomeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val repository = AppRepository(app, viewModelScope)
    val icons = IconLoader(app, repository)
    private val store = LauncherStore(app)

    val apps: StateFlow<List<LauncherApp>> = repository.apps
    val profiles: StateFlow<List<Profile>> = repository.profiles

    private val _layout = MutableStateFlow(store.readLayout())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    private val _prefs = MutableStateFlow(store.readPrefs())
    val prefs: StateFlow<LauncherPrefs> = _prefs.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    /** Emits when HOME is pressed while the launcher is shown (true = animate). */
    private val _homePressed = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val homePressed: SharedFlow<Boolean> = _homePressed

    init {
        repository.start()
        viewModelScope.launch {
            repository.removed.collect { (pkg, serial) -> updateLayout { it.removePackage(pkg, serial) } }
        }
        viewModelScope.launch {
            repository.changedPackages.collect { icons.invalidate(it) }
        }
        viewModelScope.launch {
            // Remove pins of profiles that were deleted (e.g. work profile removed).
            repository.profiles.collect { list ->
                if (list.isEmpty()) return@collect
                val serials = list.map { it.serial }.toSet()
                val stale = (_layout.value.home + _layout.value.dock).map { it.userSerial }.filter { it !in serials }.toSet()
                stale.forEach { s -> updateLayout { it.removeProfile(s) } }
            }
        }
        if (!store.isInitialized) {
            viewModelScope.launch {
                repository.loaded.filter { it }.first()
                val defaults = withContext(Dispatchers.Default) { DefaultLayout.build(app, repository.apps.value) }
                updateLayout { defaults }
            }
        }
    }

    override fun onCleared() {
        repository.stop()
    }

    private fun updateLayout(change: (HomeLayout) -> HomeLayout) {
        val next = change(_layout.value)
        _layout.value = next
        store.writeLayout(next)
    }

    fun updatePrefs(change: (LauncherPrefs) -> LauncherPrefs) {
        val next = change(_prefs.value)
        _prefs.value = next
        store.writePrefs(next)
    }

    fun pinToHome(key: AppKey) = updateLayout { it.pinToHome(key) }
    fun unpinFromHome(key: AppKey) = updateLayout { it.unpinFromHome(key) }
    fun pinToDock(key: AppKey) = updateLayout { it.pinToDock(key) }
    fun unpinFromDock(key: AppKey) = updateLayout { it.unpinFromDock(key) }
    fun moveOnHome(key: AppKey, delta: Int) = updateLayout { it.moveOnHome(key, delta) }
    fun moveInDock(key: AppKey, delta: Int) = updateLayout { it.moveInDock(key, delta) }
    fun resetLayout() = updateLayout { DefaultLayout.build(getApplication(), repository.apps.value) }

    fun openDrawer() { _drawerOpen.value = true }
    fun closeDrawer() { _drawerOpen.value = false }

    fun onHomePressed(animate: Boolean) {
        _drawerOpen.value = false
        _homePressed.tryEmit(animate)
    }

    fun launch(app: LauncherApp, bounds: android.graphics.Rect? = null, options: android.os.Bundle? = null): Boolean {
        val ok = repository.launch(app, bounds, options)
        if (ok) _drawerOpen.value = false
        return ok
    }

    fun shortcuts(app: LauncherApp): List<ShortcutInfo> = repository.shortcutsFor(app)

    fun setQuietMode(profile: Profile, quiet: Boolean) {
        repository.setQuietMode(profile, quiet)
        repository.requestReload()
    }
}
