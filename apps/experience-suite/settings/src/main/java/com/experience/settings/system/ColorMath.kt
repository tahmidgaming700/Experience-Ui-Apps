package com.experience.settings.system

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/** Perceptual colour distance (CIELAB ΔE76), used to match the seed to an accent overlay. */
object ColorMath {
    data class Lab(val l: Double, val a: Double, val b: Double)

    fun toLab(argb: Int): Lab {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((argb shr 16) and 0xFF); val g = lin((argb shr 8) and 0xFF); val b = lin(argb and 0xFF)
        // sRGB -> XYZ (D65), normalised by the reference white.
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 1.0
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun distance(a: Int, b: Int): Double {
        val p = toLab(a); val q = toLab(b)
        return sqrt((p.l - q.l).pow(2) + (p.a - q.a).pow(2) + (p.b - q.b).pow(2))
    }

    /** Key of the candidate colour nearest to [seed], or null if there are none. */
    fun <K> nearest(seed: Int, candidates: Map<K, Int>): K? = candidates.minByOrNull { distance(seed, it.value) }?.key

    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}
