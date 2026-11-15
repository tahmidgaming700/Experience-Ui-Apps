package com.experience.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.experience.calculator.CalculatorViewModel
import com.experience.calculator.R
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(vm: CalculatorViewModel) {
    val context = LocalContext.current
    val width = currentWidthClass()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showHistoryPane = width == WidthClass.EXPANDED
    val alwaysScientific = landscape || width != WidthClass.COMPACT
    var scientificOpen by rememberSaveable { mutableStateOf(false) }
    var historySheet by rememberSaveable { mutableStateOf(false) }

    val copiedMsg = stringResource(R.string.copied)
    val copy = {
        vm.copyText()?.let { text ->
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
        }
        Unit
    }
    val paste = {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { vm.paste(it.toString()) }
        Unit
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                TopRow(
                    showHistoryButton = !showHistoryPane,
                    onHistory = { historySheet = true },
                    onCopy = copy,
                    onPaste = paste,
                )
                val keyText = MaterialTheme.typography.headlineSmall.copy(fontSize = if (landscape) 20.sp else 26.sp)
                val sciText = MaterialTheme.typography.titleMedium
                CalculatorDisplay(
                    expression = vm.expression,
                    preview = vm.preview,
                    result = vm.result,
                    error = vm.error,
                    onCopy = copy,
                    modifier = Modifier.weight(if (alwaysScientific) 0.35f else 1f),
                )
                Spacer(Modifier.height(12.dp))
                if (alwaysScientific) {
                    Row(Modifier.weight(0.65f)) {
                        KeyGrid(scientificKeys(vm.inverse, vm.angle), vm::press, sciText, Modifier.weight(5f).fillMaxHeight())
                        Spacer(Modifier.width(12.dp))
                        KeyGrid(mainKeys, vm::press, keyText, Modifier.weight(4f).fillMaxHeight())
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FilledTonalIconButton(onClick = { scientificOpen = !scientificOpen }) {
                            Icon(
                                if (scientificOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = stringResource(if (scientificOpen) R.string.hide_scientific else R.string.show_scientific),
                            )
                        }
                    }
                    AnimatedVisibility(scientificOpen, enter = expandVertically(), exit = shrinkVertically()) {
                        KeyGrid(
                            scientificKeys(vm.inverse, vm.angle), vm::press, sciText,
                            Modifier.fillMaxWidth().height(176.dp).padding(bottom = 8.dp),
                        )
                    }
                    KeyGrid(mainKeys, vm::press, keyText, Modifier.weight(1.5f).fillMaxWidth())
                }
            }
            if (showHistoryPane) {
                VerticalDivider()
                HistoryPanel(
                    vm.history, onReuse = vm::reuse, onClear = vm::clearHistory,
                    modifier = Modifier.width(360.dp).fillMaxHeight().padding(top = 16.dp),
                )
            }
        }
    }

    if (historySheet) {
        ModalBottomSheet(onDismissRequest = { historySheet = false }) {
            HistoryPanel(
                vm.history,
                onReuse = { e, r -> vm.reuse(e, r); historySheet = false },
                onClear = vm::clearHistory,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
            )
        }
    }
}

@Composable
private fun TopRow(showHistoryButton: Boolean, onHistory: () -> Unit, onCopy: () -> Unit, onPaste: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (showHistoryButton) {
            IconButton(onClick = onHistory) { Icon(Icons.Outlined.History, stringResource(R.string.history)) }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menu = false; onCopy() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.paste)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                    onClick = { menu = false; onPaste() },
                )
            }
        }
    }
}
