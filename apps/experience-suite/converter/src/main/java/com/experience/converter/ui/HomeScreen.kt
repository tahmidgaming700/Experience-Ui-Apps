package com.experience.converter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.converter.ConverterViewModel
import com.experience.converter.model.BaseConverter
import com.experience.converter.model.Category
import com.experience.converter.model.NumberFormat
import com.experience.converter.model.RecentConversion
import com.experience.converter.model.UnitCatalog
import com.experience.converter.model.UnitConverter
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape

@Composable
fun HomeScreen(vm: ConverterViewModel, selected: Category?, compactGrid: Boolean) {
    ExperienceScaffold(
        title = "Converter",
        actions = {
            if (vm.recents.isNotEmpty()) {
                IconButton(onClick = vm::clearRecents) { Icon(Icons.Outlined.ClearAll, contentDescription = "Clear recent conversions") }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (compactGrid) 160.dp else 156.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(Category.entries, key = { it.name }) { c -> CategoryCard(c, c == selected) { vm.open(c) } }
            if (vm.recents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Recent") }
                val list = vm.recents.toList()
                list.forEachIndexed { i, r ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "recent-$i-${r.category}-${r.fromId}-${r.toId}") {
                        GroupedRow(
                            title = recentTitle(r),
                            summary = r.category.title,
                            icon = Icons.Outlined.History,
                            index = i, count = list.size,
                            onClick = { vm.openRecent(r) },
                        )
                    }
                }
            }
        }
    }
}

private fun recentTitle(r: RecentConversion): String {
    if (r.category == Category.NUMBER_BASE) {
        val radix = r.fromId.toIntOrNull() ?: 10
        return "${r.input} (${BaseConverter.name(radix)})"
    }
    val from = UnitCatalog.find(r.category, r.fromId) ?: return r.input
    val to = UnitCatalog.find(r.category, r.toId) ?: return r.input
    val v = NumberFormat.parse(r.input) ?: return r.input
    val out = UnitConverter.convert(v, from, to)?.let { NumberFormat.format(it) } ?: "undefined"
    return "${NumberFormat.format(v)} ${from.symbol} = $out ${to.symbol}"
}

@Composable
private fun CategoryCard(c: Category, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .semantics { this.selected = isSelected }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(shape = ExperienceShape.pill, color = if (isSelected) scheme.primary else scheme.secondaryContainer) {
                Icon(
                    c.icon, contentDescription = null,
                    tint = if (isSelected) scheme.onPrimary else scheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.example, style = MaterialTheme.typography.bodySmall, color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
