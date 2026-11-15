package com.experience.design.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceShape

/** Section heading above a grouped list. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Tonal round icon badge used at the start of list rows. */
@Composable
fun IconBadge(icon: ImageVector, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp)) }
    }
}

/**
 * One row in a grouped (segmented) Expressive list. [index]/[count] choose the
 * corner treatment so a group reads as one rounded card with fine separators.
 */
@Composable
fun GroupedRow(
    title: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = ExperienceShape.grouped(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .heightIn(min = 64.dp),
    ) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconBadge(icon)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

/** Grouped row with a switch; the whole row toggles (large touch target). */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, index: Int, count: Int, summary: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    GroupedRow(
        title = title, summary = summary, icon = icon, index = index, count = count,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled) },
    )
}

/** Large tonal hero card with headline, used at the top of hub screens. */
@Composable
fun HeroCard(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    Surface(
        shape = ExperienceShape.hero,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
        }
    }
}

/** Centered empty / error state. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = ExperienceShape.hero, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body != null) {
            Spacer(Modifier.size(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.size(16.dp)); action() }
    }
}

/** Circular letter avatar for contacts and conversations. */
@Composable
fun LetterAvatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    Surface(shape = CircleShape, color = bg, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "#", color = fg, style = MaterialTheme.typography.titleMedium)
        }
    }
}
