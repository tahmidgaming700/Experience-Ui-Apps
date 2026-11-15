package com.experience.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experience.design.ExperienceActivity
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.gallery.model.MediaItem
import com.experience.gallery.model.Timeline
import com.experience.gallery.ui.AlbumsGrid
import com.experience.gallery.ui.PhotoGrid
import com.experience.gallery.ui.Viewer
import kotlinx.coroutines.launch

/** How the activity was started. */
sealed interface Mode {
    data object Browse : Mode
    data class View(val uri: Uri, val type: String?) : Mode
    data class Pick(val filters: List<String>, val multiple: Boolean) : Mode
}

class MainActivity : ExperienceActivity() {

    private val mode: Mode by lazy {
        val i = intent
        when (i?.action) {
            Intent.ACTION_VIEW, "com.android.camera.action.REVIEW" -> i.data?.let { Mode.View(it, i.type) } ?: Mode.Browse
            Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> Mode.Pick(
                // ACTION_PICK often carries only a MediaStore dir URI: resolve its type from the provider.
                i.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() ?: listOfNotNull(i.resolveType(contentResolver)),
                i.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
            )
            else -> Mode.Browse
        }
    }

    @Composable
    override fun Content() {
        val vm: GalleryViewModel = viewModel()
        when (val m = mode) {
            is Mode.View -> {
                // Content from another app is shown on its own; it needs no storage permission.
                LaunchedEffect(m.uri) { vm.openExternal(m.uri, m.type) }
                val v = vm.viewer
                if (v != null) Viewer(v.first, v.second, {}, onClose = ::finish, onDelete = {}, onFavorite = {}, onPick = null)
                else Box(Modifier.fillMaxSize())
            }
            else -> PermissionGate(
                permissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                rationaleTitle = "See your photos and videos",
                rationale = "Experience Gallery needs access to photos and videos on this device to show them.",
            ) {
                LaunchedEffect(Unit) { vm.start() }
                GalleryApp(vm, m as? Mode.Pick)
            }
        }
    }

    private fun deliver(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val result = Intent().setData(uris[0]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        result.clipData = MediaActions.clip(uris)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    @Composable
    private fun GalleryApp(vm: GalleryViewModel, pick: Mode.Pick?) {
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        // MediaStore consent dialogs (Android 11): delete and favourite.
        val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) { vm.clearSelection(); vm.reload() }
            else scope.launch { snackbar.showSnackbar("Nothing was changed") }
        }
        fun delete(items: List<MediaItem>) = MediaActions.deleteRequest(context, items)?.let { consent.launch(it) }
            ?: scope.launch { snackbar.showSnackbar("These items can't be deleted") }
        fun favorite(items: List<MediaItem>, value: Boolean) = MediaActions.favoriteRequest(context, items, value)?.let { consent.launch(it) }

        val filtered: (List<MediaItem>) -> List<MediaItem> = { l -> if (pick == null) l else l.filter { Timeline.pickerAccepts(it, pick.filters) } }
        val visible = filtered(vm.itemsOf(vm.album))
        val selected = visible.filter { it.uri in vm.selection }
        val wide = currentWidthClass() != WidthClass.COMPACT

        BackHandler(enabled = vm.selection.isNotEmpty() || vm.album != null) {
            if (vm.selection.isNotEmpty()) vm.clearSelection() else vm.album = null
        }

        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (wide && vm.album == null) {
                    NavigationRail(Modifier.padding(top = 24.dp)) {
                        tabs.forEach { (tab, icon, label) ->
                            NavigationRailItem(selected = vm.tab == tab, onClick = { vm.tab = tab }, icon = { Icon(icon, null) }, label = { Text(label) })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    val title = when {
                        vm.selection.isNotEmpty() -> "${vm.selection.size} selected"
                        vm.album != null -> vm.album!!.name
                        pick != null -> "Choose " + if (pick.filters.all { it.startsWith("video") }) "a video" else "media"
                        vm.tab == Tab.ALBUMS -> "Albums"
                        else -> "Photos"
                    }
                    ExperienceScaffold(
                        title = title,
                        snackbarHostState = snackbar,
                        onBack = when {
                            vm.selection.isNotEmpty() -> vm::clearSelection
                            vm.album != null -> ({ vm.album = null })
                            pick != null -> ({ setResult(Activity.RESULT_CANCELED); finish() })
                            else -> null
                        },
                        actions = {
                            if (vm.selection.isNotEmpty()) {
                                IconButton(onClick = { vm.selectAll(visible) }) { Icon(Icons.Outlined.SelectAll, "Select all") }
                                if (pick != null) {
                                    TextButton(onClick = { deliver(selected) }) { Text("Select") }
                                } else {
                                    IconButton(onClick = { MediaActions.share(context, selected) }) { Icon(Icons.Filled.Share, "Share selected") }
                                    IconButton(onClick = { favorite(selected, !selected.all { it.favorite }) }) { Icon(Icons.Filled.Favorite, if (selected.all { it.favorite }) "Remove selected from favourites" else "Add selected to favourites") }
                                    IconButton(onClick = { delete(selected) }) { Icon(Icons.Filled.Delete, "Delete selected") }
                                }
                                IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") }
                            }
                        },
                        bottomBar = {
                            if (!wide && vm.album == null && vm.selection.isEmpty()) {
                                NavigationBar {
                                    tabs.forEach { (tab, icon, label) ->
                                        NavigationBarItem(selected = vm.tab == tab, onClick = { vm.tab = tab }, icon = { Icon(icon, null) }, label = { Text(label) })
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        val onOpen: (Int) -> Unit = { index ->
                            if (pick != null) deliver(listOf(visible[index])) else vm.openViewer(visible, index)
                        }
                        val onLong: (MediaItem) -> Unit = { if (pick == null || pick.multiple) vm.toggleSelect(it) }
                        when {
                            !vm.loaded -> Box(Modifier.fillMaxSize())
                            vm.album == null && vm.tab == Tab.ALBUMS -> AlbumsGrid(filtered(vm.items), vm.albums.filter { a -> filtered(vm.itemsOf(a)).isNotEmpty() }, padding) { vm.album = it }
                            visible.isEmpty() -> Column(Modifier.padding(padding)) {
                                EmptyState(Icons.Outlined.PhotoLibrary, "No photos or videos", "Pictures you take or download appear here.")
                            }
                            else -> PhotoGrid(visible, vm.selection, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 8.dp), onOpen, onLong)
                        }
                    }
                }
            }
            vm.viewer?.let { (list, index) ->
                Viewer(
                    list, index, onPageChange = vm::setViewerIndex, onClose = vm::closeViewer,
                    onDelete = { delete(listOf(it)) }, onFavorite = { favorite(listOf(it), !it.favorite) },
                    onPick = null,
                )
            }
        }
    }

    private val tabs = listOf(
        Triple(Tab.PHOTOS, Icons.Outlined.PhotoLibrary, "Photos"),
        Triple(Tab.ALBUMS, Icons.Outlined.PhotoAlbum, "Albums"),
    )
}
