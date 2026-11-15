package com.experience.files.data

import android.app.usage.StorageStatsManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.experience.files.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class Volume(
    val label: String,
    val root: File,
    val removable: Boolean,
    val primary: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

/** MediaStore-backed file categories. */
enum class FileCategory(val title: String) {
    IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents"), DOWNLOADS("Downloads"), APKS("APKs")
}

object Mime {
    fun of(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /** True if [mime] satisfies a filter such as "image/ *" or a list of them. */
    fun matches(mime: String, filters: List<String>): Boolean = filters.isEmpty() || filters.any { f ->
        f == "*/*" || f == mime || (f.endsWith("/*") && mime.startsWith(f.dropLast(1)))
    }
}

class StorageRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /** Mounted volumes: internal shared storage first, then SD cards / USB drives. */
    fun volumes(): List<Volume> {
        val sm = context.getSystemService(StorageManager::class.java)
        return sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null // API 30: null when not mounted
            val stat = runCatching { StatFs(dir.path) }.getOrNull() ?: return@mapNotNull null
            var total = stat.totalBytes
            var free = stat.availableBytes
            if (v.isPrimary) {
                // StorageStatsManager reports the whole device (system partitions included),
                // matching the figure shown in system Settings.
                runCatching {
                    val ssm = context.getSystemService(StorageStatsManager::class.java)
                    total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                    free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                }
            }
            Volume(v.getDescription(context), dir, v.isRemovable, v.isPrimary, total, free)
        }.sortedByDescending { it.primary }
    }

    suspend fun list(dir: File, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        (dir.listFiles() ?: throw java.io.IOException("Can't read ${dir.name}"))
            .filter { showHidden || !it.name.startsWith(".") }
            .map { FileItem.of(it) }
    }

    suspend fun category(cat: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        if (cat == FileCategory.DOWNLOADS) {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return@withContext dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.map { FileItem.of(it) }.toList()
        }
        val (selection, args) = when (cat) {
            FileCategory.IMAGES -> "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            FileCategory.VIDEOS -> "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            FileCategory.AUDIO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            FileCategory.DOCUMENTS -> {
                val types = DOCUMENT_MIMES
                "(${MediaStore.Files.FileColumns.MIME_TYPE} IN (${types.joinToString { "?" }}) OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%')" to types.toTypedArray()
            }
            FileCategory.APKS -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" to arrayOf("%.apk")
            FileCategory.DOWNLOADS -> error("handled above")
        }
        queryFiles(selection, args, limit = 0)
    }

    /** Most recently modified files across shared storage. */
    suspend fun recent(limit: Int = 60): List<FileItem> = withContext(Dispatchers.IO) {
        queryFiles("${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%'", emptyArray(), limit)
    }

    /**
     * MediaStore.Files across every volume. With All-files access Android 11 returns
     * every indexed file, including the DATA path that file operations need.
     */
    @Suppress("DEPRECATION") // DATA is the documented way to get a path with All-files access.
    private fun queryFiles(selection: String, args: Array<String>, limit: Int): List<FileItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            if (limit > 0) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val out = mutableListOf<FileItem>()
        runCatching {
            resolver.query(uri, projection, query, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    val f = File(path)
                    if (!f.isFile) continue // stale index entry
                    out += FileItem(
                        name = c.getString(1) ?: f.name,
                        path = path,
                        isDirectory = false,
                        size = c.getLong(2),
                        lastModified = c.getLong(3) * 1000,
                    )
                }
            }
        }
        return out
    }

    /** Case-insensitive name search below [root], capped at [max] results. */
    suspend fun search(root: File, query: String, showHidden: Boolean, max: Int = 500): List<FileItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val out = mutableListOf<FileItem>()
        for (f in root.walkTopDown().onEnter { showHidden || !it.name.startsWith(".") || it == root }) {
            currentCoroutineContext().ensureActive()
            if (f != root && f.name.contains(q, ignoreCase = true) && (showHidden || !f.name.startsWith("."))) {
                out += FileItem.of(f)
                if (out.size >= max) break
            }
        }
        out
    }

    companion object {
        val DOCUMENT_MIMES = listOf(
            "application/pdf", "application/msword", "application/rtf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation", "application/epub+zip",
        )
    }
}
