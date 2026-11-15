package com.experience.calculator.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

enum class AngleMode { DEG, RAD }

/** Error states shown to the user as text (mapped to string resources in the UI). */
enum class CalcError { SYNTAX, DIV_ZERO, DOMAIN, OVERFLOW }

class CalcException(val error: CalcError) : RuntimeException(error.name)

sealed interface CalcResult {
    data class Value(val value: BigDecimal) : CalcResult
    data class Failure(val error: CalcError) : CalcResult
}

/**
 * Recursive-descent evaluator over [BigDecimal] (34 significant digits, DECIMAL128).
 *
 * Grammar (lowest to highest precedence):
 * ```
 * expr    := term (('+' | '-') term)*
 * term    := unary (('×' | '÷') unary | <implicit ×> unary)*
 * unary   := ('-' | '+' | '√') unary | power
 * power   := postfix ('^' unary)?          // right-associative: 2^3^2 = 2^9
 * postfix := primary ('!' | '%')*
 * primary := number | π | e | func ( '(' expr ')' | unary ) | '(' expr ')'?
 * ```
 * Missing closing parentheses at the end of input are closed automatically.
 * Unary minus binds looser than '^' (-2^2 = -4), as in conventional notation.
 */
class Evaluator(private val angle: AngleMode = AngleMode.DEG) {

    companion object {
        val MC: MathContext = MathContext.DECIMAL128
        val PI: BigDecimal = BigMath.PI
        val E: BigDecimal = BigMath.E
        private val HUNDRED = BigDecimal(100)
        private val D180 = BigDecimal(180)
        private val D360 = BigDecimal(360)
        /** Results whose decimal exponent exceeds this are reported as overflow. */
        const val MAX_EXPONENT = 9999
        const val MAX_FACTORIAL = 3000

        fun evaluate(expression: String, angle: AngleMode = AngleMode.DEG): CalcResult = try {
            val tokens = Tokenizer.tokenize(expression)
            if (tokens.isEmpty()) CalcResult.Failure(CalcError.SYNTAX)
            else CalcResult.Value(Evaluator(angle).run(tokens))
        } catch (e: CalcException) {
            CalcResult.Failure(e.error)
        } catch (e: ArithmeticException) {
            CalcResult.Failure(CalcError.OVERFLOW)
        } catch (e: NumberFormatException) {
            CalcResult.Failure(CalcError.SYNTAX)
        }
    }

    private lateinit var tokens: List<Token>
    private var pos = 0

    fun run(input: List<Token>): BigDecimal {
        tokens = input
        pos = 0
        val v = expr()
        if (pos != tokens.size) throw CalcException(CalcError.SYNTAX)
        return check(v)
    }

    private fun peek(): Token? = tokens.getOrNull(pos)
    private fun next(): Token = tokens.getOrNull(pos++) ?: throw CalcException(CalcError.SYNTAX)

    private fun expr(): BigDecimal {
        var v = term()
        while (true) {
            val t = peek()
            if (t is Token.Op && (t.symbol == '+' || t.symbol == '-')) {
                pos++
                val r = term()
                v = check(if (t.symbol == '+') v.add(r, MC) else v.subtract(r, MC))
            } else return v
        }
    }

    private fun startsOperand(t: Token?) =
        t is Token.Num || t is Token.Const || t is Token.Func || t == Token.LParen || t == Token.Sqrt

    private fun term(): BigDecimal {
        var v = unary()
        while (true) {
            val t = peek()
            when {
                t is Token.Op && t.symbol == '×' -> { pos++; v = check(v.multiply(unary(), MC)) }
                t is Token.Op && t.symbol == '÷' -> { pos++; v = divide(v, unary()) }
                startsOperand(t) -> v = check(v.multiply(unary(), MC)) // implicit: 2π, 3(4)
                else -> return v
            }
        }
    }

    private fun unary(): BigDecimal {
        val t = peek()
        return when {
            t is Token.Op && t.symbol == '-' -> { pos++; unary().negate() }
            t is Token.Op && t.symbol == '+' -> { pos++; unary() }
            t == Token.Sqrt -> { pos++; sqrt(unary()) }
            else -> power()
        }
    }

    private fun power(): BigDecimal {
        val base = postfix()
        val t = peek()
        if (t is Token.Op && t.symbol == '^') {
            pos++
            return pow(base, unary())
        }
        return base
    }

    private fun postfix(): BigDecimal {
        var v = primary()
        while (true) {
            when (peek()) {
                Token.Factorial -> { pos++; v = factorial(v) }
                Token.Percent -> { pos++; v = v.divide(HUNDRED, MC) }
                else -> return v
            }
        }
    }

    private fun primary(): BigDecimal = when (val t = next()) {
        is Token.Num -> t.value
        is Token.Const -> if (t.name == "π") PI else E
        is Token.Func -> {
            val arg = if (peek() == Token.LParen) { pos++; parenRest() } else unary()
            function(t.name, arg)
        }
        Token.LParen -> parenRest()
        else -> throw CalcException(CalcError.SYNTAX)
    }

    /** After '(' — parses the inner expression and the ')' (optional at end of input). */
    private fun parenRest(): BigDecimal {
        val v = expr()
        when (peek()) {
            Token.RParen -> pos++
            null -> Unit // auto-close
            else -> throw CalcException(CalcError.SYNTAX)
        }
        return v
    }

    // ---- arithmetic helpers ----

    private fun check(v: BigDecimal): BigDecimal {
        if (v.signum() != 0 && v.precision() - v.scale() - 1 > MAX_EXPONENT) throw CalcException(CalcError.OVERFLOW)
        return v
    }

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw CalcException(CalcError.DIV_ZERO)
        return check(a.divide(b, MC))
    }

    private fun isInteger(v: BigDecimal) = v.signum() == 0 || v.stripTrailingZeros().scale() <= 0

    private fun pow(base: BigDecimal, exp: BigDecimal): BigDecimal {
        if (base.signum() == 0) {
            if (exp.signum() < 0) throw CalcException(CalcError.DIV_ZERO)
            if (exp.signum() == 0) return BigDecimal.ONE
            return BigDecimal.ZERO
        }
        if (isInteger(exp) && exp.abs() <= BigDecimal(100_000)) {
            // Estimate the decimal magnitude first so 10^99999 fails fast instead of allocating.
            val magnitude = BigMath.ln(base.abs()).toDouble() / Math.log(10.0) * exp.toDouble()
            if (magnitude > MAX_EXPONENT + 1) throw CalcException(CalcError.OVERFLOW)
            return check(base.pow(exp.intValueExact(), MC))
        }
        if (base.signum() < 0) {
            // Real odd roots of negatives: (-8)^(1/3) = -2 when 1/exp is an odd integer.
            val inv = BigDecimal.ONE.divide(exp, MC).round(MathContext(20))
            if (isInteger(inv) && inv.toBigInteger().testBit(0)) {
                return pow(base.negate(), exp).negate()
            }
            throw CalcException(CalcError.DOMAIN)
        }
        // b^y = e^(y·ln b), with a magnitude check so huge results fail fast.
        val lnResult = exp.multiply(BigMath.ln(base), MC)
        val magnitude = lnResult.toDouble() / Math.log(10.0)
        if (magnitude > MAX_EXPONENT + 1) throw CalcException(CalcError.OVERFLOW)
        if (magnitude < -MAX_EXPONENT - 1) return BigDecimal.ZERO
        return check(BigMath.exp(lnResult).round(MC))
    }

    private fun sqrt(v: BigDecimal): BigDecimal {
        if (v.signum() < 0) throw CalcException(CalcError.DOMAIN)
        return BigMath.sqrt(v).round(MC).stripZerosSafe()
    }

    private fun factorial(v: BigDecimal): BigDecimal {
        if (!isInteger(v) || v.signum() < 0) throw CalcException(CalcError.DOMAIN)
        if (v > BigDecimal(MAX_FACTORIAL)) throw CalcException(CalcError.OVERFLOW)
        var acc = BigInteger.ONE
        for (i in 2..v.toInt()) acc = acc.multiply(BigInteger.valueOf(i.toLong()))
        return check(BigDecimal(acc).round(MC))
    }

    /** Angle in degrees, snapping values within rounding error of an integer (π rad → 180°). */
    private fun toDegrees(x: BigDecimal): BigDecimal {
        val deg = if (angle == AngleMode.DEG) x else x.multiply(D180, MC).divide(PI, MC)
        val rounded = deg.setScale(0, RoundingMode.HALF_EVEN)
        return if (deg.subtract(rounded).abs() < BigDecimal("1E-25")) rounded else deg
    }

    private fun fromRadians(rad: BigDecimal): BigDecimal =
        (if (angle == AngleMode.DEG) rad.multiply(D180, MC).divide(PI, MC) else rad).round(MC)

    private fun function(name: String, x: BigDecimal): BigDecimal = when (name) {
        "sin", "cos", "tan" -> trig(name, x)
        "asin", "acos" -> {
            if (x.abs() > BigDecimal.ONE) throw CalcException(CalcError.DOMAIN)
            exactInverse(name, x) ?: fromRadians(if (name == "asin") BigMath.asin(x) else BigMath.acos(x))
        }
        "atan" -> exactInverse(name, x) ?: fromRadians(BigMath.atan(x))
        "ln" -> {
            if (x.signum() <= 0) throw CalcException(CalcError.DOMAIN)
            if (x.compareTo(BigDecimal.ONE) == 0) BigDecimal.ZERO else BigMath.ln(x).round(MC)
        }
        "log" -> {
            if (x.signum() <= 0) throw CalcException(CalcError.DOMAIN)
            val s = x.stripTrailingZeros()
            // Exact powers of ten give exact integers: log(1000) = 3, log(0.01) = -2.
            if (s.unscaledValue() == BigInteger.ONE) BigDecimal(-s.scale())
            else BigMath.ln(x).divide(BigMath.LN10, MC)
        }
        else -> throw CalcException(CalcError.SYNTAX)
    }

    /**
     * Trigonometry with exact results at the special angles (multiples of 30° and 45°)
     * so sin(180°) = 0 and cos(60°) = 0.5 instead of floating-point noise; tan of an
     * odd multiple of 90° is a domain error.
     */
    private fun trig(name: String, x: BigDecimal): BigDecimal {
        val deg = toDegrees(x)
        if (isInteger(deg)) {
            val d = deg.remainder(D360).let { if (it.signum() < 0) it.add(D360) else it }.toInt()
            exactTrig(name, d)?.let { return it }
        }
        val rad = if (angle == AngleMode.DEG) deg.remainder(D360).multiply(PI, MC).divide(D180, MC) else x
        return when (name) {
            "sin" -> BigMath.sin(rad).round(MC)
            "cos" -> BigMath.cos(rad).round(MC)
            else -> {
                val c = BigMath.cos(rad)
                if (c.signum() == 0) throw CalcException(CalcError.DOMAIN)
                check(BigMath.sin(rad).divide(c, MC))
            }
        }
    }

    private fun exactTrig(name: String, d: Int): BigDecimal? {
        val half = BigDecimal("0.5")
        fun sinExact(a: Int): BigDecimal? = when (a) {
            0, 180 -> BigDecimal.ZERO
            90 -> BigDecimal.ONE
            270 -> BigDecimal.ONE.negate()
            30, 150 -> half
            210, 330 -> half.negate()
            else -> null
        }
        return when (name) {
            "sin" -> sinExact(d)
            "cos" -> sinExact((d + 90) % 360)
            else -> when (d) {
                0, 180 -> BigDecimal.ZERO
                45, 225 -> BigDecimal.ONE
                135, 315 -> BigDecimal.ONE.negate()
                90, 270 -> throw CalcException(CalcError.DOMAIN)
                else -> null
            }
        }
    }

    private fun exactInverse(name: String, x: BigDecimal): BigDecimal? {
        if (angle != AngleMode.DEG) return null
        val v = x.stripZerosSafe()
        fun eq(s: String) = v.compareTo(BigDecimal(s)) == 0
        return when (name) {
            "asin" -> when { eq("0") -> BigDecimal.ZERO; eq("0.5") -> BigDecimal(30); eq("-0.5") -> BigDecimal(-30); eq("1") -> BigDecimal(90); eq("-1") -> BigDecimal(-90); else -> null }
            "acos" -> when { eq("1") -> BigDecimal.ZERO; eq("0.5") -> BigDecimal(60); eq("0") -> BigDecimal(90); eq("-0.5") -> BigDecimal(120); eq("-1") -> BigDecimal(180); else -> null }
            else -> when { eq("0") -> BigDecimal.ZERO; eq("1") -> BigDecimal(45); eq("-1") -> BigDecimal(-45); else -> null }
        }
    }
}

internal fun BigDecimal.stripZerosSafe(): BigDecimal = if (signum() == 0) BigDecimal.ZERO else stripTrailingZeros()
