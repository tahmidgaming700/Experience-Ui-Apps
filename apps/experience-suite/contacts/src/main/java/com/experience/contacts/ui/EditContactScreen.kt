package com.experience.contacts.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.experience.contacts.data.ContactDraft
import com.experience.contacts.data.ContactSaver
import com.experience.contacts.data.EditableField
import com.experience.contacts.data.FieldTypes
import com.experience.contacts.data.VCardParser
import com.experience.design.components.ExperienceScaffold
import com.experience.design.components.SectionHeader
import com.experience.design.theme.ExperienceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contact editor. [original] is the stored state (null for a new contact); the
 * diff between it and the edited draft becomes the ContentProviderOperation batch.
 */
@Composable
fun EditContactScreen(
    initial: ContactDraft,
    original: ContactDraft?,
    existingPhotoUri: String?,
    onSave: (ContactDraft) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(initial) { mutableStateOf(initial) }
    var showMoreName by rememberSaveable { mutableStateOf(initial.prefix.isNotBlank() || initial.middle.isNotBlank() || initial.suffix.isNotBlank()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var birthdayError by remember { mutableStateOf(false) }
    val changed = draft != initial || original == null && !draft.isEmpty

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { ContactSaver(context).preparePhoto(uri) }
            if (bytes != null) draft = draft.copy(newPhoto = bytes, removePhoto = false)
        }
    }
    val newPhotoBitmap = remember(draft.newPhoto) { draft.newPhoto?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }

    fun trySave() {
        val bday = draft.birthday.trim()
        if (bday.isNotEmpty() && VCardParser.normalizeBirthday(bday) == null) { birthdayError = true; return }
        onSave(draft.copy(birthday = if (bday.isEmpty()) "" else VCardParser.normalizeBirthday(bday)!!))
    }

    BackHandler { if (changed) confirmDiscard = true else onCancel() }

    ExperienceScaffold(
        title = if (original == null) "New contact" else "Edit contact",
        onBack = { if (changed) confirmDiscard = true else onCancel() },
        maxContentWidth = 720.dp,
        actions = {
            Button(onClick = ::trySave, enabled = !draft.isEmpty, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 48.dp),
        ) {
            // Photo
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Choose photo") { photoPicker.launch("image/*") }) {
                    when {
                        newPhotoBitmap != null -> Image(newPhotoBitmap, contentDescription = "Contact photo", contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp).clip(CircleShape))
                        existingPhotoUri != null && !draft.removePhoto -> ContactAvatar(draft.fullName, existingPhotoUri, size = 120.dp)
                        else -> Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(120.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo", Modifier.size(40.dp)) }
                        }
                    }
                }
                Row {
                    TextButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose photo") }
                    if (draft.newPhoto != null || (existingPhotoUri != null && !draft.removePhoto && draft.photoDataId != null)) {
                        TextButton(onClick = { draft = draft.copy(newPhoto = null, removePhoto = true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
                    }
                }
            }

            SectionHeader("Name")
            Field("First name", draft.given, Icons.Filled.Person, cap = KeyboardCapitalization.Words) { draft = draft.copy(given = it) }
            Field("Last name", draft.family, null, cap = KeyboardCapitalization.Words) { draft = draft.copy(family = it) }
            if (showMoreName) {
                Field("Prefix", draft.prefix, null, cap = KeyboardCapitalization.Words) { draft = draft.copy(prefix = it) }
                Field("Middle name", draft.middle, null, cap = KeyboardCapitalization.Words) { draft = draft.copy(middle = it) }
                Field("Suffix", draft.suffix, null, cap = KeyboardCapitalization.Words) { draft = draft.copy(suffix = it) }
            } else {
                TextButton(onClick = { showMoreName = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More name fields") }
            }

            MultiField("Phone", Icons.Filled.Phone, draft.phones, FieldTypes.phone, KeyboardType.Phone, defaultType = FieldTypes.phone.first().first) { draft = draft.copy(phones = it) }
            MultiField("Email", Icons.Filled.Email, draft.emails, FieldTypes.email, KeyboardType.Email, defaultType = FieldTypes.email.first().first) { draft = draft.copy(emails = it) }
            MultiField("Address", Icons.Filled.Place, draft.addresses, FieldTypes.postal, KeyboardType.Text, defaultType = FieldTypes.postal.first().first, multiline = true) { draft = draft.copy(addresses = it) }
            MultiField("Website", Icons.Filled.Language, draft.websites, FieldTypes.website, KeyboardType.Uri, defaultType = FieldTypes.website.first().first) { draft = draft.copy(websites = it) }

            SectionHeader("Organisation")
            Field("Company", draft.company, Icons.Filled.Business, cap = KeyboardCapitalization.Words) { draft = draft.copy(company = it) }
            Field("Job title", draft.jobTitle, null, cap = KeyboardCapitalization.Sentences) { draft = draft.copy(jobTitle = it) }

            SectionHeader("More")
            OutlinedTextField(
                value = draft.birthday,
                onValueChange = { draft = draft.copy(birthday = it); birthdayError = false },
                label = { Text("Birthday") },
                placeholder = { Text("YYYY-MM-DD or --MM-DD") },
                leadingIcon = { Icon(Icons.Filled.Cake, contentDescription = null) },
                isError = birthdayError,
                supportingText = if (birthdayError) ({ Text("Use YYYY-MM-DD, or --MM-DD without a year") }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Field("Notes", draft.note, Icons.Filled.EditNote, cap = KeyboardCapitalization.Sentences, singleLine = false) { draft = draft.copy(note = it) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onCancel() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    icon: ImageVector?,
    cap: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = cap),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun MultiField(
    title: String,
    icon: ImageVector,
    fields: List<EditableField>,
    types: List<Pair<Int, String>>,
    keyboard: KeyboardType,
    defaultType: Int,
    multiline: Boolean = false,
    onChange: (List<EditableField>) -> Unit,
) {
    SectionHeader(title)
    fields.forEachIndexed { i, f ->
        Surface(shape = ExperienceShape.grouped(i, fields.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = f.value,
                        onValueChange = { v -> onChange(fields.toMutableList().also { it[i] = f.copy(value = v) }) },
                        label = { Text(title) },
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        singleLine = !multiline, minLines = if (multiline) 2 else 1,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange(fields.toMutableList().also { it.removeAt(i) }) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove $title ${i + 1}")
                    }
                }
                TypeChooser(types, f.type, f.label) { t -> onChange(fields.toMutableList().also { it[i] = f.copy(type = t, label = null) }) }
            }
        }
    }
    TextButton(onClick = { onChange(fields + EditableField(type = defaultType)) }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add ${title.lowercase()}")
    }
}

@Composable
private fun TypeChooser(types: List<Pair<Int, String>>, type: Int, custom: String?, onType: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(FieldTypes.label(types, type, custom)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            types.forEach { (t, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onType(t) }) }
        }
    }
}
