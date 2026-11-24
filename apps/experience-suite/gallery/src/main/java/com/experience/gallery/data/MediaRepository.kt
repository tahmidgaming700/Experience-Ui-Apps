package com.experience.gallery.data

import android.os.Build
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import com.experience.gallery.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /** All images and videos on external volumes, newest first. */
    @Suppress("DEPRECATION") // DATA is only shown as the informational path.
    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
            )
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
        }
        val out = ArrayList<MediaItem>()
        resolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val video = c.getInt(13) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(3)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(1) ?: "",
                    mime = c.getString(2) ?: if (video) "video/*" else "image/*",
                    timestamp = if (taken > 0) taken else c.getLong(4) * 1000,
                    size = c.getLong(5),
                    width = c.getInt(6),
                    height = c.getInt(7),
                    durationMs = c.getLong(8),
                    bucketId = c.getString(9) ?: "",
                    bucketName = c.getString(10) ?: "",
                    path = c.getString(11),
                    favorite = c.getInt(12) == 1,
                )
            }
        }
        out.sortByDescending { it.timestamp }
        out
    }

    /** Describes a content:// or file:// URI handed to us by another app (ACTION_VIEW). */
    suspend fun describe(uri: Uri, typeHint: String?): MediaItem = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "Media"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val mime = typeHint?.takeIf { it.contains('/') && !it.endsWith("/*") }
            ?: runCatching { resolver.getType(uri) }.getOrNull()
            ?: typeHint ?: "image/*"
        MediaItem(
            id = -1, uri = uri, name = name, mime = mime, timestamp = System.currentTimeMillis(), size = size,
            width = 0, height = 0, durationMs = 0, bucketId = "", bucketName = "", path = null, favorite = false,
            inMediaStore = uri.authority == MediaStore.AUTHORITY,
        )
    }
}

/**
 * Thumbnail loading through ContentResolver.loadThumbnail (API 29+), which uses
 * MediaStore's cached thumbnails, with an in-memory LRU sized to 1/8 of the heap.
 */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun key(uri: Uri, px: Int) = "$uri@$px"
    fun cached(uri: Uri, px: Int): Bitmap? = cache.get(key(uri, px))

    suspend fun load(resolver: ContentResolver, uri: Uri, px: Int): Bitmap? {
        cached(uri, px)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Bitmap?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val result = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.loadThumbnail(uri, Size(px, px), signal)
                    else resolver.openInputStream(uri)?.use { @Suppress("DEPRECATION") android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }
                }.getOrNull()
                if (cont.isActive) cont.resumeWith(Result.success(result))
            }
        }
        if (bmp != null) cache.put(key(uri, px), bmp)
        return bmp
    }
}
