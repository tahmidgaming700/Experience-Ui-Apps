package com.experience.camera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.experience.camera.engine.MediaHelpers
import com.experience.design.theme.ExperienceShape

/** Confirmation step for ACTION_IMAGE_CAPTURE: retake or hand the photo back. */
@Composable
fun ReviewScreen(jpeg: ByteArray, onRetake: () -> Unit, onAccept: () -> Unit) {
    val bitmap = remember(jpeg) { MediaHelpers.decodeUpright(jpeg, 2048) }
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = "Captured photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onRetake, shape = ExperienceShape.pill, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Replay, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retake")
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = onAccept, shape = ExperienceShape.pill, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use photo")
            }
        }
    }
}
