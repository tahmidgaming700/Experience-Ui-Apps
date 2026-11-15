package com.experience.calculator.engine

/**
 * Pure editing rules for the expression line, applied to keypad presses. Keeping them
 * free of Android types makes them unit-testable.
 */
object ExpressionEditor {
    const val MAX_LENGTH = 400
    private const val BINARY_OPS = "+−×÷^"
    /** Multi-character entries removed as one unit by backspace. */
    private val UNITS = listOf("sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sin(", "cos(", "tan(", "ln(", "log(")

    fun openParens(expr: String) = expr.count { it == '(' } - expr.count { it == ')' }

    /** The trailing number literal (digits and '.') of [expr]. */
    private fun currentNumber(expr: String) = expr.takeLastWhile { it.isDigit() || it == '.' }

    fun appendDigit(expr: String, d: Char): String {
        if (expr.length >= MAX_LENGTH) return expr
        val num = currentNumber(expr)
        // Avoid leading zeros like "007" (but keep "0.07").
        if (num == "0") return expr.dropLast(1) + d
        return expr + d
    }

    fun appendDecimal(expr: String): String {
        val num = currentNumber(expr)
        if (num.contains('.')) return expr
        if (num.isEmpty()) {
            // E notation exponent cannot take a fraction.
            if (expr.endsWith("E")) return expr
            return expr + "0."
        }
        return "$expr."
    }

    fun appendOperator(expr: String, op: Char): String {
        if (expr.isEmpty()) return if (op == '−') "−" else expr
        val last = expr.last()
        if (last in BINARY_OPS) {
            // Allow a negative operand after ×, ÷ and ^ (e.g. 2×−3); otherwise replace.
            if (op == '−' && last in "×÷^") return expr + op
            val trimmed = expr.dropLast(1)
            if (trimmed.isEmpty() || trimmed.last() == '(') return if (op == '−') trimmed + op else expr
            if (trimmed.last() in BINARY_OPS) return expr // e.g. "2×−" stays
            return trimmed + op
        }
        if (last == '(') return if (op == '−') expr + op else expr
        if (last == '.') return expr + "0" + op
        return expr + op
    }

    fun appendPostfix(expr: String, op: Char): String {
        if (expr.isEmpty()) return expr
        val last = expr.last()
        if (last in BINARY_OPS || last == '(' || last == '√') return expr
        return expr + op
    }

    /** Functions, constants, √ and '(' — implicit multiplication handles "2sin(", "3π". */
    fun appendPrefix(expr: String, text: String): String =
        if (expr.length >= MAX_LENGTH) expr else if (expr.endsWith(".")) expr + "0" + text else expr + text

    fun appendClose(expr: String): String {
        if (openParens(expr) <= 0) return expr
        val last = expr.lastOrNull() ?: return expr
        if (last in BINARY_OPS || last == '(' || last == '√') return expr
        return "$expr)"
    }

    fun backspace(expr: String): String {
        UNITS.firstOrNull { expr.endsWith(it) }?.let { return expr.dropLast(it.length) }
        return expr.dropLast(1)
    }

    /** Sanitises pasted text into something the tokenizer understands. */
    fun sanitizePaste(text: String): String = text
        .replace(",", "")
        .replace('*', '×').replace('/', '÷').replace('-', '−').replace('x', '×')
        .filter { it.isDigit() || it in "+−×÷^().%!√πeE" || it.isLetter() && it in "sincotalgh⁻¹" || it in "⁻¹" }
        .take(MAX_LENGTH)

    /** True when [expr] has something to calculate beyond a lone number (worth previewing). */
    fun hasOperation(expr: String): Boolean {
        val body = expr.trimStart('−')
        return body.any { !it.isDigit() && it != '.' && it != 'E' }
    }
}
