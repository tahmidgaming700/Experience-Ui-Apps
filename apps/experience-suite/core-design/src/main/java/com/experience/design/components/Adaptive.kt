package com.experience.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Width classes per Material 3 adaptive guidance (compact < 600dp <= medium < 840dp <= expanded). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun currentWidthClass(): WidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 600 -> WidthClass.COMPACT
        w < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}
