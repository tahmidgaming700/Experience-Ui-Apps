package com.experience.dialer.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import com.experience.dialer.DialerViewModel
import com.experience.dialer.data.CallGroup
import com.experience.dialer.data.CallKind
import com.experience.dialer.data.DurationFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailsSheet(vm: DialerViewModel, group: CallGroup, onDismiss: () -> Unit, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val e = group.latest
    val hasNumber = e.number.isNotBlank()
    val title = e.name ?: if (hasNumber) vm.formatter.format(e.number) else "Private number"
    var blocked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(e.number) {
        blocked = if (!hasNumber) null else withContext(Dispatchers.IO) { Blocking.state(context, e.number) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ContactAvatar(title, e.photoUri, size = 80.dp)
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (e.name != null && hasNumber) {
                    Text(listOfNotNull(e.numberLabel, vm.formatter.format(e.number)).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (hasNumber) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SheetAction(Icons.Filled.Call, "Call") { onCall(e.number) }
                    SheetAction(Icons.AutoMirrored.Filled.Message, "Message") {
                        context.safeStart(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", e.number, null)))
                    }
                    if (e.lookupUri != null) {
                        SheetAction(Icons.Filled.Person, "Contact") { context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(e.lookupUri))) }
                    } else {
                        SheetAction(Icons.Filled.PersonAdd, "Add") {
                            context.safeStart(
                                Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                    .putExtra(ContactsContract.Intents.Insert.PHONE, e.number)
                            )
                        }
                    }
                    SheetAction(Icons.Filled.ContentCopy, "Copy") {
                        clipboard.setText(AnnotatedString(e.number))
                        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                    }
                    blocked?.let { isBlocked ->
                        SheetAction(Icons.Filled.Block, if (isBlocked) "Unblock" else "Block") {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { Blocking.set(context, e.number, !isBlocked) }
                                if (ok) blocked = !isBlocked
                                else Toast.makeText(context, "Blocking isn't available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            SectionHeader("Calls")
            group.entries.forEachIndexed { i, c ->
                val kind = CallKind.of(c.type)
                Surface(shape = ExperienceShape.grouped(i, group.count), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(kind.icon, contentDescription = null, tint = kind.tint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(kind.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                DateUtils.formatDateTime(context, c.dateMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (c.durationSeconds > 0) Text(DurationFormat.words(c.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { vm.deleteCalls(group.ids); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (group.count > 1) "Delete these ${group.count} calls" else "Delete from history")
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app can handle this", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, "No app can handle this", Toast.LENGTH_SHORT).show()
    }
}

/** BlockedNumberContract access; allowed for the default dialer / SMS app and system apps. */
object Blocking {
    /** null when this user cannot block numbers or the app is not allowed to. */
    fun state(context: Context, number: String): Boolean? = runCatching {
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) null
        else BlockedNumberContract.isBlocked(context, number)
    }.getOrNull()

    fun set(context: Context, number: String, block: Boolean): Boolean = runCatching {
        if (block) {
            val values = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
        } else {
            BlockedNumberContract.unblock(context, number) > 0
        }
    }.getOrDefault(false)
}
