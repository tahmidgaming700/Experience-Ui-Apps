package com.experience.calculator.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.experience.calculator.Key
import com.experience.calculator.R
import com.experience.calculator.engine.AngleMode
import com.experience.design.theme.ExperienceMotion

enum class KeyStyle { NUMBER, FUNCTION, OPERATOR, ACCENT, EQUALS }

/** One spec per button: what it shows, what it does, how it is described to TalkBack. */
data class KeySpec(val label: String, val key: Key, val style: KeyStyle, val description: Int? = null)

/**
 * Expressive key: a pill at rest that morphs to a rounded square while pressed
 * (spring-animated corner radius), with tonal colours per key role.
 */
@Composable
fun RowScope.CalcButton(spec: KeySpec, onPress: (Key) -> Unit, textStyle: TextStyle) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val radius by animateIntAsState(if (pressed) 22 else 50, ExperienceMotion.fastSpatial(), label = "radius")
    val scheme = MaterialTheme.colorScheme
    val (bg: Color, fg: Color) = when (spec.style) {
        KeyStyle.NUMBER -> scheme.surfaceContainerHigh to scheme.onSurface
        KeyStyle.FUNCTION -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyStyle.OPERATOR -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        KeyStyle.ACCENT -> scheme.primaryContainer to scheme.onPrimaryContainer
        KeyStyle.EQUALS -> scheme.primary to scheme.onPrimary
    }
    val description = spec.description?.let { stringResource(it) } ?: spec.label
    Surface(
        shape = RoundedCornerShape(percent = radius),
        color = bg,
        contentColor = fg,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 48.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button) { onPress(spec.key) }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (spec.key == Key.Backspace) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
            } else {
                Text(spec.label, style = textStyle, maxLines = 1)
            }
        }
    }
}

/** Lays [rows] out as an evenly spaced grid filling the given space. */
@Composable
fun KeyGrid(rows: List<List<KeySpec>>, onPress: (Key) -> Unit, textStyle: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { CalcButton(it, onPress, textStyle) }
            }
        }
    }
}

val mainKeys: List<List<KeySpec>> = listOf(
    listOf(KeySpec("AC", Key.Clear, KeyStyle.ACCENT, R.string.key_clear), KeySpec("(", Key.Open, KeyStyle.FUNCTION, R.string.key_open), KeySpec(")", Key.Close, KeyStyle.FUNCTION, R.string.key_close), KeySpec("÷", Key.Operator('÷'), KeyStyle.OPERATOR, R.string.key_divide)),
    listOf(KeySpec("7", Key.Digit('7'), KeyStyle.NUMBER), KeySpec("8", Key.Digit('8'), KeyStyle.NUMBER), KeySpec("9", Key.Digit('9'), KeyStyle.NUMBER), KeySpec("×", Key.Operator('×'), KeyStyle.OPERATOR, R.string.key_multiply)),
    listOf(KeySpec("4", Key.Digit('4'), KeyStyle.NUMBER), KeySpec("5", Key.Digit('5'), KeyStyle.NUMBER), KeySpec("6", Key.Digit('6'), KeyStyle.NUMBER), KeySpec("−", Key.Operator('−'), KeyStyle.OPERATOR, R.string.key_minus)),
    listOf(KeySpec("1", Key.Digit('1'), KeyStyle.NUMBER), KeySpec("2", Key.Digit('2'), KeyStyle.NUMBER), KeySpec("3", Key.Digit('3'), KeyStyle.NUMBER), KeySpec("+", Key.Operator('+'), KeyStyle.OPERATOR, R.string.key_plus)),
    listOf(KeySpec("0", Key.Digit('0'), KeyStyle.NUMBER), KeySpec(".", Key.Decimal, KeyStyle.NUMBER, R.string.key_point), KeySpec("⌫", Key.Backspace, KeyStyle.NUMBER, R.string.key_backspace), KeySpec("=", Key.Equals, KeyStyle.EQUALS, R.string.key_equals)),
)

/** Scientific keys; [inverse] swaps trig functions for their inverses. */
fun scientificKeys(inverse: Boolean, angle: AngleMode): List<List<KeySpec>> {
    fun fn(name: String, desc: Int) = KeySpec(name, Key.Prefix("$name("), KeyStyle.FUNCTION, desc)
    val trig = if (inverse) listOf(fn("sin⁻¹", R.string.key_asin), fn("cos⁻¹", R.string.key_acos), fn("tan⁻¹", R.string.key_atan))
    else listOf(fn("sin", R.string.key_sin), fn("cos", R.string.key_cos), fn("tan", R.string.key_tan))
    return listOf(
        listOf(
            KeySpec(if (inverse) "INV ✓" else "INV", Key.ToggleInverse, KeyStyle.FUNCTION, if (inverse) R.string.key_inverse_on else R.string.key_inverse),
            KeySpec(if (angle == AngleMode.DEG) "DEG" else "RAD", Key.ToggleAngle, KeyStyle.FUNCTION, if (angle == AngleMode.DEG) R.string.key_deg else R.string.key_rad),
            KeySpec("%", Key.Postfix('%'), KeyStyle.FUNCTION, R.string.key_percent),
            KeySpec("!", Key.Postfix('!'), KeyStyle.FUNCTION, R.string.key_factorial),
            KeySpec("^", Key.Operator('^'), KeyStyle.FUNCTION, R.string.key_power),
        ),
        trig + listOf(fn("ln", R.string.key_ln), fn("log", R.string.key_log)),
        listOf(
            KeySpec("√", Key.Prefix("√"), KeyStyle.FUNCTION, R.string.key_sqrt),
            KeySpec("π", Key.Prefix("π"), KeyStyle.FUNCTION, R.string.key_pi),
            KeySpec("e", Key.Prefix("e"), KeyStyle.FUNCTION, R.string.key_e),
            KeySpec("x²", Key.Square, KeyStyle.FUNCTION, R.string.key_square),
            KeySpec("E", Key.Exponent, KeyStyle.FUNCTION, R.string.key_exp),
        ),
    )
}

