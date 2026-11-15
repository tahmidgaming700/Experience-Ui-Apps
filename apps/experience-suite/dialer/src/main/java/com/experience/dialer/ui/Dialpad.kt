package com.experience.dialer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape

private val rows = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/**
 * Expressive 4×3 dial pad. [onPress]/[onRelease] fire on touch down/up (used for
 * in-call DTMF), [onKey] on click, [onLongKey] on long-press (0 → +, 1 → voicemail).
 */
@Composable
fun Dialpad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 68.dp,
    onLongKey: ((Char) -> Unit)? = null,
    onPress: ((Char) -> Unit)? = null,
    onRelease: ((Char) -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (digit, letters) ->
                    DialKey(
                        digit, letters, Modifier.weight(1f).height(keyHeight),
                        onKey, onLongKey, onPress, onRelease, containerColor, contentColor,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: Char,
    letters: String,
    modifier: Modifier,
    onKey: (Char) -> Unit,
    onLongKey: ((Char) -> Unit)?,
    onPress: ((Char) -> Unit)?,
    onRelease: ((Char) -> Unit)?,
    containerColor: Color,
    contentColor: Color,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, ExperienceMotion.fastSpatial(), label = "key")
    LaunchedEffect(source) {
        source.interactions.collect {
            when (it) {
                is PressInteraction.Press -> onPress?.invoke(digit)
                is PressInteraction.Release, is PressInteraction.Cancel -> onRelease?.invoke(digit)
            }
        }
    }
    val longLabel = when {
        onLongKey == null -> null
        digit == '0' -> "Type plus"
        digit == '1' -> "Call voicemail"
        else -> null
    }
    val spoken = when (digit) { '*' -> "star"; '#' -> "pound"; else -> digit.toString() } +
        if (letters.isNotEmpty() && letters != "+") ", $letters" else ""
    Surface(
        shape = if (pressed) ExperienceShape.listSingle else ExperienceShape.pill,
        color = if (pressed) MaterialTheme.colorScheme.primaryContainer else containerColor,
        contentColor = if (pressed) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = spoken }
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onClick = { onKey(digit) },
                onLongClick = if (longLabel != null) ({ onLongKey?.invoke(digit) }) else null,
                onLongClickLabel = longLabel,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
                when {
                    digit == '1' && onLongKey != null -> Icon(Icons.Filled.Voicemail, null, Modifier.size(14.dp))
                    letters.isNotEmpty() -> Text(letters, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
