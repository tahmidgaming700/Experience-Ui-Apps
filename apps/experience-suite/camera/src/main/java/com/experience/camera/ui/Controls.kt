package com.experience.camera.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.experience.camera.model.CaptureMode
import com.experience.camera.model.ZoomPresets
import com.experience.design.theme.ExperienceMotion
import com.experience.design.theme.ExperienceShape

/** Round translucent icon button whose glyph rotates with the device. */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, rotation: Float, onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 48) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.rotate(rotation))
    }
}

/**
 * The shutter: white ring with a white disc (photo), red disc (video idle) or red
 * rounded square (recording). Springs smaller while pressed.
 */
@Composable
fun ShutterButton(mode: CaptureMode, recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, ExperienceMotion.fastSpatial(), label = "press")
    val inner by animateDpAsState(if (recording) 30.dp else 62.dp, ExperienceMotion.fastSpatial(), label = "inner")
    val corner by animateDpAsState(if (recording) 8.dp else 31.dp, ExperienceMotion.fastSpatial(), label = "corner")
    val color by animateColorAsState(if (mode == CaptureMode.VIDEO) Color(0xFFE53935) else Color.White, ExperienceMotion.defaultEffects(), label = "color")
    val label = when {
        recording -> "Stop recording"
        mode == CaptureMode.VIDEO -> "Start recording"
        else -> "Take photo"
    }
    Box(
        Modifier
            .size(80.dp)
            .scale(scale)
            .border(BorderStroke(4.dp, Color.White), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).background(if (enabled) color else color.copy(alpha = 0.4f), RoundedCornerShape(corner)))
    }
}

/** Last-capture thumbnail; opens it in the gallery / viewer. */
@Composable
fun ThumbnailButton(thumbnail: Bitmap?, isVideo: Boolean, rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(2.dp, Color.White, CircleShape)
            .clickable(enabled = thumbnail != null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isVideo) "Open last video" else "Open last photo" },
    ) {
        if (thumbnail != null) {
            Image(
                thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).rotate(rotation),
            )
        }
    }
}

/** Photo / Video switch; the selected mode is a filled pill (and announced as selected). */
@Composable
fun ModeSwitch(mode: CaptureMode, enabled: Boolean, onMode: (CaptureMode) -> Unit) {
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.35f), ExperienceShape.pill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(CaptureMode.PHOTO to "Photo", CaptureMode.VIDEO to "Video").forEach { (m, label) ->
            val selected = m == mode
            Surface(
                shape = ExperienceShape.pill,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                modifier = Modifier.selectable(selected, enabled = enabled, role = Role.Tab) { onMode(m) },
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    }
}

/** Zoom presets; the active chip shows the exact ratio. */
@Composable
fun ZoomChips(ratio: Float, min: Float, max: Float, rotation: Float, onZoom: (Float) -> Unit) {
    val presets = ZoomPresets.presets(min, max)
    if (presets.size < 2) return
    val active = ZoomPresets.activeIndex(presets, ratio)
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.35f), ExperienceShape.pill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presets.forEachIndexed { i, p ->
            val selected = i == active
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                    .selectable(selected, role = Role.Button) { onZoom(p) }
                    .semantics { contentDescription = "Zoom ${ZoomPresets.label(p)}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selected) ZoomPresets.label(ratio) else ZoomPresets.label(p),
                    color = if (selected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}
