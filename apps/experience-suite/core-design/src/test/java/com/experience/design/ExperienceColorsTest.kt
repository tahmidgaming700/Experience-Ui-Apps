package com.experience.design

import androidx.compose.ui.graphics.toArgb
import com.experience.design.theme.ExperienceColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceColorsTest {
    private val seeds = listOf(ExperienceColors.BRAND_SEED, 0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFFFFEB3B.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())

    @Test
    fun textOnSurfacesMeetsWcagAa() {
        for (seed in seeds) for (dark in listOf(false, true)) for (style in ExperienceColors.Style.entries) {
            val s = ExperienceColors.scheme(seed, dark, style)
            val pairs = listOf(
                s.onSurface to s.surface, s.onPrimary to s.primary,
                s.onPrimaryContainer to s.primaryContainer, s.onSecondaryContainer to s.secondaryContainer,
                s.onSurfaceVariant to s.surfaceContainer, s.onBackground to s.background,
            )
            for ((fg, bg) in pairs) {
                val r = ExperienceColors.contrastRatio(fg.toArgb(), bg.toArgb())
                assertTrue("seed=%08x dark=$dark style=$style ratio=$r".format(seed), r >= 4.5)
            }
        }
    }

    @Test
    fun darkAndLightDiffer() {
        val l = ExperienceColors.scheme(ExperienceColors.BRAND_SEED, false)
        val d = ExperienceColors.scheme(ExperienceColors.BRAND_SEED, true)
        assertTrue(l.surface != d.surface)
    }
}
