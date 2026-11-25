package com.experience.setupwizard.ui.pages

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.experience.setupwizard.R

/**
 * The branded welcome clip (res/raw/welcome.mp4), auto-playing, looping and muted via the
 * platform [VideoView] (no extra playback dependency; works from Android 9 up). If playback
 * cannot start for any reason, [fallback] is shown instead so the welcome screen always
 * renders something.
 */
@Composable
fun WelcomeVideo(modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val uri = remember { Uri.parse("android.resource://${context.packageName}/${R.raw.welcome}") }

    Box(modifier) {
        if (!failed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            runCatching { mp.setVolume(0f, 0f) }
                            start()
                        }
                        setOnErrorListener { _: MediaPlayer?, _: Int, _: Int ->
                            failed = true
                            true
                        }
                    }
                },
                update = { if (!it.isPlaying) runCatching { it.start() } },
            )
        } else {
            fallback()
        }
    }
}
