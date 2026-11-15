package com.experience.design.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * System-wide theme settings shared by all Experience apps.
 *
 * Experience Settings (a platform-signed privileged app) writes these keys to
 * Settings.Secure; every app reads them and observes changes so a new accent or
 * palette style is applied live, without restarting apps.
 */
object ExperienceThemeSettings {
    /** ARGB seed colour, or absent to use the system accent overlay. */
    const val KEY_SEED = "experience_theme_seed"
    /** One of [ExperienceColors.Style] names. */
    const val KEY_STYLE = "experience_theme_style"
    /** 1 = derive the seed from the wallpaper (Experience Settings keeps KEY_SEED updated). */
    const val KEY_FROM_WALLPAPER = "experience_theme_from_wallpaper"

    fun readSeed(context: Context): Int {
        val stored = runCatching { Settings.Secure.getString(context.contentResolver, KEY_SEED) }.getOrNull()
        stored?.toLongOrNull()?.let { return it.toInt() or 0xFF000000.toInt() }
        return systemAccent(context) ?: ExperienceColors.BRAND_SEED
    }

    fun readStyle(context: Context): ExperienceColors.Style {
        val stored = runCatching { Settings.Secure.getString(context.contentResolver, KEY_STYLE) }.getOrNull()
        return ExperienceColors.Style.entries.firstOrNull { it.name == stored } ?: ExperienceColors.Style.TONAL_SPOT
    }

    /**
     * The accent of Theme.DeviceDefault. On Android 11 this reflects the accent colour
     * overlay (android.theme.customization.accent_color) currently enabled.
     */
    fun systemAccent(context: Context): Int? {
        val themed = android.view.ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
        val tv = TypedValue()
        return if (themed.theme.resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            if (tv.resourceId != 0) themed.getColor(tv.resourceId) else tv.data
        } else null
    }
}

@Composable
private fun rememberSystemSeed(): Pair<Int, ExperienceColors.Style> {
    val context = LocalContext.current
    var seed by remember { mutableIntStateOf(ExperienceThemeSettings.readSeed(context)) }
    var style by remember { mutableStateOf(ExperienceThemeSettings.readStyle(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                seed = ExperienceThemeSettings.readSeed(context)
                style = ExperienceThemeSettings.readStyle(context)
            }
        }
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(Settings.Secure.getUriFor(ExperienceThemeSettings.KEY_SEED), false, observer)
            resolver.registerContentObserver(Settings.Secure.getUriFor(ExperienceThemeSettings.KEY_STYLE), false, observer)
        }
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return seed to style
}

/**
 * Root theme for every Experience application.
 *
 * @param seedOverride use a fixed seed instead of the system one (previews, tests).
 */
@Composable
fun ExperienceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedOverride: Int? = null,
    content: @Composable () -> Unit,
) {
    val (systemSeed, style) = rememberSystemSeed()
    val seed = seedOverride ?: systemSeed
    val target = remember(seed, darkTheme, style) { ExperienceColors.scheme(seed, darkTheme, style) }
    MaterialTheme(
        colorScheme = target.animated(),
        typography = ExperienceTypography,
        shapes = ExperienceShapes,
        content = content,
    )
}

/** Cross-fades the key roles when the palette changes (accent change, dark mode). */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable fun a(c: Color) = animateColorAsState(c, ExperienceMotion.slowEffects(), label = "scheme").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        tertiaryContainer = a(tertiaryContainer), onTertiaryContainer = a(onTertiaryContainer),
        background = a(background), surface = a(surface),
        surfaceContainer = a(surfaceContainer), surfaceContainerHigh = a(surfaceContainerHigh),
        surfaceContainerLow = a(surfaceContainerLow),
    )
}
