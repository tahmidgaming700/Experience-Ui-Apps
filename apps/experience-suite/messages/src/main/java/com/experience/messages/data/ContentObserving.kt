package com.experience.messages.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Emits once immediately and again whenever [uri] (or a descendant) changes. */
fun ContentResolver.changes(uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { trySend(Unit) }
    }
    trySend(Unit)
    runCatching { registerContentObserver(uri, true, observer) }
    awaitClose { unregisterContentObserver(observer) }
}.conflate()
