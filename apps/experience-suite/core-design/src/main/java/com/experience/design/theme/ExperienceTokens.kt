package com.experience.design.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive-inspired shape scale: larger radii than baseline M3 and an
 * extra "extra-large increased" step used for hero cards and sheets.
 */
val ExperienceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

object ExperienceShape {
    val pill = RoundedCornerShape(percent = 50)
    val hero = RoundedCornerShape(40.dp)
    val listTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    val listMiddle = RoundedCornerShape(6.dp)
    val listBottom = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val listSingle = RoundedCornerShape(24.dp)

    /** Shape for item [index] of [count] in a grouped (segmented) list. */
    fun grouped(index: Int, count: Int) = when {
        count <= 1 -> listSingle
        index == 0 -> listTop
        index == count - 1 -> listBottom
        else -> listMiddle
    }
}

/**
 * Expressive type scale: heavier display/headline weights and tighter tracking for
 * clear hierarchy. Uses the platform sans-serif family so the system font overlay
 * selected in Experience Settings applies everywhere.
 */
private val Sans = FontFamily.SansSerif

val ExperienceTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

/**
 * Expressive motion: spring-based physics for spatial (position/size) and effects
 * (colour/alpha) changes, in fast / default / slow speeds.
 */
object ExperienceMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
}

/** Spacing scale (4dp grid). */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum accessible touch target. */
    val touchTarget = 48.dp
}
