package com.experience.calculator.engine

import java.math.BigDecimal

/** Lexical tokens of a calculator expression. */
sealed interface Token {
    data class Num(val value: BigDecimal) : Token
    data class Const(val name: String) : Token            // "π" or "e"
    data class Func(val name: String) : Token             // sin, cos, tan, asin, acos, atan, ln, log
    data class Op(val symbol: Char) : Token               // + - × ÷ ^
    data object Sqrt : Token
    data object Factorial : Token
    data object Percent : Token
    data object LParen : Token
    data object RParen : Token
}

/**
 * Converts user input into [Token]s. Accepts both the display symbols used by the keypad
 * (×, ÷, −, √, π, sin⁻¹) and their ASCII spellings (*, /, -, sqrt, pi, asin) so pasted
 * text works. Upper-case `E` is the scientific-notation exponent marker, lower-case `e`
 * is Euler's number.
 */
object Tokenizer {
    private val functionNames = listOf(
        "sin⁻¹" to "asin", "cos⁻¹" to "acos", "tan⁻¹" to "atan",
        "asin" to "asin", "acos" to "acos", "atan" to "atan",
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "ln" to "ln", "log" to "log", "sqrt" to "√", "pi" to "π",
    )

    fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val s = input
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dots = 0
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) { if (s[i] == '.') dots++; i++ }
                    if (dots > 1) throw CalcException(CalcError.SYNTAX)
                    var text = s.substring(start, i)
                    if (text == ".") throw CalcException(CalcError.SYNTAX)
                    // Scientific notation: 1.5E10, 2E-3
                    if (i < s.length && s[i] == 'E') {
                        var j = i + 1
                        if (j < s.length && (s[j] == '-' || s[j] == '+' || s[j] == '−')) j++
                        val expStart = j
                        while (j < s.length && s[j].isDigit()) j++
                        if (j == expStart) throw CalcException(CalcError.SYNTAX)
                        text += "E" + s.substring(i + 1, j).replace('−', '-')
                        i = j
                    }
                    out += Token.Num(BigDecimal(text))
                }
                c == '+' -> { out += Token.Op('+'); i++ }
                c == '-' || c == '−' || c == '–' -> { out += Token.Op('-'); i++ }
                c == '*' || c == '×' || c == '·' -> { out += Token.Op('×'); i++ }
                c == '/' || c == '÷' -> { out += Token.Op('÷'); i++ }
                c == '^' -> { out += Token.Op('^'); i++ }
                c == '(' -> { out += Token.LParen; i++ }
                c == ')' -> { out += Token.RParen; i++ }
                c == '!' -> { out += Token.Factorial; i++ }
                c == '%' -> { out += Token.Percent; i++ }
                c == '√' -> { out += Token.Sqrt; i++ }
                c == 'π' -> { out += Token.Const("π"); i++ }
                c == '²' -> { out += Token.Op('^'); out += Token.Num(BigDecimal(2)); i++ }
                else -> {
                    val match = functionNames.firstOrNull { s.startsWith(it.first, i) }
                    if (match != null) {
                        out += when (match.second) {
                            "√" -> Token.Sqrt
                            "π" -> Token.Const("π")
                            else -> Token.Func(match.second)
                        }
                        i += match.first.length
                    } else if (c == 'e') {
                        out += Token.Const("e"); i++
                    } else {
                        throw CalcException(CalcError.SYNTAX)
                    }
                }
            }
        }
        return out
    }
}
