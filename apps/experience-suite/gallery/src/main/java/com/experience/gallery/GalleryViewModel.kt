package com.experience.gallery

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.gallery.data.MediaRepository
import com.experience.gallery.model.Album
import com.experience.gallery.model.MediaItem
import com.experience.gallery.model.Timeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab { PHOTOS, ALBUMS }

/** Virtual album id for favourites (MediaStore IS_FAVORITE). */
const val FAVORITES_ID = "__favorites__"
const val VIDEOS_ID = "__videos__"

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)

    var items by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var tab by mutableStateOf(Tab.PHOTOS)
    var album by mutableStateOf<Album?>(null)
    var selection by mutableStateOf<Set<Uri>>(emptySet()); private set

    /** Items currently in the viewer, and the start page; null when the viewer is closed. */
    var viewer by mutableStateOf<Pair<List<MediaItem>, Int>?>(null); private set

    val albums: List<Album> get() = Timeline.albums(items)

    fun itemsOf(album: Album?): List<MediaItem> = when (album?.bucketId) {
        null -> items
        FAVORITES_ID -> items.filter { it.favorite }
        VIDEOS_ID -> items.filter { it.isVideo }
        else -> items.filter { it.bucketId == album.bucketId }
    }

    private var observer: ContentObserver? = null
    private var reloadJob: Job? = null

    /** Loads media and keeps it fresh through a MediaStore ContentObserver. */
    fun start() {
        if (observer == null) {
            val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = scheduleReload()
            }
            getApplication<Application>().contentResolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, obs)
            // Images/Video tables notify on their own URIs too.
            getApplication<Application>().contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
            getApplication<Application>().contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, obs)
            observer = obs
        }
        reload()
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        // MediaStore fires bursts of changes (e.g. while a camera writes a file): debounce.
        reloadJob = viewModelScope.launch { delay(400); reload() }
    }

    fun reload() {
        viewModelScope.launch {
            val list = runCatching { repo.loadAll() }.getOrDefault(items)
            items = list
            loaded = true
            // Keep the viewer pointing at fresh rows (favourite flag) and drop deleted ones.
            viewer?.let { (old, index) ->
                if (old.all { it.inMediaStore }) {
                    val byUri = list.associateBy { it.uri }
                    val current = old.getOrNull(index)
                    val fresh = old.mapNotNull { byUri[it.uri] }
                    viewer = if (fresh.isEmpty()) null else fresh to (fresh.indexOfFirst { it.uri == current?.uri }.takeIf { it >= 0 } ?: index.coerceAtMost(fresh.lastIndex))
                }
            }
            selection = selection.filter { u -> list.any { it.uri == u } }.toSet()
        }
    }

    fun openViewer(list: List<MediaItem>, index: Int) { viewer = list to index }
    fun closeViewer() { viewer = null }
    fun setViewerIndex(index: Int) { viewer = viewer?.let { it.first to index } }

    fun openExternal(uri: Uri, type: String?) {
        viewModelScope.launch { viewer = listOf(repo.describe(uri, type)) to 0 }
    }

    fun toggleSelect(item: MediaItem) { selection = if (item.uri in selection) selection - item.uri else selection + item.uri }
    fun clearSelection() { selection = emptySet() }
    fun selectAll(list: List<MediaItem>) { selection = list.map { it.uri }.toSet() }

    override fun onCleared() {
        observer?.let { getApplication<Application>().contentResolver.unregisterContentObserver(it) }
    }
}
