package com.experience.converter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.experience.converter.ConverterViewModel
import com.experience.converter.model.BaseConverter
import com.experience.converter.model.Category
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Integer conversion between binary, octal, decimal and hexadecimal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(vm: ConverterViewModel, onBack: (() -> Unit)?) {
    val category = Category.NUMBER_BASE
    val state = vm.state(category)
    val radix = state.fromId.toIntOrNull()?.takeIf { it in BaseConverter.radices } ?: 10
    val parsed = BaseConverter.parse(state.input, radix)
    var grouped by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        delay(1500)
        if (parsed is BaseConverter.Parsed.Ok) vm.remember(category)
    }

    ExperienceScaffold(title = category.title, onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(20.dp)) {
                    Text("Input base", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        BaseConverter.radices.forEachIndexed { i, r ->
                            SegmentedButton(
                                selected = r == radix,
                                onClick = {
                                    // Re-express the current value in the newly chosen base.
                                    val text = (parsed as? BaseConverter.Parsed.Ok)?.let { BaseConverter.format(it.value, r) } ?: state.input
                                    vm.update(category, state.copy(fromId = r.toString(), input = text))
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, BaseConverter.radices.size),
                                label = { Text(shortName(r)) },
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    val error = parsed as? BaseConverter.Parsed.Invalid
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { vm.update(category, state.copy(input = it)) },
                        label = { Text("${BaseConverter.name(radix)} value") },
                        isError = error != null,
                        supportingText = { Text(error?.message ?: "Digits: ${BaseConverter.validDigits(radix)}") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (radix == 16) KeyboardType.Ascii else KeyboardType.Number,
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SectionHeader("Results")
            val value = (parsed as? BaseConverter.Parsed.Ok)?.value
            BaseConverter.radices.forEachIndexed { i, r ->
                val text = value?.let { BaseConverter.format(it, r, grouped) } ?: "—"
                GroupedRow(
                    title = text,
                    summary = BaseConverter.name(r),
                    index = i, count = BaseConverter.radices.size,
                    trailing = {
                        IconButton(enabled = value != null, onClick = {
                            val plain = value?.let { BaseConverter.format(it, r) } ?: return@IconButton
                            clipboard.setText(AnnotatedString(plain))
                            vm.remember(category)
                            scope.launch { snackbar.showSnackbar("Copied ${BaseConverter.name(r).lowercase()} value") }
                        }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy ${BaseConverter.name(r).lowercase()} value") }
                    },
                )
            }
            Spacer(Modifier.size(12.dp))
            SwitchRow(title = "Group digits", summary = "Separate digits in groups for readability", checked = grouped, onCheckedChange = { grouped = it }, index = 0, count = 1)
            if (value != null) {
                Text(
                    "${value.bitLength()} bit${if (value.bitLength() == 1) "" else "s"} needed (${if (value.signum() < 0) "two's complement adds a sign bit" else "unsigned"})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

private fun shortName(r: Int) = when (r) { 2 -> "BIN"; 8 -> "OCT"; 10 -> "DEC"; else -> "HEX" }
