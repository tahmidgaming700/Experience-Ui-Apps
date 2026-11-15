package com.experience.dialer.ui

import android.content.Intent
import android.content.res.Configuration
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.experience.design.theme.ExperienceShape
import com.experience.dialer.DialerViewModel
import com.experience.dialer.data.CallKind
import com.experience.dialer.data.DialContact
import com.experience.dialer.data.SmartDial
import com.experience.dialer.telecom.CallPlacer
import com.experience.dialer.telecom.DtmfFeedback
import com.experience.dialer.telecom.SpecialCodes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(vm: DialerViewModel, bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val input by vm.dialInput.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val recents by vm.callLog.collectAsStateWithLifecycle()
    val call = rememberCallAction()
    val placer = remember { CallPlacer(context) }
    val dtmf = remember { DtmfFeedback(context) }
    DisposableEffect(Unit) { onDispose { dtmf.release() } }
    var deviceIds by remember { mutableStateOf<List<String>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var voicemailMissing by remember { mutableStateOf(false) }
    LaunchedEffect(voicemailMissing) {
        if (voicemailMissing) { snackbar.showSnackbar("No voicemail number is set up"); voicemailMissing = false }
    }

    fun set(value: String) {
        vm.dialInput.value = value
        SpecialCodes.secretCodeOf(value)?.let { code ->
            if (SpecialCodes.sendSecretCode(context, code)) vm.dialInput.value = ""
        }
        if (SpecialCodes.isDeviceIdCode(value)) {
            deviceIds = SpecialCodes.deviceIds(context)
            vm.dialInput.value = ""
        }
    }

    val suggestions = remember(input, contacts) { SmartDial.filter(contacts, input, { it.name }, { it.number }).take(40) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shortScreen = LocalConfiguration.current.screenHeightDp < 680

    deviceIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { deviceIds = null },
            title = { Text("Device identifiers") },
            text = { Text(if (ids.isEmpty()) "Device identifiers are not available to this app." else ids.joinToString("\n")) },
            confirmButton = { TextButton(onClick = { deviceIds = null }) { Text("OK") } },
        )
    }

    Scaffold(
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        val suggestionsPane: @Composable (Modifier) -> Unit = { m ->
            SuggestionList(suggestions, vm, m) { call(it.number) }
        }
        val padPane: @Composable (Modifier) -> Unit = { m ->
            Column(m.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Number display with backspace; long-press the number to paste.
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp)) {
                        if (input.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                            .putExtra(ContactsContract.Intents.Insert.PHONE, input)
                                    )
                                },
                                modifier = Modifier.size(56.dp),
                            ) { Icon(Icons.Filled.PersonAdd, contentDescription = "Add to contacts") }
                        }
                    }
                    val shown = vm.formatter.format(input)
                    Text(
                        shown.ifEmpty { "" },
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = if (shown.length > 14) 28.sp else 36.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = if (input.isEmpty()) "Number, empty" else "Number $input" }
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    clipboard.getText()?.text?.filter { it.isDigit() || it in "+*#,;" }?.takeIf { it.isNotEmpty() }?.let { set(input + it) }
                                },
                                onLongClickLabel = "Paste number",
                            ),
                    )
                    Box(
                        Modifier
                            .size(56.dp)
                            .combinedClickable(
                                enabled = input.isNotEmpty(),
                                role = Role.Button,
                                onClickLabel = "Delete digit",
                                onClick = { set(input.dropLast(1)) },
                                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); set("") },
                                onLongClickLabel = "Clear number",
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (input.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Dialpad(
                    keyHeight = if (shortScreen || landscape) 54.dp else 68.dp,
                    onKey = { k -> dtmf.play(k); set(input + k) },
                    onLongKey = { k ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (k) {
                            '0' -> set(input + '+')
                            '1' -> if (!placer.callVoicemail()) voicemailMissing = true
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = ExperienceShape.pill,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .width(160.dp)
                        .height(if (shortScreen) 56.dp else 64.dp)
                        .clickable(role = Role.Button, onClickLabel = "Call") {
                            if (input.isNotBlank()) {
                                call(input)
                            } else {
                                // Empty keypad: recall the last dialled number, like other dialers.
                                recents.flatMap { it.entries }.firstOrNull { CallKind.of(it.type) == CallKind.OUTGOING && it.number.isNotBlank() }
                                    ?.let { set(it.number) }
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, contentDescription = "Call", Modifier.size(30.dp)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (landscape) {
            Row(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                suggestionsPane(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { padPane(Modifier) }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                suggestionsPane(Modifier.weight(1f).fillMaxWidth())
                padPane(Modifier)
            }
        }
    }
}

@Composable
private fun SuggestionList(items: List<Pair<DialContact, SmartDial.Match>>, vm: DialerViewModel, modifier: Modifier, onClick: (DialContact) -> Unit) {
    LazyColumn(modifier, reverseLayout = false, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(items, key = { _, it -> it.first.dataId }) { i, (c, _) ->
            Surface(
                shape = com.experience.design.theme.ExperienceShape.grouped(i, items.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Row(
                    Modifier.clickable(role = Role.Button, onClickLabel = "Call ${c.name}") { onClick(c) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(c.name, c.photoUri, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(c.label, vm.formatter.format(c.number)).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
