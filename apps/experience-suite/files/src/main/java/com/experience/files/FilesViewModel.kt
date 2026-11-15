package com.experience.files

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experience.files.data.FileCategory
import com.experience.files.data.StorageRepository
import com.experience.files.data.Volume
import com.experience.files.model.FileItem
import com.experience.files.model.FileOps
import com.experience.files.model.FileSorting
import com.experience.files.model.SortKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Location {
    data object Home : Location
    data class Folder(val dir: File) : Location
    data class Category(val category: FileCategory) : Location
    data object Recent : Location
    data class Search(val root: File, val query: String) : Location
}

/** A running copy/move/delete/extract shown in the progress dialog. */
data class Operation(val title: String, val done: Long, val total: Long, val current: String, val countsItems: Boolean = false)

data class Clip(val files: List<File>, val cut: Boolean)

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)
    private val prefs = app.getSharedPreferences("browser", Context.MODE_PRIVATE)

    val backStack = mutableStateListOf<Location>(Location.Home)
    val location: Location get() = backStack.last()

    var volumes by mutableStateOf<List<Volume>>(emptyList()); private set
    private var rawItems by mutableStateOf<List<FileItem>>(emptyList())
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var recentPreview by mutableStateOf<List<FileItem>>(emptyList()); private set

    var sortKey by mutableStateOf(SortKey.entries.getOrElse(prefs.getInt("sort", 0)) { SortKey.NAME }); private set
    var ascending by mutableStateOf(prefs.getBoolean("asc", true)); private set
    var grid by mutableStateOf(prefs.getBoolean("grid", false)); private set
    var showHidden by mutableStateOf(prefs.getBoolean("hidden", false)); private set

    var selection by mutableStateOf<Set<String>>(emptySet()); private set
    var clip by mutableStateOf<Clip?>(null); private set
    var operation by mutableStateOf<Operation?>(null); private set
    private var opJob: Job? = null
    private var loadJob: Job? = null

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    /** Items of the current location, sorted; Recent keeps newest-first order. */
    val items: List<FileItem>
        get() = if (location == Location.Recent) rawItems else FileSorting.sort(rawItems, sortKey, ascending)

    fun refreshHome() {
        viewModelScope.launch {
            volumes = withContext(Dispatchers.IO) { repo.volumes() }
            recentPreview = repo.recent(12)
        }
    }

    fun navigate(to: Location) {
        selection = emptySet()
        if (to is Location.Folder) {
            // Opening a folder from Home/category starts a fresh trail; inside the browser it extends it.
            val cur = location
            if (cur !is Location.Folder || !FileOps.isInside(to.dir, cur.dir)) {
                while (backStack.size > 1 && backStack.last() !is Location.Home) backStack.removeAt(backStack.lastIndex)
                // Build the breadcrumb trail from the volume root, so Back walks up the tree.
                val root = volumes.firstOrNull { FileOps.isInside(to.dir, it.root) }?.root
                if (root != null) {
                    val chain = generateSequence(to.dir) { it.parentFile }.takeWhile { FileOps.isInside(it, root) }.toList().reversed()
                    chain.forEach { backStack.add(Location.Folder(it)) }
                    load(); return
                }
            }
        }
        if (to == Location.Home) { backStack.clear(); backStack.add(Location.Home); refreshHome(); return }
        backStack.add(to)
        load()
    }

    /** Jumps to a breadcrumb position. */
    fun popTo(index: Int) {
        while (backStack.size - 1 > index) backStack.removeAt(backStack.lastIndex)
        selection = emptySet()
        load()
    }

    fun back(): Boolean {
        if (selection.isNotEmpty()) { selection = emptySet(); return true }
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        if (location == Location.Home) refreshHome() else load()
        return true
    }

    fun load() {
        loadJob?.cancel()
        val loc = location
        error = null
        loading = true
        loadJob = viewModelScope.launch {
            try {
                rawItems = when (loc) {
                    Location.Home -> emptyList()
                    is Location.Folder -> repo.list(loc.dir, showHidden)
                    is Location.Category -> repo.category(loc.category)
                    Location.Recent -> repo.recent()
                    is Location.Search -> repo.search(loc.root, loc.query, showHidden)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rawItems = emptyList()
                error = e.message ?: "Couldn't open this location"
            } finally {
                loading = false
            }
        }
    }

    fun setSort(key: SortKey) {
        if (key == sortKey) ascending = !ascending else { sortKey = key; ascending = key == SortKey.NAME || key == SortKey.TYPE }
        prefs.edit().putInt("sort", sortKey.ordinal).putBoolean("asc", ascending).apply()
    }

    fun toggleGrid() { grid = !grid; prefs.edit().putBoolean("grid", grid).apply() }
    fun toggleHidden() { showHidden = !showHidden; prefs.edit().putBoolean("hidden", showHidden).apply(); load() }

    fun toggleSelect(item: FileItem) {
        selection = if (item.path in selection) selection - item.path else selection + item.path
    }
    fun selectAll() { selection = items.map { it.path }.toSet() }
    fun clearSelection() { selection = emptySet() }
    val selectedFiles: List<File> get() = selection.map { File(it) }

    fun copySelection(cut: Boolean) {
        clip = Clip(selectedFiles, cut)
        val n = selection.size
        selection = emptySet()
        messageChannel.trySend("${if (cut) "Cut" else "Copied"} $n item${if (n == 1) "" else "s"}. Open a folder and paste.")
    }

    fun clearClip() { clip = null }

    fun paste() {
        val c = clip ?: return
        val dest = (location as? Location.Folder)?.dir ?: return
        runOp(if (c.cut) "Moving" else "Copying", total = FileOps.totalSize(c.files)) { report ->
            val result = if (c.cut) FileOps.move(c.files, dest, report) else FileOps.copy(c.files, dest, report)
            if (c.cut) clip = null
            scan(c.files.takeIf { c.cut }.orEmpty() + result)
            "${if (c.cut) "Moved" else "Copied"} ${result.size} item${if (result.size == 1) "" else "s"}"
        }
    }

    fun delete(files: List<File>) {
        selection = emptySet()
        // Collect media paths first so the MediaStore entries can be dropped afterwards.
        runOp("Deleting", total = files.size.toLong(), countsItems = true) { _ ->
            val paths = withContext(Dispatchers.IO) { files.flatMap { f -> f.walkTopDown().filter { it.isFile }.map { it.absolutePath }.take(2000).toList() } }
            FileOps.delete(files) { i, name -> operation = operation?.copy(done = i.toLong(), current = name) }
            scanPaths(paths)
            "Deleted ${files.size} item${if (files.size == 1) "" else "s"}"
        }
    }

    fun extract(zip: File) {
        runOp("Extracting", total = FileOps.zipUncompressedSize(zip)) { report ->
            val dir = FileOps.extractZip(zip, report)
            scan(listOf(dir))
            "Extracted to \"${dir.name}\""
        }
    }

    fun rename(item: FileItem, newName: String) = quick {
        val old = item.file
        val renamed = FileOps.rename(old, newName.trim())
        scan(listOf(renamed)); scanPaths(listOf(old.absolutePath))
        "Renamed to \"${renamed.name}\""
    }

    fun createFolder(name: String) = quick {
        val parent = (location as? Location.Folder)?.dir ?: error("Open a folder first")
        FileOps.createFolder(parent, name.trim())
        "Created \"${name.trim()}\""
    }

    fun search(query: String) {
        val root = (location as? Location.Folder)?.dir
            ?: (location as? Location.Search)?.root
            ?: volumes.firstOrNull()?.root ?: return
        if (location is Location.Search) backStack.removeAt(backStack.lastIndex)
        navigate(Location.Search(root, query))
    }

    fun cancelOperation() { opJob?.cancel() }

    private fun quick(block: suspend () -> String) {
        viewModelScope.launch {
            val msg = try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { e.message ?: "Operation failed" }
            messageChannel.trySend(msg)
            load()
        }
    }

    private fun runOp(title: String, total: Long, countsItems: Boolean = false, block: suspend ((Long, String) -> Unit) -> String) {
        if (opJob?.isActive == true) { messageChannel.trySend("Another operation is still running"); return }
        operation = Operation(title, 0, total, "", countsItems)
        opJob = viewModelScope.launch {
            val msg = try {
                withContext(Dispatchers.IO) {
                    var last = 0L
                    block { done, current ->
                        // Throttle state updates to keep the UI thread free.
                        val now = System.nanoTime()
                        if (now - last > 50_000_000L) { last = now; operation = operation?.copy(done = done, current = current) }
                    }
                }
            } catch (e: CancellationException) {
                "$title cancelled"
            } catch (e: Exception) {
                e.message ?: "$title failed"
            }
            operation = null
            messageChannel.trySend(msg)
            if (location == Location.Home) refreshHome() else load()
        }
    }

    /** Tells MediaStore about new/changed files so Gallery and categories stay accurate. */
    private suspend fun scan(files: List<File>) {
        val paths = withContext(Dispatchers.IO) { files.flatMap { f -> f.walkTopDown().take(2000).map { it.absolutePath }.toList() } }
        scanPaths(paths)
    }

    private fun scanPaths(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
    }
}
