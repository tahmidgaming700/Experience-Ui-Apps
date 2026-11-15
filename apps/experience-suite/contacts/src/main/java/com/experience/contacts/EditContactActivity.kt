package com.experience.contacts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.experience.contacts.data.ContactDraft
import com.experience.contacts.data.ContactSaver
import com.experience.contacts.data.InsertExtras
import com.experience.contacts.ui.ContactList
import com.experience.contacts.ui.EditContactScreen
import com.experience.design.ExperienceActivity
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.HeroCard
import com.experience.design.components.PermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ACTION_INSERT (new contact, honouring Intents.Insert extras), ACTION_EDIT (existing
 * contact) and ACTION_INSERT_OR_EDIT (choose a contact or create one, then add the
 * extras). Returns RESULT_OK with the contact lookup URI.
 */
class EditContactActivity : ExperienceActivity() {
    private val vm: ContactsViewModel by viewModels()

    private sealed interface Stage {
        data object Loading : Stage
        data object Choose : Stage
        data class Edit(val draft: ContactDraft, val original: ContactDraft?, val photoUri: String?) : Stage
    }

    private var stage by mutableStateOf<Stage>(Stage.Loading)
    private val saver by lazy { ContactSaver(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            Intent.ACTION_EDIT -> intent.data?.let { open(it) } ?: newContact()
            Intent.ACTION_INSERT_OR_EDIT -> stage = Stage.Choose
            else -> newContact()
        }
    }

    private fun newContact() {
        stage = Stage.Edit(InsertExtras.apply(ContactDraft(), intent.extras), null, null)
    }

    /** Loads the editable raw contact behind [uri] and merges any Insert extras into it. */
    private fun open(uri: Uri) {
        stage = Stage.Loading
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val lookup = vm.repo.resolve(uri) ?: return@runCatching null
                    val raw = saver.rawContactFor(lookup) ?: return@runCatching null
                    val original = saver.load(raw)
                    val photo = vm.repo.details(lookup)?.photoUri
                    Triple(original, InsertExtras.apply(original, intent.extras), photo)
                }.getOrNull()
            }
            if (loaded == null) {
                Toast.makeText(this@EditContactActivity, "Contact can't be edited", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                stage = Stage.Edit(loaded.second, loaded.first, loaded.third)
            }
        }
    }

    private fun save(draft: ContactDraft, original: ContactDraft?) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { runCatching { saver.save(draft, original) }.getOrNull() }
            if (uri == null) {
                Toast.makeText(this@EditContactActivity, "Couldn't save the contact", Toast.LENGTH_LONG).show()
                return@launch
            }
            setResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            // Like AOSP Contacts, show the new contact unless the caller waits for a result or
            // passed finishActivityOnSaveCompleted.
            if (!intent.getBooleanExtra(EXTRA_FINISH_ON_SAVE, false) && callingActivity == null && original == null) {
                startActivity(Intent(Intent.ACTION_VIEW, uri, this@EditContactActivity, MainActivity::class.java))
            }
            finish()
        }
    }

    @Composable
    override fun Content() {
        PermissionGate(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            "Contacts",
            "Allow access to your contacts to save this contact.",
        ) {
            when (val s = stage) {
                Stage.Loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(64.dp)) }
                Stage.Choose -> ChooseContact()
                is Stage.Edit -> EditContactScreen(
                    initial = s.draft, original = s.original, existingPhotoUri = s.photoUri,
                    onSave = { save(it, s.original) },
                    onCancel = { setResult(Activity.RESULT_CANCELED); finish() },
                )
            }
        }
    }

    @Composable
    private fun ChooseContact() {
        LaunchedEffect(Unit) { vm.refresh() }
        val number = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
            ?: intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL)
        ExperienceScaffold(title = "Add to contact", onBack = { setResult(Activity.RESULT_CANCELED); finish() }) { padding ->
            Column(Modifier.padding(padding)) {
                HeroCard(
                    title = "Create new contact",
                    body = number ?: "Start a new contact with these details",
                    icon = Icons.Filled.PersonAdd,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { newContact() },
                )
                ContactList(vm, selectedId = null, onOpen = { open(it.lookupUri) }, modifier = Modifier.weight(1f))
            }
        }
    }

    companion object {
        const val EXTRA_FINISH_ON_SAVE = "finishActivityOnSaveCompleted"
    }
}
