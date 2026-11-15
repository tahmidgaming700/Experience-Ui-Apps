package com.experience.setupwizard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceShape

/**
 * Common page layout: a large expressive header (tonal icon, headline, description)
 * above the page body on phones, or beside it on tablets and landscape.
 */
@Composable
fun PageLayout(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val wide = currentWidthClass() != WidthClass.COMPACT
    if (wide) {
        Row(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            PageHeader(icon, title, description, Modifier.weight(0.42f).padding(top = 32.dp, end = 24.dp))
            Box(Modifier.weight(0.58f).fillMaxSize().padding(top = 16.dp)) { content() }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            PageHeader(icon, title, description, Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) { content() }
        }
    }
}

@Composable
fun PageHeader(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A single-choice row in a grouped list: radio + check mark, not colour alone. */
@Composable
fun ChoiceRow(title: String, selected: Boolean, index: Int, count: Int, summary: String? = null, onClick: () -> Unit) {
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(
            Modifier
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
        },
        singleLine = true,
        shape = RoundedCornerShape(percent = 50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

/** Text button styled as an inline link, with a 48dp touch target. */
@Composable
fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

/** Starts an activity, returning false when nothing can handle it. */
fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent); true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
