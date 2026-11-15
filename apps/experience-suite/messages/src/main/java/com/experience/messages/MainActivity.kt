package com.experience.messages

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.experience.design.ExperienceActivity
import com.experience.design.components.EmptyState
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.PermissionGate
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.messages.data.Recipients
import com.experience.messages.notify.MessageNotifier
import com.experience.messages.ui.ConversationList
import com.experience.messages.ui.NewConversationScreen
import com.experience.messages.ui.ThreadScreen
import kotlinx.coroutines.launch

/**
 * Conversation list with thread view (two panes on expanded width). Handles
 * SENDTO / VIEW for sms:, smsto:, mms:, mmsto:, SEND text/plain and opening a
 * thread from a notification.
 */
class MainActivity : ExperienceActivity() {
    private val vm: MessagesViewModel by viewModels()

    sealed interface Route {
        data object List : Route
        data class Thread(val threadId: Long?, val addresses: kotlin.collections.List<String>, val text: String = "") : Route
        data class New(val text: String = "") : Route
    }

    private var route by mutableStateOf<Route>(Route.List)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MessageNotifier(this).ensureChannels()
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme !in setOf("sms", "smsto", "mms", "mmsto")) return
                val recipients = Recipients.fromSchemeSpecificPart(data.schemeSpecificPart)
                val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: Recipients.bodyFromSchemeSpecificPart(data.schemeSpecificPart)?.let { android.net.Uri.decode(it) } ?: ""
                route = if (recipients.isEmpty()) Route.New(body) else Route.Thread(null, recipients, body)
            }
            Intent.ACTION_SEND -> route = Route.New(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            ACTION_OPEN_THREAD -> {
                val id = intent.getLongExtra(EXTRA_THREAD_ID, -1)
                if (id >= 0) lifecycleScope.launch { route = Route.Thread(id, vm.addressesOf(id)) }
            }
        }
    }

    private val permissions = listOf(
        Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE,
    )

    @Composable
    override fun Content() {
        val width = currentWidthClass()
        val twoPane = width == WidthClass.EXPANDED
        val r = route
        PermissionGate(permissions, "Messages", "Allow access to SMS, contacts and phone state to read and send your messages.") {
            LaunchedEffect(Unit) { vm.refresh() }
            if (!twoPane && r != Route.List) {
                BackHandler { route = Route.List }
                Detail(r, onBack = { route = Route.List })
                return@PermissionGate
            }
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    ListPane(Modifier.weight(0.4f), selected = (r as? Route.Thread)?.threadId)
                    Box(Modifier.weight(0.6f)) {
                        if (r == Route.List) {
                            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 120.dp)) {
                                EmptyState(Icons.AutoMirrored.Filled.Chat, "Select a conversation")
                            }
                        } else {
                            Detail(r, onBack = null)
                        }
                    }
                }
            } else {
                ListPane(Modifier, selected = null)
            }
        }
    }

    @Composable
    private fun Detail(r: Route, onBack: (() -> Unit)?) {
        when (r) {
            is Route.Thread -> key(r) {
                ThreadScreen(vm, r.threadId, r.addresses, r.text, onBack = onBack, onDeleted = { route = Route.List })
            }
            is Route.New -> NewConversationScreen(onBack = onBack ?: { route = Route.List }) { recipients ->
                route = Route.Thread(null, recipients, r.text)
            }
            Route.List -> Unit
        }
    }

    @Composable
    private fun ListPane(modifier: Modifier, selected: Long?) {
        val archived by vm.showArchived.collectAsStateWithLifecycle()
        val delivery by vm.prefs.deliveryReports.collectAsStateWithLifecycle()
        var menu by remember { mutableStateOf(false) }
        ExperienceScaffold(
            title = if (archived) "Archived" else "Messages",
            modifier = modifier,
            onBack = if (archived) ({ vm.showArchived.value = false }) else null,
            actions = {
                IconButton(onClick = { vm.showArchived.value = !archived }) {
                    Icon(if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive, contentDescription = if (archived) "Show inbox" else "Show archived")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Request delivery reports") },
                            trailingIcon = { Checkbox(checked = delivery, onCheckedChange = null) },
                            onClick = { vm.prefs.setDeliveryReports(!delivery) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!archived) ExtendedFloatingActionButton(
                    onClick = { route = Route.New() },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    text = { Text("Start chat") },
                )
            },
        ) { padding ->
            if (archived) BackHandler { vm.showArchived.value = false }
            ConversationList(vm, selected, onOpen = { route = Route.Thread(it.threadId, it.conversation.addresses) }, modifier = Modifier.padding(padding))
        }
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.experience.messages.action.OPEN_THREAD"
        const val EXTRA_THREAD_ID = "thread_id"
    }
}
