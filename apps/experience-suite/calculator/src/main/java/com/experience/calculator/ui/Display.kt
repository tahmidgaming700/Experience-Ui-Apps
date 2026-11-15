package com.experience.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.experience.calculator.R
import com.experience.calculator.engine.CalcError
import com.experience.calculator.engine.NumberFormatter
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape

/**
 * Single-line text that picks the largest font size in [maxSize]..[minSize] that fits the
 * available width; if it still does not fit it scrolls horizontally, pinned to the end.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    maxSize: TextUnit,
    minSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        val size = remember(text, maxWidthPx, style) {
            var s = maxSize.value
            while (s > minSize.value) {
                val w = measurer.measure(text, style.copy(fontSize = s.sp), maxLines = 1, softWrap = false,
                    constraints = Constraints(maxWidth = Constraints.Infinity), density = density).size.width
                if (w <= maxWidthPx) break
                s -= 2f
            }
            s.coerceAtLeast(minSize.value)
        }
        val animated = animateFloatAsState(size, ExperienceMotion.fastSpatial(), label = "fontSize").value
        val scroll = rememberScrollState()
        LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
        Text(
            text,
            style = style.copy(fontSize = animated.sp, textAlign = TextAlign.End),
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.horizontalScroll(scroll),
        )
    }
}

@Composable
fun errorText(error: CalcError): String = stringResource(
    when (error) {
        CalcError.SYNTAX -> R.string.error_syntax
        CalcError.DIV_ZERO -> R.string.error_div_zero
        CalcError.DOMAIN -> R.string.error_domain
        CalcError.OVERFLOW -> R.string.error_overflow
    }
)

/**
 * The expressive display: expression (large until "=" is pressed) and result/preview.
 * After "=", the result takes the large slot with a spring-animated swap. Long press copies.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorDisplay(
    expression: String,
    preview: String?,
    result: String?,
    error: CalcError?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copyLabel = stringResource(R.string.copy)
    val shownExpression = NumberFormatter.group(expression)
    Surface(
        shape = ExperienceShape.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = {}, onLongClick = onCopy, onLongClickLabel = copyLabel)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            val big = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Normal)
            val small = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)
            AnimatedContent(
                targetState = result != null,
                transitionSpec = { fadeIn(ExperienceMotion.defaultEffects()) togetherWith fadeOut(ExperienceMotion.fastEffects()) },
                label = "display",
            ) { finished ->
                Column(horizontalAlignment = Alignment.End) {
                    if (finished) {
                        AutoSizeText(shownExpression, small, 32.sp, 18.sp, MaterialTheme.colorScheme.onSurfaceVariant)
                        AutoSizeText(
                            NumberFormatter.group(result ?: ""), big, 72.sp, 30.sp, MaterialTheme.colorScheme.onSurface,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        AutoSizeText(
                            shownExpression.ifEmpty { "0" }, big, 72.sp, 28.sp, MaterialTheme.colorScheme.onSurface,
                            Modifier.semantics { contentDescription = shownExpression.ifEmpty { "0" } },
                        )
                        val secondary = when {
                            error != null -> errorText(error)
                            preview != null -> NumberFormatter.group(preview)
                            else -> ""
                        }
                        Text(
                            secondary,
                            style = small,
                            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.heightIn(min = 36.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
    }
}
