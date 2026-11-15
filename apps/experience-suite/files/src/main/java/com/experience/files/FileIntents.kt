package com.experience.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.experience.files.data.Mime
import java.io.File

/** Hands files to other apps through FileProvider content URIs (never file:// URIs). */
object FileIntents {
    const val AUTHORITY = "com.experience.files.fileprovider"

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, AUTHORITY, file)

    fun open(context: Context, file: File, forceChooser: Boolean) {
        val uri = runCatching { uri(context, file) }.getOrElse { toast(context, "Can't share files from this location"); return }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, Mime.of(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val intent = if (forceChooser) Intent.createChooser(view, "Open with") else view
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(context, "No app can open this file")
        }
    }

    fun share(context: Context, files: List<File>) {
        val regular = files.filter { it.isFile }
        if (regular.isEmpty()) { toast(context, "Folders can't be shared"); return }
        val uris = regular.mapNotNull { runCatching { uri(context, it) }.getOrNull() }
        if (uris.isEmpty()) return
        val mimes = regular.map { Mime.of(it.name) }.distinct()
        val type = when {
            mimes.size == 1 -> mimes[0]
            mimes.map { it.substringBefore('/') }.distinct().size == 1 -> mimes[0].substringBefore('/') + "/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        // ClipData carries the read grant for every URI through the chooser.
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, "Share"))
        } catch (_: ActivityNotFoundException) {
            toast(context, "No app can receive this")
        }
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
