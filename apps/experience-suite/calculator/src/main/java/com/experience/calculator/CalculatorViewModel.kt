package com.experience.calculator

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.experience.calculator.data.HistoryEntry
import com.experience.calculator.data.HistoryStore
import com.experience.calculator.engine.AngleMode
import com.experience.calculator.engine.CalcError
import com.experience.calculator.engine.CalcResult
import com.experience.calculator.engine.Evaluator
import com.experience.calculator.engine.ExpressionEditor
import com.experience.calculator.engine.NumberFormatter

/** Keys on the pad. Functions insert their name plus "(". */
sealed interface Key {
    data class Digit(val d: Char) : Key
    data class Operator(val op: Char) : Key
    data class Postfix(val op: Char) : Key
    data class Prefix(val text: String) : Key
    data object Decimal : Key
    data object Open : Key
    data object Close : Key
    data object Backspace : Key
    data object Clear : Key
    data object Equals : Key
    data object Square : Key
    data object Exponent : Key
    data object ToggleInverse : Key
    data object ToggleAngle : Key
}

class CalculatorViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val store = HistoryStore(app)

    var expression by mutableStateOf(saved.get<String>("expr") ?: "")
        private set
    /** Non-null after "=": the shown final result. */
    var result by mutableStateOf<String?>(saved.get<String>("result"))
        private set
    var error by mutableStateOf<CalcError?>(null)
        private set
    var angle by mutableStateOf(runCatching { AngleMode.valueOf(store.angleModeName) }.getOrDefault(AngleMode.DEG))
        private set
    var inverse by mutableStateOf(false)
    var history by mutableStateOf(store.load())
        private set

    /** Live preview of the current expression (null when there is nothing to preview). */
    val preview: String?
        get() {
            if (result != null || !ExpressionEditor.hasOperation(expression)) return null
            return (Evaluator.evaluate(expression, angle) as? CalcResult.Value)?.let { NumberFormatter.format(it.value) }
        }

    private fun update(expr: String) {
        expression = expr
        error = null
        saved["expr"] = expr
    }

    private fun storeResult(r: String?) {
        result = r
        saved["result"] = r
    }

    fun toggleAngle() {
        angle = if (angle == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        store.angleModeName = angle.name
    }

    fun press(key: Key) {
        when (key) {
            Key.ToggleInverse -> { inverse = !inverse; return }
            Key.ToggleAngle -> { toggleAngle(); return }
            else -> Unit
        }
        val finished = result
        // After "=", digits start over while operators continue from the result.
        if (finished != null && key != Key.Equals) {
            storeResult(null)
            val continues = key is Key.Operator || key is Key.Postfix || key == Key.Square
            update(if (continues) finished.replace('-', '−') else "")
            if (key == Key.Backspace || key == Key.Clear) return
        }
        val e = expression
        when (key) {
            is Key.Digit -> update(ExpressionEditor.appendDigit(e, key.d))
            is Key.Operator -> update(ExpressionEditor.appendOperator(e, key.op))
            is Key.Postfix -> update(ExpressionEditor.appendPostfix(e, key.op))
            is Key.Prefix -> update(ExpressionEditor.appendPrefix(e, key.text))
            Key.Decimal -> update(ExpressionEditor.appendDecimal(e))
            Key.Open -> update(ExpressionEditor.appendPrefix(e, "("))
            Key.Close -> update(ExpressionEditor.appendClose(e))
            Key.Backspace -> update(ExpressionEditor.backspace(e))
            Key.Clear -> update("")
            Key.Equals -> equals()
            Key.Square -> {
                val withPow = ExpressionEditor.appendPostfix(e, '^')
                if (withPow != e) update(withPow + "2")
            }
            Key.Exponent -> if (e.lastOrNull()?.isDigit() == true && !e.takeLastWhile { it.isDigit() || it == '.' || it == 'E' }.contains('E')) update(e + "E")
            Key.ToggleInverse, Key.ToggleAngle -> Unit
        }
    }

    private fun equals() {
        if (expression.isBlank() || result != null) return
        when (val r = Evaluator.evaluate(expression, angle)) {
            is CalcResult.Value -> {
                val text = NumberFormatter.format(r.value)
                if (ExpressionEditor.hasOperation(expression)) {
                    val closed = expression + ")".repeat(ExpressionEditor.openParens(expression).coerceAtLeast(0))
                    history = store.add(HistoryEntry(closed, text, System.currentTimeMillis()))
                    update(closed)
                }
                storeResult(text)
            }
            is CalcResult.Failure -> error = r.error
        }
    }

    fun reuse(entry: HistoryEntry, useResult: Boolean) {
        storeResult(null)
        update(if (useResult) entry.result.replace('-', '−') else entry.expression)
    }

    fun paste(text: String) {
        val clean = ExpressionEditor.sanitizePaste(text)
        if (clean.isEmpty()) return
        if (result != null) { storeResult(null); update("") }
        update((expression + clean).take(ExpressionEditor.MAX_LENGTH))
    }

    fun clearHistory() {
        store.clear()
        history = emptyList()
    }

    /** Text to copy: the final result, else the preview, else the expression. */
    fun copyText(): String? = result ?: preview ?: expression.ifBlank { null }
}
