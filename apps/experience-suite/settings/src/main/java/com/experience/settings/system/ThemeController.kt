package com.experience.settings.system

import android.app.WallpaperManager
import android.content.Context
import android.provider.Settings
import com.experience.design.theme.ExperienceColors
import com.experience.design.theme.ExperienceThemeSettings

/**
 * Writes the system-wide Experience theme (Settings.Secure keys read by every
 * Experience app through ExperienceThemeSettings) and keeps the framework/SystemUI
 * accent overlay in step, so system UI matches the apps.
 */
class ThemeController(private val context: Context) {
    private val resolver = context.contentResolver
    val overlays = Overlays(context)

    data class Swatch(val name: String, val argb: Int)

    companion object {
        /** Curated seed colours offered in Style & wallpaper. */
        val PALETTE = listOf(
            Swatch("Experience indigo", ExperienceColors.BRAND_SEED),
            Swatch("Ocean", 0xFF0061A4.toInt()),
            Swatch("Teal", 0xFF006A6A.toInt()),
            Swatch("Forest", 0xFF386A20.toInt()),
            Swatch("Olive", 0xFF5B6300.toInt()),
            Swatch("Amber", 0xFF7D5700.toInt()),
            Swatch("Terracotta", 0xFFA23F16.toInt()),
            Swatch("Rose", 0xFFB4235A.toInt()),
            Swatch("Grape", 0xFF7442A8.toInt()),
            Swatch("Slate", 0xFF535F70.toInt()),
        )
    }

    /** Stored seed, or null when unset (apps then follow the accent overlay). */
    fun storedSeed(): Int? = runCatching { Settings.Secure.getString(resolver, ExperienceThemeSettings.KEY_SEED) }
        .getOrNull()?.toLongOrNull()?.toInt()

    fun effectiveSeed(): Int = ExperienceThemeSettings.readSeed(context)
    fun style(): ExperienceColors.Style = ExperienceThemeSettings.readStyle(context)
    fun fromWallpaper(): Boolean = runCatching { Settings.Secure.getInt(resolver, ExperienceThemeSettings.KEY_FROM_WALLPAPER, 0) == 1 }.getOrDefault(false)

    /** Primary colour of the home-screen wallpaper (WallpaperColors, API 27+). */
    fun wallpaperSeed(): Int? = runCatching {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    }.getOrNull()?.let { it or 0xFF000000.toInt() }

    /** True when this app may write Settings.Secure (WRITE_SECURE_SETTINGS, granted in the ROM). */
    fun canWrite(): Boolean = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Saves [seed] (decimal ARGB as ExperienceThemeSettings expects) and the
     * wallpaper flag, then selects the nearest installed accent overlay.
     */
    fun applySeed(seed: Int, fromWallpaper: Boolean): Boolean {
        val ok = runCatching {
            Settings.Secure.putString(resolver, ExperienceThemeSettings.KEY_SEED, (seed.toLong() and 0xFFFFFFFFL).toString())
            Settings.Secure.putString(resolver, ExperienceThemeSettings.KEY_FROM_WALLPAPER, if (fromWallpaper) "1" else "0")
        }.isSuccess
        if (ok) syncAccentOverlay(seed)
        return ok
    }

    fun applyStyle(style: ExperienceColors.Style): Boolean = runCatching {
        Settings.Secure.putString(resolver, ExperienceThemeSettings.KEY_STYLE, style.name)
    }.isSuccess

    /** Clears the custom seed: apps go back to the system accent overlay colour. */
    fun clearSeed(): Boolean = runCatching {
        Settings.Secure.putString(resolver, ExperienceThemeSettings.KEY_SEED, null)
        Settings.Secure.putString(resolver, ExperienceThemeSettings.KEY_FROM_WALLPAPER, "0")
    }.isSuccess

    /** Installed accent overlays (discovered, never hard-coded) with their colours. */
    fun accentOverlays(): List<Pair<OverlayEntry, Int>> = overlays.list(Overlays.CATEGORY_ACCENT)
        .mapNotNull { e -> overlays.accentColor(e.packageName)?.let { e to it } }

    /** Enables the accent overlay nearest to [seed]; returns its package or null. */
    fun syncAccentOverlay(seed: Int): String? {
        val candidates = accentOverlays().associate { it.first.packageName to it.second }
        val pkg = ColorMath.nearest(seed, candidates) ?: return null
        val already = overlays.list(Overlays.CATEGORY_ACCENT).firstOrNull { it.packageName == pkg }?.enabled == true
        return if (already || overlays.enableExclusive(pkg)) pkg else null
    }

    /** Re-derives the seed from the wallpaper when "Match wallpaper" is on. */
    fun refreshFromWallpaper(): Boolean {
        if (!fromWallpaper()) return false
        val seed = wallpaperSeed() ?: return false
        if (storedSeed() == seed) return false
        return applySeed(seed, fromWallpaper = true)
    }
}
