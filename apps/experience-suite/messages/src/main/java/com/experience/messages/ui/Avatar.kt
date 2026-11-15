package com.experience.messages.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.experience.design.components.LetterAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache of decoded contact photos keyed by content URI. */
object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun peek(uri: String?): ImageBitmap? = uri?.let { cache.get(it) }

    suspend fun load(context: Context, uri: String): ImageBitmap? = cache.get(uri) ?: withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()?.also { cache.put(uri, it) }
    }
}

/** Contact photo when available, otherwise a [LetterAvatar]. Decorative: the name is shown next to it. */
@Composable
fun ContactAvatar(name: String, photoUri: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val context = LocalContext.current
    var photo by remember(photoUri) { mutableStateOf(PhotoCache.peek(photoUri)) }
    LaunchedEffect(photoUri) {
        if (photo == null && photoUri != null) photo = PhotoCache.load(context, photoUri)
    }
    val bmp = photo
    if (bmp != null) {
        Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
    } else {
        LetterAvatar(name.ifBlank { "#" }, modifier, size)
    }
}
