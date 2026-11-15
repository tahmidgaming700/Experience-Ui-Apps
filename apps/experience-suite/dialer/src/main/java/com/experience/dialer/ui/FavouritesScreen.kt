package com.experience.dialer.ui

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.theme.ExperienceShape
import com.experience.dialer.DialerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavouritesScreen(vm: DialerViewModel, bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    ExperienceScaffold(title = "Favourites", bottomBar = bottomBar) { padding ->
        Box(Modifier.padding(padding)) {
            PermissionGate(listOf(Manifest.permission.READ_CONTACTS), "Favourites", "Allow access to contacts to see your starred contacts.") {
                LaunchedEffect(Unit) { vm.refresh() }
                val call = rememberCallAction()
                val contacts by vm.contacts.collectAsStateWithLifecycle()
                // One tile per starred contact, using its first (primary-sorted) number.
                val favourites = remember(contacts) { contacts.filter { it.starred }.distinctBy { it.contactId } }
                LazyVerticalGrid(
                    GridCells.Adaptive(112.dp),
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { DialerRoleBanner() }
                    if (favourites.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(Icons.Filled.StarBorder, "No favourites yet", "Star contacts in Contacts to call them from here.")
                        }
                    }
                    items(favourites, key = { it.contactId }) { c ->
                        Surface(
                            shape = ExperienceShape.hero,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.combinedClickable(
                                onClickLabel = "Call ${c.name}",
                                onClick = { call(c.number) },
                                onLongClickLabel = "Open contact",
                                onLongClick = { context.safeStart(Intent(Intent.ACTION_VIEW, c.lookupUri)) },
                            ),
                        ) {
                            Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                ContactAvatar(c.name, c.photoUri, size = 72.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                c.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}
