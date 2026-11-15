package com.experience.converter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.experience.converter.ConverterViewModel
import com.experience.converter.model.Category
import com.experience.converter.model.NumberFormat
import com.experience.converter.model.UnitCatalog
import com.experience.converter.model.UnitConverter
import com.experience.converter.model.UnitDef
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState

@Composable
fun ConvertScreen(vm: ConverterViewModel, category: Category, onBack: (() -> Unit)?) {
    val state = vm.state(category)
    val units = category.units
    val from = UnitCatalog.find(category, state.fromId) ?: units.first()
    val to = UnitCatalog.find(category, state.toId) ?: units[1]
    val value = NumberFormat.parse(state.input)
    val result = value?.let { UnitConverter.convert(it, from, to) }
    val resultText = when {
        state.input.isBlank() -> ""
        value == null -> "Not a number"
        result == null -> "Undefined"
        else -> NumberFormat.format(result)
    }
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = to
    var rotation by remember { mutableFloatStateOf(0f) }
    val angle by animateFloatAsState(rotation, ExperienceMotion.fastSpatial(), label = "swap")

    // Remember the conversion once the user stops typing.
    LaunchedEffect(category, state) {
        delay(1500)
        if (value != null) vm.remember(category)
    }

    ExperienceScaffold(title = category.title, onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(20.dp)) {
                    UnitButton("From", from) { picking = true }
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = { vm.update(category, state.copy(input = it.filter { c -> c.isDigit() || c in ".,-eE+" })) },
                            label = { Text("Value in ${from.symbol}") },
                            isError = state.input.isNotBlank() && value == null,
                            supportingText = if (state.input.isNotBlank() && value == null) ({ Text("Enter a number such as 12.5 or 3e8") }) else null,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(8.dp))
                        TextButton(
                            onClick = {
                                val s = state.input
                                vm.update(category, state.copy(input = if (s.startsWith("-")) s.drop(1) else "-$s"))
                            },
                            modifier = Modifier.semantics { contentDescription = "Toggle negative sign" },
                        ) { Text("±", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(
                    onClick = {
                        rotation += 180f
                        vm.update(category, state.copy(fromId = to.id, toId = from.id, input = if (result != null) NumberFormat.format(result) else state.input))
                    },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Outlined.SwapVert, contentDescription = "Swap units", modifier = Modifier.rotate(angle)) }
            }
            Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.padding(20.dp)) {
                    UnitButton("To", to) { picking = false }
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = resultText,
                            transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut() },
                            label = "result",
                            modifier = Modifier.weight(1f),
                        ) { text ->
                            SelectionContainer {
                                Text(
                                    if (text.isEmpty()) "—" else text,
                                    style = MaterialTheme.typography.displaySmall,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                        IconButton(
                            enabled = result != null,
                            onClick = {
                                clipboard.setText(AnnotatedString(resultText))
                                vm.remember(category)
                                scope.launch { snackbar.showSnackbar("Copied $resultText ${to.symbol}") }
                            },
                        ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy result") }
                    }
                    Text(to.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.size(8.dp))
            val one = UnitConverter.convert(java.math.BigDecimal.ONE, from, to)
            Text(
                if (one != null) "1 ${from.symbol} = ${NumberFormat.format(one)} ${to.symbol}" else "1 ${from.symbol} has no finite value in ${to.symbol}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            if (value != null) {
                SectionHeader("All ${category.title.lowercase()} units")
                val others = units.filter { it != from }
                others.forEachIndexed { i, u ->
                    val v = UnitConverter.convert(value, from, u)
                    GroupedRow(
                        title = (v?.let { NumberFormat.format(it) } ?: "Undefined") + " " + u.symbol,
                        summary = u.name + if (u == to) " · current target" else "",
                        index = i, count = others.size,
                        onClick = { vm.update(category, state.copy(toId = u.id)) },
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    picking?.let { isFrom ->
        UnitPicker(
            title = if (isFrom) "Convert from" else "Convert to",
            units = units,
            selected = if (isFrom) from else to,
            onDismiss = { picking = null },
            onPick = { u ->
                vm.update(category, if (isFrom) state.copy(fromId = u.id) else state.copy(toId = u.id))
                picking = null
            },
        )
    }
}

@Composable
private fun UnitButton(label: String, unit: UnitDef, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ExperienceShape.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { contentDescription = "$label: ${unit.name}. Change unit" },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${unit.name} (${unit.symbol})", style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
    }
}
