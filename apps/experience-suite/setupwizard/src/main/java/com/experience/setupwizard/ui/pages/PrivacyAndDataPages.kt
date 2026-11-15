package com.experience.setupwizard.ui.pages

import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.EmptyState
import com.experience.design.components.GroupedRow
import com.experience.design.components.HeroCard
import com.experience.design.components.SectionHeader
import com.experience.design.components.SwitchRow
import com.experience.setupwizard.SetupViewModel
import com.experience.setupwizard.ui.PageLayout
import com.experience.setupwizard.ui.tryStart

@Composable
fun PrivacyPage(vm: SetupViewModel) {
    val context = LocalContext.current
    val s by vm.systemState.collectAsState()
    PageLayout(Icons.Outlined.PrivacyTip, "Your privacy", "Experience UI is built to keep your data on your device.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            HeroCard(
                "No telemetry. No Google services.",
                "The Vanilla edition of Experience UI contains no telemetry, analytics or Google services. " +
                    "Nothing is sent anywhere unless an app you install does so.",
                icon = Icons.Outlined.Shield,
            )
            SectionHeader("Location")
            SwitchRow(
                "Use location", s.location, vm::setLocation, 0, 1,
                summary = "Lets apps with permission use GPS, Wi-Fi and mobile networks to find your location",
                icon = Icons.Outlined.LocationOn,
            )
            SectionHeader("More")
            GroupedRow("Privacy settings", 0, 1, summary = "Permissions, permission usage and more", icon = Icons.Outlined.PrivacyTip, onClick = {
                if (!context.tryStart(Intent(Settings.ACTION_PRIVACY_SETTINGS))) context.tryStart(Intent(Settings.ACTION_SETTINGS))
            })
        }
    }
}

@Composable
fun CopyDataPage(vm: SetupViewModel) {
    val context = LocalContext.current
    var imported by rememberSaveable { mutableStateOf<String?>(null) }
    // The contacts app imports the file itself (ImportVCardActivity handles ACTION_VIEW text/x-vcard).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context.tryStart(view)) imported = uri.lastPathSegment ?: "file" else vm.showMessage("No contacts app can import this file.")
        }
    }
    PageLayout(Icons.Outlined.SystemUpdateAlt, "Copy your data", "Bring contacts from your old phone. This step is optional.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            HeroCard(
                "No cloud restore",
                "Restoring a backup from the cloud needs Google services, which this edition does not include. " +
                    "Export your contacts as a .vcf file on your old phone and copy it to this one.",
            )
            SectionHeader("Contacts")
            GroupedRow(
                "Import contacts from a file", 0, 1,
                summary = imported?.let { "Opened in Contacts: $it" } ?: "Choose a .vcf file from storage or a USB drive",
                icon = if (imported != null) Icons.Outlined.CheckCircle else Icons.Outlined.ContactPage,
                onClick = { picker.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory")) },
            )
        }
    }
}

private class Authenticator(val description: AuthenticatorDescription, val label: String, val icon: ImageBitmap?)

private fun loadAuthenticators(context: Context): List<Authenticator> {
    val pm = context.packageManager
    return AccountManager.get(context).authenticatorTypes.mapNotNull { d ->
        val res = runCatching { pm.getResourcesForApplication(d.packageName) }.getOrNull() ?: return@mapNotNull null
        val label = runCatching { res.getString(d.labelId) }.getOrNull() ?: d.type
        val icon = runCatching {
            val drawable = pm.getDrawable(d.packageName, d.iconId, null) ?: return@runCatching null
            val px = (40 * context.resources.displayMetrics.density).toInt()
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
                drawable.setBounds(0, 0, px, px); drawable.draw(Canvas(b))
            }.asImageBitmap()
        }.getOrNull()
        Authenticator(d, label, icon)
    }.sortedBy { it.label.lowercase() }
}

@Composable
fun AccountsPage(vm: SetupViewModel) {
    val context = LocalContext.current
    val added by vm.accountsAdded.collectAsState()
    val authenticators = remember { loadAuthenticators(context) }
    PageLayout(Icons.Outlined.AccountCircle, "Add your accounts", "Sign in to sync mail, contacts and calendars through the apps you have installed.") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            if (authenticators.isEmpty()) {
                EmptyState(
                    Icons.Outlined.PersonAdd,
                    "No account providers installed",
                    "Apps that offer accounts (for example mail or sync apps) add them here once installed. Skip this step for now.",
                )
            } else {
                SectionHeader("Add an account")
                authenticators.forEachIndexed { i, a ->
                    GroupedRow(
                        a.label, i, authenticators.size,
                        icon = if (a.icon == null) Icons.Outlined.AccountCircle else null,
                        onClick = {
                            val activity = context as? Activity ?: return@GroupedRow
                            // The authenticator shows its own sign-in screens on top of the wizard.
                            AccountManager.get(context).addAccount(a.description.type, null, null, null, activity, { future ->
                                val result = runCatching { future.result }.getOrNull()
                                val name = result?.getString(AccountManager.KEY_ACCOUNT_NAME)
                                if (name != null) vm.onAccountAdded("$name (${a.label})")
                            }, null)
                        },
                        trailing = a.icon?.let { bmp -> { Image(bmp, contentDescription = null, modifier = Modifier.size(40.dp)) } },
                    )
                }
            }
            if (added.isNotEmpty()) {
                SectionHeader("Added")
                added.forEachIndexed { i, name -> GroupedRow(name, i, added.size, icon = Icons.Outlined.CheckCircle) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "You can add more accounts later in Settings > Accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
