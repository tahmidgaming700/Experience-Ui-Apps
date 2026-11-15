package com.experience.design.theme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

/**
 * Builds Material 3 colour schemes from a single seed colour.
 *
 * Android 11 has no system "Monet" palette, so Experience UI derives every app's
 * tonal palette from one seed (the system accent chosen in Experience Settings or
 * extracted from the wallpaper). The HCT colour space and scheme variants come from
 * material-color-utilities, the same algorithm Android 12+ uses for dynamic colour.
 */
object ExperienceColors {

    /** The Experience UI brand seed: an expressive indigo. */
    const val BRAND_SEED: Int = 0xFF4355B9.toInt()

    enum class Style { TONAL_SPOT, VIBRANT, EXPRESSIVE }

    @SuppressLint("RestrictedApi")
    fun scheme(seed: Int, dark: Boolean, style: Style = Style.TONAL_SPOT, contrast: Double = 0.0): ColorScheme {
        val hct = Hct.fromInt(seed)
        val dyn: DynamicScheme = when (style) {
            Style.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrast)
            Style.VIBRANT -> SchemeVibrant(hct, dark, contrast)
            Style.EXPRESSIVE -> SchemeExpressive(hct, dark, contrast)
        }
        val c = MaterialDynamicColors()
        fun col(dc: com.google.android.material.color.utilities.DynamicColor) = Color(dc.getArgb(dyn))
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = col(c.primary()),
            onPrimary = col(c.onPrimary()),
            primaryContainer = col(c.primaryContainer()),
            onPrimaryContainer = col(c.onPrimaryContainer()),
            inversePrimary = col(c.inversePrimary()),
            secondary = col(c.secondary()),
            onSecondary = col(c.onSecondary()),
            secondaryContainer = col(c.secondaryContainer()),
            onSecondaryContainer = col(c.onSecondaryContainer()),
            tertiary = col(c.tertiary()),
            onTertiary = col(c.onTertiary()),
            tertiaryContainer = col(c.tertiaryContainer()),
            onTertiaryContainer = col(c.onTertiaryContainer()),
            background = col(c.background()),
            onBackground = col(c.onBackground()),
            surface = col(c.surface()),
            onSurface = col(c.onSurface()),
            surfaceVariant = col(c.surfaceVariant()),
            onSurfaceVariant = col(c.onSurfaceVariant()),
            surfaceTint = col(c.surfaceTint()),
            inverseSurface = col(c.inverseSurface()),
            inverseOnSurface = col(c.inverseOnSurface()),
            error = col(c.error()),
            onError = col(c.onError()),
            errorContainer = col(c.errorContainer()),
            onErrorContainer = col(c.onErrorContainer()),
            outline = col(c.outline()),
            outlineVariant = col(c.outlineVariant()),
            scrim = col(c.scrim()),
            surfaceBright = col(c.surfaceBright()),
            surfaceDim = col(c.surfaceDim()),
            surfaceContainer = col(c.surfaceContainer()),
            surfaceContainerHigh = col(c.surfaceContainerHigh()),
            surfaceContainerHighest = col(c.surfaceContainerHighest()),
            surfaceContainerLow = col(c.surfaceContainerLow()),
            surfaceContainerLowest = col(c.surfaceContainerLowest()),
        )
    }

    /** WCAG relative-luminance contrast ratio between two opaque colours. */
    fun contrastRatio(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int): Double {
                val s = v / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
        }
        val la = lum(a); val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
