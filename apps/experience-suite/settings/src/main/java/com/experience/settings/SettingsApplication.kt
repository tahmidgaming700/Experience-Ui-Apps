package com.experience.settings

import android.app.Application
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.experience.settings.system.ThemeController
import java.util.concurrent.Executors

/**
 * Keeps "Match wallpaper" in effect: the seed is re-derived when the process starts
 * and whenever the wallpaper (or its colours) change while the app is running.
 */
class SettingsApplication : Application() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        val theme = ThemeController(this)
        val refresh = { worker.execute { theme.refreshFromWallpaper() } }
        refresh()
        // WallpaperColors change callback (API 27+).
        runCatching {
            WallpaperManager.getInstance(this).addOnColorsChangedListener({ _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) refresh()
            }, Handler(Looper.getMainLooper()))
        }
        // The wallpaper-changed broadcast (only deliverable to runtime receivers).
        @Suppress("DEPRECATION")
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh() }
        }, filter)
    }
}
