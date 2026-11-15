package com.experience.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Transcendental functions evaluated in [BigDecimal] with a few guard digits beyond
 * DECIMAL128, so results are correct to the 15 digits shown (doubles would leave the last
 * displayed digit wrong, e.g. sin 45° = 0.707106781186547|46 as a double).
 */
object BigMath {
    private val MC = MathContext(40, RoundingMode.HALF_EVEN)
    private val EPS = BigDecimal("1E-40")
    private val TWO = BigDecimal(2)
    val PI = BigDecimal("3.14159265358979323846264338327950288419716939937510582097494")
    val E = BigDecimal("2.71828182845904523536028747135266249775724709369995957496697")
    val LN10 = BigDecimal("2.30258509299404568401799145468436420760110148862877297603333")

    fun sqrt(v: BigDecimal): BigDecimal {
        require(v.signum() >= 0)
        if (v.signum() == 0) return BigDecimal.ZERO
        // Seed from double when in range; otherwise from the decimal exponent.
        val d = v.toDouble()
        var x = if (d.isFinite() && d > 0 && d < 1e300 && d > 1e-300) BigDecimal(Math.sqrt(d))
        else BigDecimal.ONE.scaleByPowerOfTen((v.precision() - v.scale()) / 2)
        repeat(200) {
            val nx = x.add(v.divide(x, MC), MC).divide(TWO, MC)
            if (nx.subtract(x).abs() <= nx.abs().multiply(EPS)) return nx
            x = nx
        }
        return x
    }

    /** sin(x), x in radians. */
    fun sin(x: BigDecimal): BigDecimal {
        val r = reduce(x)
        var term = r
        var sum = r
        val x2 = r.multiply(r, MC)
        var n = 1
        while (term.abs() > EPS) {
            term = term.multiply(x2, MC).divide(BigDecimal((2 * n) * (2 * n + 1)), MC).negate()
            sum = sum.add(term, MC)
            n++
        }
        return sum
    }

    fun cos(x: BigDecimal): BigDecimal = sin(x.add(PI.divide(TWO, MC), MC))

    /** Reduces an angle to (-π, π]. */
    private fun reduce(x: BigDecimal): BigDecimal {
        val twoPi = PI.multiply(TWO)
        var r = x.remainder(twoPi, MC)
        if (r > PI) r = r.subtract(twoPi, MC)
        if (r < PI.negate()) r = r.add(twoPi, MC)
        return r
    }

    /** atan(x) via argument halving then the Taylor series. */
    fun atan(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        var y = x
        var factor = BigDecimal.ONE
        // atan(x) = 2·atan(x / (1 + √(1 + x²))) shrinks |x| below 0.1 quickly.
        while (y.abs() > BigDecimal("0.1")) {
            y = y.divide(BigDecimal.ONE.add(sqrt(BigDecimal.ONE.add(y.multiply(y, MC), MC)), MC), MC)
            factor = factor.multiply(TWO)
        }
        val y2 = y.multiply(y, MC)
        var power = y
        var sum = y
        var n = 1
        while (true) {
            power = power.multiply(y2, MC).negate()
            val term = power.divide(BigDecimal(2 * n + 1), MC)
            if (term.abs() <= EPS) break
            sum = sum.add(term, MC)
            n++
        }
        return sum.multiply(factor, MC)
    }

    fun asin(x: BigDecimal): BigDecimal {
        val one = BigDecimal.ONE
        if (x.abs().compareTo(one) == 0) return PI.divide(TWO, MC).multiply(BigDecimal(x.signum()))
        return atan(x.divide(sqrt(one.subtract(x.multiply(x, MC), MC)), MC))
    }

    fun acos(x: BigDecimal): BigDecimal = PI.divide(TWO, MC).subtract(asin(x), MC)

    /** Natural log for x > 0: split off powers of ten, then 2·atanh((m−1)/(m+1)). */
    fun ln(x: BigDecimal): BigDecimal {
        require(x.signum() > 0)
        val exp10 = x.precision() - x.scale() - 1
        var m = x.movePointLeft(exp10) // 1 <= m < 10
        // Bring m close to 1 with square roots for fast convergence: ln m = 2^k ln(m^(1/2^k)).
        var k = 0
        while (m.subtract(BigDecimal.ONE).abs() > BigDecimal("0.1")) { m = sqrt(m); k++ }
        val z = m.subtract(BigDecimal.ONE).divide(m.add(BigDecimal.ONE), MC)
        val z2 = z.multiply(z, MC)
        var power = z
        var sum = z
        var n = 1
        while (true) {
            power = power.multiply(z2, MC)
            val term = power.divide(BigDecimal(2 * n + 1), MC)
            if (term.abs() <= EPS) break
            sum = sum.add(term, MC)
            n++
        }
        val lnM = sum.multiply(TWO).multiply(TWO.pow(k))
        return lnM.add(LN10.multiply(BigDecimal(exp10)), MC)
    }

    /** e^x via range reduction (x = n·ln10 + r) and the Taylor series of e^r. */
    fun exp(x: BigDecimal): BigDecimal {
        val n = x.divide(LN10, MC).setScale(0, RoundingMode.FLOOR)
        var r = x.subtract(LN10.multiply(n), MC) // 0 <= r < ln10
        // Halve r until tiny, square back afterwards.
        var k = 0
        while (r > BigDecimal("0.01")) { r = r.divide(TWO, MC); k++ }
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        var i = 1
        while (term.abs() > EPS) {
            term = term.multiply(r, MC).divide(BigDecimal(i), MC)
            sum = sum.add(term, MC)
            i++
        }
        repeat(k) { sum = sum.multiply(sum, MC) }
        return sum.scaleByPowerOfTen(n.intValueExact())
    }
}
