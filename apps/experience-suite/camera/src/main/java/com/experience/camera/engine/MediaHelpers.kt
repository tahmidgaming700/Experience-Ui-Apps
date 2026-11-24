package com.experience.camera.engine

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** MediaStore and JPEG helpers (run off the main thread). */
object MediaHelpers {

    @Suppress("DEPRECATION")
    suspend fun thumbnail(context: Context, uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }.getOrNull()
        } else {
            // Android 9: ContentResolver.loadThumbnail did not exist; downscale the full image.
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }.getOrNull()
        }
    }

    /**
     * The newest photo or video in DCIM/Camera visible to us. With scoped storage and
     * no storage permission this is the newest item this app created.
     */
    suspend fun latestCapture(context: Context): SavedMedia? = withContext(Dispatchers.IO) {
        fun newest(collection: Uri, mime: String): Pair<SavedMedia, Long>? = runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("DCIM/Camera%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                if (c.moveToFirst()) SavedMedia(ContentUris.withAppendedId(collection, c.getLong(0)), mime) to c.getLong(1) else null
            }
        }.getOrNull()
        listOfNotNull(
            newest(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg"),
            newest(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4"),
        ).maxByOrNull { it.second }?.first
    }

    /** Rotation from the JPEG's EXIF orientation tag. */
    fun exifRotation(jpeg: ByteArray): Int = runCatching {
        when (ExifInterface(ByteArrayInputStream(jpeg)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    /** Decodes [jpeg] upright, downsampled so the longer side is at most [maxSide]. */
    fun decodeUpright(jpeg: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = (maxSide.toFloat() / maxOf(raw.width, raw.height)).coerceAtMost(1f)
        val m = Matrix().apply { postScale(scale, scale); postRotate(exifRotation(jpeg).toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    /** Writes the JPEG to the caller's EXTRA_OUTPUT uri. */
    suspend fun writeTo(context: Context, uri: Uri, jpeg: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(jpeg) } ?: error("no stream")
        }.isSuccess
    }
}
