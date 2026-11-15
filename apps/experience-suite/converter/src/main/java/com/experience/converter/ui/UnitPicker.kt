package com.experience.converter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.experience.converter.model.UnitDef
import com.experience.design.components.GroupedRow

/** Bottom sheet listing a category's units with a search field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitPicker(title: String, units: List<UnitDef>, selected: UnitDef, onDismiss: () -> Unit, onPick: (UnitDef) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = units.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.symbol.contains(query, ignoreCase = true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search units") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.padding(top = 12.dp)) {
                itemsIndexed(filtered, key = { _, u -> u.id }) { i, u ->
                    GroupedRow(
                        title = u.name, summary = u.symbol, index = i, count = filtered.size,
                        onClick = { onPick(u) },
                        trailing = if (u == selected) ({ Icon(Icons.Filled.Check, contentDescription = "Selected") }) else null,
                    )
                }
                if (filtered.isEmpty()) item { Text("No units match \"$query\"", modifier = Modifier.padding(16.dp)) }
                item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 32.dp)) }
            }
        }
    }
}
