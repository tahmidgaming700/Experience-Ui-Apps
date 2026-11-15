package com.experience.converter.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

object UnitConverter {
    /**
     * Converts [value] from [from] to [to]. Returns null when the result is undefined
     * (for example 0 L/100 km, which is an infinite distance per litre).
     */
    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef): BigDecimal? {
        if (from == to) return value
        return try {
            val a = from.scale
            val b = to.scale
            if (a is Scale.Linear && b is Scale.Linear) {
                // Combine both factors so there is exactly one division.
                value.multiply(a.num, MC).multiply(b.den, MC).divide(a.den.multiply(b.num, MC), MC)
            } else {
                b.fromBase(a.toBase(value))
            }
        } catch (_: ArithmeticException) {
            null
        }
    }
}

object NumberFormat {
    /** Significant digits shown to the user. */
    const val DISPLAY_DIGITS = 12

    /**
     * Parses user input. Accepts an optional sign, decimal point or comma, and an
     * exponent ("1.5e3"). Returns null for anything else.
     */
    fun parse(input: String): BigDecimal? {
        val s = input.trim().replace(" ", "").replace(' '.toString(), "").replace(',', '.')
        if (s.isEmpty() || s == "-" || s == "." || s == "-.") return null
        return runCatching { BigDecimal(s) }.getOrNull()
    }

    /**
     * Rounds to [digits] significant digits and drops trailing zeros. Very large or
     * very small magnitudes use scientific notation ("1.5E+20").
     */
    fun format(value: BigDecimal, digits: Int = DISPLAY_DIGITS): String {
        if (value.signum() == 0) return "0"
        val r = value.round(MathContext(digits, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val abs = r.abs()
        return if (abs >= BigDecimal("1E15") || abs < BigDecimal("1E-7")) {
            val unscaled = r.unscaledValue().abs().toString()
            val exponent = unscaled.length - 1 - r.scale()
            val mantissa = if (unscaled.length == 1) unscaled else unscaled[0] + "." + unscaled.substring(1)
            (if (r.signum() < 0) "-" else "") + mantissa + "E" + (if (exponent >= 0) "+" else "") + exponent
        } else {
            r.toPlainString()
        }
    }
}

/** Integer conversion between binary, octal, decimal and hexadecimal. */
object BaseConverter {
    val radices = listOf(2, 8, 10, 16)

    fun name(radix: Int) = when (radix) {
        2 -> "Binary"; 8 -> "Octal"; 10 -> "Decimal"; 16 -> "Hexadecimal"; else -> "Base $radix"
    }

    sealed interface Parsed {
        data class Ok(val value: BigInteger) : Parsed
        data object Empty : Parsed
        data class Invalid(val message: String) : Parsed
    }

    /**
     * Parses [input] in [radix]. Whitespace and '_' separators are ignored, and a
     * matching prefix (0b, 0o, 0x) is accepted. Errors name the offending digit.
     */
    fun parse(input: String, radix: Int): Parsed {
        var s = input.trim().replace(" ", "").replace("_", "")
        if (s.isEmpty()) return Parsed.Empty
        val negative = s.startsWith("-")
        if (negative) s = s.substring(1)
        val prefix = when (radix) { 2 -> "0b"; 8 -> "0o"; 16 -> "0x"; else -> null }
        if (prefix != null && s.lowercase().startsWith(prefix)) s = s.substring(2)
        if (s.isEmpty()) return Parsed.Invalid("Enter digits after the sign or prefix")
        val bad = s.firstOrNull { Character.digit(it, radix) < 0 }
        if (bad != null) return Parsed.Invalid("'$bad' is not a valid ${name(radix).lowercase()} digit")
        val v = BigInteger(s, radix)
        return Parsed.Ok(if (negative) v.negate() else v)
    }

    /** Formats [value] in [radix]; groups of 4 (bin/hex) or 3 (oct/dec) when [grouped]. */
    fun format(value: BigInteger, radix: Int, grouped: Boolean = false): String {
        val digits = value.abs().toString(radix).uppercase()
        val out = if (!grouped) digits else {
            val size = if (radix == 2 || radix == 16) 4 else 3
            val sep = if (radix == 10) "," else " "
            digits.reversed().chunked(size).joinToString(sep).reversed()
        }
        return if (value.signum() < 0) "-$out" else out
    }

    fun validDigits(radix: Int): String = "0123456789ABCDEF".take(radix)
}
