package com.experience.dialer.incall

import android.content.Intent
import android.telecom.CallAudioState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.design.components.GroupedRow
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import com.experience.dialer.MainActivity
import com.experience.dialer.data.DurationFormat
import com.experience.dialer.telecom.CallPlacer
import com.experience.dialer.ui.ContactAvatar
import com.experience.dialer.ui.Dialpad
import kotlinx.coroutines.delay

@Composable
fun InCallScreen(onFinished: () -> Unit) {
    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val audio by CallManager.audioState.collectAsStateWithLifecycle()
    val canAdd by CallManager.canAddCall.collectAsStateWithLifecycle()
    val lastEnded by CallManager.lastEnded.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val top = calls.filter { it.parentId == null && !it.isEnded }
    val background = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surface))
    Box(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (top.isEmpty()) {
            EndedView(lastEnded)
            LaunchedEffect(Unit) {
                delay(1500)
                CallManager.clearLastEnded()
                onFinished()
            }
            return@Box
        }
        val ringing = top.firstOrNull { it.isRinging }
        val primary = ringing ?: top.firstOrNull { it.isActive } ?: top.firstOrNull { it.isDialing || it.needsAccount } ?: top.first()
        val others = top.filter { it.id != primary.id }
        val children = calls.filter { it.parentId == primary.id }

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            others.forEach { other -> OtherCallBar(other, now, onSwap = { CallManager.hold(other.id, false) }) }
            Spacer(Modifier.height(32.dp))
            CallerHeader(primary, now, children)
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = when {
                    primary.needsAccount -> 0
                    ringing != null -> 1
                    else -> 2
                },
                label = "controls",
            ) { mode ->
                when (mode) {
                    0 -> AccountPicker(primary)
                    1 -> IncomingControls(primary, hasOtherCalls = others.isNotEmpty())
                    else -> OngoingControls(primary, others, children, audio, canAdd)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EndedView(ended: CallSnapshot?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (ended != null) {
            ContactAvatar(ended.title, ended.photoUri, size = 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(ended.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
        Text(ended?.disconnectReason ?: "Call ended", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CallerHeader(call: CallSnapshot, now: Long, children: List<CallSnapshot>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (call.isConference) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(112.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Groups, contentDescription = null, Modifier.size(56.dp)) }
            }
        } else {
            ContactAvatar(call.title, call.photoUri, size = 112.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(call.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = when {
            call.isConference -> children.joinToString(", ") { it.title }
            call.name != null && !call.numberHidden -> listOfNotNull(call.numberLabel, call.number).joinToString(" · ")
            else -> null
        }
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        val status = if (call.isActive && call.connectTimeMillis > 0) DurationFormat.clock((now - call.connectTimeMillis) / 1000) else CallSnapshot.stateLabel(call.state)
        Surface(shape = ExperienceShape.pill, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(status, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OtherCallBar(call: CallSnapshot, now: Long, onSwap: () -> Unit) {
    Surface(shape = ExperienceShape.listSingle, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (call.isHeld) Icons.Filled.Pause else Icons.Filled.Call, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(call.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val label = if (call.isActive && call.connectTimeMillis > 0) DurationFormat.clock((now - call.connectTimeMillis) / 1000) else CallSnapshot.stateLabel(call.state)
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            if (call.isHeld) TextButton(onClick = onSwap, modifier = Modifier.heightIn(min = 48.dp)) { Text("Swap") }
        }
    }
}

@Composable
private fun AccountPicker(call: CallSnapshot) {
    val context = LocalContext.current
    val accounts = remember(call.selectableAccounts) {
        val all = CallPlacer(context).accounts()
        if (call.selectableAccounts.isEmpty()) all else all.filter { it.handle in call.selectableAccounts }
    }
    var remember by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
        SectionHeader("Call with")
        accounts.forEachIndexed { i, a ->
            GroupedRow(a.label, i, accounts.size, summary = a.detail, icon = Icons.Filled.SimCard, onClick = { CallManager.selectAccount(call.id, a.handle, remember) })
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(remember, role = Role.Checkbox) { remember = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = remember, onCheckedChange = null)
            Text("Remember this choice", Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = { CallManager.hangup(call.id) }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Cancel call") }
    }
}

private val defaultResponses = listOf(
    "Can't talk now. What's up?",
    "I'll call you right back.",
    "I'll call you later.",
    "Can't talk now. Call me later?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomingControls(call: CallSnapshot, hasOtherCalls: Boolean) {
    var replies by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (!call.numberHidden && !call.number.isNullOrBlank()) {
            TextButton(onClick = { replies = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reply with message")
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            BigRoundButton(Icons.Filled.CallEnd, "Decline", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError) { CallManager.reject(call.id) }
            if (hasOtherCalls) {
                BigRoundButton(Icons.Filled.CallEnd, "End & answer", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary) { CallManager.endOthersAndAnswer(call.id) }
                BigRoundButton(Icons.Filled.PhonePaused, "Hold & answer", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { CallManager.answer(call.id) }
            } else {
                BigRoundButton(Icons.Filled.Call, "Answer", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { CallManager.answer(call.id) }
            }
        }
    }
    if (replies) {
        var custom by remember { mutableStateOf("") }
        val options = call.cannedResponses.ifEmpty { defaultResponses }
        ModalBottomSheet(onDismissRequest = { replies = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text("Decline and send", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                options.forEachIndexed { i, text ->
                    GroupedRow(text, i, options.size, onClick = { replies = false; CallManager.reject(call.id, text) })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it },
                    label = { Text("Write your own…") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { if (custom.isNotBlank()) { replies = false; CallManager.reject(call.id, custom.trim()) } }, enabled = custom.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send and decline")
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OngoingControls(call: CallSnapshot, others: List<CallSnapshot>, children: List<CallSnapshot>, audio: CallAudioState?, canAdd: Boolean) {
    val context = LocalContext.current
    var keypad by rememberSaveableBoolean()
    var routes by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val mask = audio?.supportedRouteMask ?: 0
    val hasBluetooth = mask and CallAudioState.ROUTE_BLUETOOTH != 0
    val heldOther = others.firstOrNull { it.isHeld }

    Column(Modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (keypad) {
            Text(dtmfDigits.takeLast(18), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.heightIn(min = 40.dp))
            Dialpad(
                keyHeight = 60.dp,
                onKey = { dtmfDigits += it },
                onPress = { CallManager.playDtmf(call.id, it) },
                onRelease = { CallManager.stopDtmf(call.id) },
            )
            TextButton(onClick = { keypad = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hide keypad") }
        } else {
            val tiles = buildList {
                add(Tile(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", muted, enabled = call.canMute || audio != null) { CallManager.setMuted(!muted) })
                if (hasBluetooth) {
                    add(Tile(routeIcon(route), routeLabel(route), route != CallAudioState.ROUTE_EARPIECE) { routes = true })
                } else {
                    val speaker = route == CallAudioState.ROUTE_SPEAKER
                    add(Tile(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", speaker) {
                        CallManager.setAudioRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                    })
                }
                add(Tile(Icons.Filled.Dialpad, "Keypad", false) { keypad = true })
                add(Tile(Icons.Filled.Pause, if (call.isHeld) "Resume" else "Hold", call.isHeld, enabled = call.canHold && !call.isDialing) {
                    CallManager.hold(call.id, !call.isHeld)
                })
                add(Tile(Icons.Filled.AddIcCall, "Add call", false, enabled = canAdd) {
                    context.startActivity(Intent(Intent.ACTION_DIAL).setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                when {
                    call.conferenceableIds.isNotEmpty() ->
                        add(Tile(Icons.Filled.CallMerge, "Merge", false) { CallManager.conference(call.id, call.conferenceableIds.first()) })
                    call.canMergeConference -> add(Tile(Icons.Filled.CallMerge, "Merge", false) { CallManager.mergeConference(call.id) })
                }
                when {
                    call.canSwapConference -> add(Tile(Icons.Filled.SwapCalls, "Swap", false) { CallManager.swapConference(call.id) })
                    heldOther != null -> add(Tile(Icons.Filled.SwapCalls, "Swap", false) { CallManager.hold(heldOther.id, false) })
                }
                if (call.isConference && children.isNotEmpty()) add(Tile(Icons.Filled.Groups, "Manage", false) { manage = true })
            }
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { ControlTile(it, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        BigRoundButton(Icons.Filled.CallEnd, "End call", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, wide = true) { CallManager.hangup(call.id) }
    }

    if (routes && audio != null) {
        ModalBottomSheet(onDismissRequest = { routes = false }) {
            val options = listOf(CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_WIRED_HEADSET, CallAudioState.ROUTE_EARPIECE)
                .filter { audio.supportedRouteMask and it != 0 }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Audio output", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                options.forEachIndexed { i, r ->
                    GroupedRow(routeLabel(r), i, options.size, icon = routeIcon(r), summary = if (r == route) "Current" else null,
                        onClick = { CallManager.setAudioRoute(r); routes = false })
                }
            }
        }
    }
    if (manage) {
        ModalBottomSheet(onDismissRequest = { manage = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Conference call", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                children.forEachIndexed { i, child ->
                    GroupedRow(child.title, i, children.size, trailing = {
                        Row {
                            if (child.canSeparate) TextButton(onClick = { CallManager.splitFromConference(child.id); manage = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Private") }
                            if (child.canDisconnectFromConference) TextButton(onClick = { CallManager.hangup(child.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("End") }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableBoolean(): MutableState<Boolean> = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

private data class Tile(val icon: ImageVector, val label: String, val on: Boolean, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
private fun ControlTile(tile: Tile, modifier: Modifier) {
    val container by animateColorAsState(
        if (tile.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "tile",
    )
    val content = if (tile.on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clickable(enabled = tile.enabled, role = Role.Button, onClick = tile.onClick)
            .semantics { contentDescription = tile.label + if (tile.on) ", on" else "" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = if (tile.on) ExperienceShape.listSingle else CircleShape,
            color = container,
            contentColor = content,
            modifier = Modifier.size(width = 72.dp, height = 60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tile.icon, contentDescription = null, tint = if (tile.enabled) content else content.copy(alpha = 0.38f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(tile.label, style = MaterialTheme.typography.labelLarge, color = if (tile.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
private fun BigRoundButton(icon: ImageVector, label: String, color: Color, onColor: Color, wide: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)) {
        Surface(
            shape = ExperienceShape.pill,
            color = color,
            contentColor = onColor,
            modifier = Modifier.size(width = if (wide) 160.dp else 76.dp, height = 76.dp).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, Modifier.size(32.dp)) }
        }
        if (!wide) {
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun routeLabel(route: Int) = when (route) {
    CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
    CallAudioState.ROUTE_SPEAKER -> "Speaker"
    CallAudioState.ROUTE_WIRED_HEADSET -> "Wired headset"
    else -> "Phone"
}

private fun routeIcon(route: Int): ImageVector = when (route) {
    CallAudioState.ROUTE_BLUETOOTH -> Icons.Filled.Bluetooth
    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Filled.Headset
    else -> Icons.Filled.PhoneInTalk
}
