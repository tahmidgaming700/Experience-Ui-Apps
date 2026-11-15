package com.experience.camera.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.experience.camera.model.MediaNaming
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Rule-of-thirds grid. */
@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.45f)
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3; val y = size.height * i / 3
            drawLine(c, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(c, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/** Focus ring at [point]: shrinks into place with a spring, then fades out. [key] restarts it. */
@Composable
fun FocusRing(point: Offset, key: Int) {
    val scale = remember(key) { Animatable(1.4f) }
    val alpha = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        launch { scale.animateTo(1f, ExperienceMotion.fastSpatial()) }
        delay(1200)
        alpha.animateTo(0f, ExperienceMotion.slowEffects())
    }
    Canvas(Modifier.fillMaxSize()) {
        val r = 36.dp.toPx() * scale.value
        drawCircle(Color.White.copy(alpha = alpha.value), radius = r, center = point, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White.copy(alpha = alpha.value * 0.8f), radius = 3.dp.toPx(), center = point)
    }
}

/** Brief dark "shutter blink" over the preview when a photo is taken. */
@Composable
fun ShutterFlash(alpha: Float) {
    if (alpha > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}

/** Large self-timer countdown. */
@Composable
fun Countdown(seconds: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "$seconds",
            color = Color.White,
            fontSize = 120.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Taking photo in $seconds" },
        )
    }
}

/** Recording indicator: red dot plus "REC" text and elapsed time (not colour alone). */
@Composable
fun RecordingPill(elapsedNanos: Long, paused: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), ExperienceShape.pill)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(if (paused) Color.White else Color(0xFFE53935), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            (if (paused) "PAUSED " else "REC ") + MediaNaming.elapsed(elapsedNanos),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
