package com.experience.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formats results for display: at most [DISPLAY_DIGITS] significant digits (which hides
 * the residue of 34-digit internal arithmetic, e.g. 1/3×3 = 1), no trailing zeros, plain
 * notation for moderate magnitudes and `1.5E20` scientific notation otherwise. The output
 * can be fed back into [Tokenizer] unchanged (no grouping separators).
 */
object NumberFormatter {
    const val DISPLAY_DIGITS = 15

    fun format(value: BigDecimal, digits: Int = DISPLAY_DIGITS): String {
        if (value.signum() == 0) return "0"
        val r = value.round(MathContext(digits, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (r.signum() == 0) return "0"
        val exponent = r.precision() - r.scale() - 1
        if (exponent in -6 until digits) return r.toPlainString()
        val unscaled = r.unscaledValue().abs().toString()
        val mantissa = if (unscaled.length > 1) unscaled[0] + "." + unscaled.substring(1) else unscaled
        return (if (r.signum() < 0) "-" else "") + mantissa + "E" + exponent
    }

    /**
     * Adds thousands separators to the integer part of each number in [text] for display
     * only (the editable expression never contains them).
     */
    fun group(text: String, separator: Char = ','): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isDigit() && (i == 0 || (!text[i - 1].isDigit() && text[i - 1] != '.'))) {
                var j = i
                while (j < text.length && text[j].isDigit()) j++
                val digits = text.substring(i, j)
                val afterExponent = i > 0 && (text[i - 1] == 'E' || (i > 1 && text[i - 2] == 'E' && text[i - 1] in "-+−"))
                if (afterExponent) sb.append(digits) else {
                    for ((k, d) in digits.withIndex()) {
                        if (k > 0 && (digits.length - k) % 3 == 0) sb.append(separator)
                        sb.append(d)
                    }
                }
                i = j
                // Copy the fractional part verbatim.
                if (i < text.length && text[i] == '.') {
                    sb.append('.'); i++
                    while (i < text.length && text[i].isDigit()) { sb.append(text[i]); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
