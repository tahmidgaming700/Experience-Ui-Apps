package com.experience.messages.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.experience.design.components.HeroCard
import com.experience.messages.sms.SmsSender

fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app can handle this", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, "No app can handle this", Toast.LENGTH_SHORT).show()
    }
}

/** Short relative time for lists: "14:05", "Mon", "12 Mar", "12/03/2024". */
fun shortTime(context: Context, millis: Long): String {
    val now = System.currentTimeMillis()
    val flags = when {
        DateUtils.isToday(millis) -> DateUtils.FORMAT_SHOW_TIME
        now - millis < 6 * DateUtils.DAY_IN_MILLIS -> DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        now - millis < 300 * DateUtils.DAY_IN_MILLIS -> DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH
        else -> DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE
    }
    return DateUtils.formatDateTime(context, millis, flags)
}

/** Offers to become the default SMS app; sending and receiving require the role. */
@Composable
fun SmsRoleBanner(modifier: Modifier = Modifier, onChanged: () -> Unit = {}) {
    // The SMS role (RoleManager) is Android 10+. On Android 9 use the system SMS-app
    // picker from system settings instead; no in-app role banner.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val context = LocalContext.current
    val rm = remember { context.getSystemService(RoleManager::class.java) }
    var isDefault by remember { mutableStateOf(SmsSender.isDefaultSmsApp(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = SmsSender.isDefaultSmsApp(context)
        onChanged()
    }
    if (!isDefault && rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
        HeroCard(
            title = "Set as default SMS app",
            body = "Needed to send and receive messages.",
            icon = Icons.AutoMirrored.Filled.Message,
            modifier = modifier.padding(vertical = 8.dp),
            onClick = { launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS)) },
        )
    }
}
