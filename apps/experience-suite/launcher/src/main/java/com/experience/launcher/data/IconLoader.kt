package com.experience.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.experience.launcher.model.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders app icons to bitmaps with one consistent rounded shape and caches them.
 *
 * Adaptive icons are drawn layer by layer: the layers are 108dp with a 72dp visible
 * viewport, so each layer is drawn at 150% of the icon size, centred, and clipped to
 * the Experience shape (independent of the device's icon-shape overlay). Legacy
 * icons are scaled into the same shape on a white plate. Work-profile icons receive
 * the system work badge via [PackageManager.getUserBadgedIcon].
 */
class IconLoader(private val context: Context, private val repository: AppRepository) {

    private val pm = context.packageManager
    private val res = context.resources
    private val iconSizePx = (ICON_DP * res.displayMetrics.density).toInt()
    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Incremented whenever cached icons are dropped, so the UI reloads them. */
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun peek(key: AppKey): ImageBitmap? = cache.get(key.encode())

    suspend fun load(app: LauncherApp): ImageBitmap? {
        cache.get(app.key.encode())?.let { return it }
        return withContext(Dispatchers.IO) {
            val info = repository.infoFor(app.key) ?: return@withContext null
            val drawable = runCatching { info.getIcon(res.displayMetrics.densityDpi) }.getOrNull()
                ?: return@withContext null
            val bitmap = badge(shape(drawable, iconSizePx), app.user, app.isWork)
            bitmap.asImageBitmap().also { cache.put(app.key.encode(), it) }
        }
    }

    suspend fun loadDrawable(id: String, drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        cache.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            shape(drawable, (SHORTCUT_DP * res.displayMetrics.density).toInt()).asImageBitmap().also { cache.put(id, it) }
        }
    }

    /** Forgets cached icons of [packageName] (or all icons for an empty name). */
    fun invalidate(packageName: String) {
        if (packageName.isEmpty()) {
            cache.evictAll()
        } else {
            cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { cache.remove(it) }
        }
        version.value++
    }

    private fun badge(icon: Bitmap, user: UserHandle, isWork: Boolean): Bitmap {
        if (!isWork) return icon
        val original = BitmapDrawable(res, icon)
        val badged = runCatching { pm.getUserBadgedIcon(original, user) }.getOrNull()
        if (badged == null || badged === original) return icon
        return Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            badged.setBounds(0, 0, icon.width, icon.height)
            badged.draw(canvas)
        }
    }

    companion object {
        const val ICON_DP = 60
        const val SHORTCUT_DP = 40
        /** Corner radius as a fraction of the icon size: a soft "squircle-like" square. */
        const val CORNER_FRACTION = 0.32f

        private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtMost(48 * 1024 * 1024)

        fun shape(drawable: Drawable, size: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = size * CORNER_FRACTION
            val path = Path().apply { addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, Path.Direction.CW) }
            canvas.clipPath(path)
            if (drawable is AdaptiveIconDrawable) {
                val inset = size / 4
                for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
                    layer.setBounds(-inset, -inset, size + inset, size + inset)
                    layer.draw(canvas)
                }
            } else {
                canvas.drawColor(Color.WHITE)
                val pad = (size * 0.14f).toInt()
                drawable.setBounds(pad, pad, size - pad, size - pad)
                drawable.draw(canvas)
            }
            return bitmap
        }
    }
}
