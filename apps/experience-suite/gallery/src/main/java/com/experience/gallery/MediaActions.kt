package com.experience.gallery

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import com.experience.gallery.model.MediaItem

/** Intents handed to other apps, and the API 30 MediaStore consent requests. */
object MediaActions {
    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        val mimes = items.map { it.mime }.distinct()
        send.type = when {
            mimes.size == 1 -> mimes[0]
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        send.clipData = clip(uris)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, Intent.createChooser(send, "Share"))
    }

    fun edit(context: Context, item: MediaItem) {
        val edit = Intent(Intent.ACTION_EDIT).setDataAndType(item.uri, item.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        launch(context, Intent.createChooser(edit, "Edit with"))
    }

    /** "Use as" / Set as wallpaper: ACTION_ATTACH_DATA lets the wallpaper picker (or contacts) take the image. */
    fun setAs(context: Context, item: MediaItem) {
        val attach = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(item.uri, item.mime)
            .putExtra("mimeType", item.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, Intent.createChooser(attach, "Set as"))
    }

    /** System dialog asking the user to confirm deletion (Android 11 scoped storage). */
    fun deleteRequest(context: Context, items: List<MediaItem>): IntentSenderRequest? = runCatching {
        val pi = MediaStore.createDeleteRequest(context.contentResolver, items.filter { it.inMediaStore }.map { it.uri })
        IntentSenderRequest.Builder(pi.intentSender).build()
    }.getOrNull()

    fun favoriteRequest(context: Context, items: List<MediaItem>, favorite: Boolean): IntentSenderRequest? = runCatching {
        val pi = MediaStore.createFavoriteRequest(context.contentResolver, items.filter { it.inMediaStore }.map { it.uri }, favorite)
        IntentSenderRequest.Builder(pi.intentSender).build()
    }.getOrNull()

    fun clip(uris: List<Uri>): ClipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }

    private fun launch(context: Context, intent: Intent) {
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can handle this", Toast.LENGTH_SHORT).show()
        }
    }
}
