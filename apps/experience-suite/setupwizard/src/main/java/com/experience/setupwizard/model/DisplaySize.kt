package com.experience.setupwizard.model

import kotlin.math.roundToInt

/** Display-size (density) and font-size steps offered on the accessibility page. */
object DisplaySize {
    data class Step(val label: String, val scale: Float)

    val densitySteps = listOf(Step("Small", 0.85f), Step("Default", 1f), Step("Large", 1.15f), Step("Larger", 1.3f))
    val fontSteps = listOf(Step("Small", 0.85f), Step("Default", 1f), Step("Large", 1.15f), Step("Largest", 1.3f))

    /** Density for a step, rounded to an even dpi like Settings' DisplayDensityUtils. */
    fun densityFor(defaultDpi: Int, scale: Float): Int = ((defaultDpi * scale / 2f).roundToInt() * 2)

    /** Index of the step closest to the current value. */
    fun closestIndex(steps: List<Step>, scale: Float): Int =
        steps.indices.minBy { kotlin.math.abs(steps[it].scale - scale) }
}
