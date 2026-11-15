package com.experience.calculator.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.experience.calculator.R
import com.experience.calculator.data.HistoryEntry
import com.experience.calculator.engine.NumberFormatter
import com.experience.design.components.EmptyState
import com.experience.design.components.GroupedRow

/** History list: tap an entry to reuse its expression, the "Use result" button inserts the result. */
@Composable
fun HistoryPanel(
    history: List<HistoryEntry>,
    onReuse: (HistoryEntry, Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.clear_history)) }
        }
        if (history.isEmpty()) {
            EmptyState(Icons.Outlined.History, stringResource(R.string.history_empty), stringResource(R.string.history_empty_body))
        } else {
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                itemsIndexed(history, key = { _, e -> "${e.timestamp}-${e.expression}" }) { i, entry ->
                    GroupedRow(
                        title = NumberFormatter.group(entry.expression),
                        summary = "= " + NumberFormatter.group(entry.result) + "  ·  " +
                            DateUtils.getRelativeTimeSpanString(entry.timestamp),
                        index = i,
                        count = history.size,
                        onClick = { onReuse(entry, false) },
                        trailing = {
                            TextButton(onClick = { onReuse(entry, true) }) {
                                Text(stringResource(R.string.use_result), textAlign = TextAlign.Center)
                            }
                        },
                    )
                }
            }
        }
    }
}
