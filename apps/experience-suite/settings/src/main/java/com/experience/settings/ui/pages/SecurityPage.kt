package com.experience.settings.ui.pages

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.experience.design.components.SwitchRow
import com.experience.design.system.SystemInfo
import com.experience.settings.ui.Group
import com.experience.settings.ui.InfoRow
import com.experience.settings.ui.PageScaffold
import com.experience.settings.ui.SystemSettings
import com.experience.settings.ui.link
import com.experience.settings.ui.rememberSystemValue

@Composable
fun SecurityPage(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val km = context.getSystemService(KeyguardManager::class.java)
    val secure = rememberSystemValue { km.isDeviceSecure }
    val showPasswords = rememberSystemValue { Settings.System.getInt(context.contentResolver, Settings.System.TEXT_SHOW_PASSWORD, 1) == 1 }
    val encryption = remember {
        when (context.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "Encrypted (file-based)"
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> "Encrypted"
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "Not encrypted"
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "Not supported"
            else -> "Unknown"
        }
    }
    val pm = context.packageManager

    PageScaffold("Security & privacy", onBack) {
        Group("Device security") {
            link(context, "Screen lock", if (secure.value) "PIN, pattern or password is set" else "None — anyone can unlock this device", Icons.Outlined.Lock,
                Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), Intent(Settings.ACTION_SECURITY_SETTINGS))
            if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) || pm.hasSystemFeature(PackageManager.FEATURE_FACE)) {
                link(context, "Biometrics", "Fingerprint and face unlock", Icons.Outlined.Fingerprint, Intent(Settings.ACTION_BIOMETRIC_ENROLL), Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            row { i, n -> InfoRow("Encryption", encryption, i, n) }
            link(context, "More security settings", "Device admin, SIM lock, trust agents", Icons.Outlined.Security, Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        Group("Security updates") {
            // Real values from the build; nothing is inferred.
            row { i, n -> InfoRow("Android security patch", SystemInfo.securityPatch.ifBlank { "Not reported" }, i, n) }
            row { i, n -> InfoRow("Vendor security patch", SystemInfo.vendorSecurityPatch.ifBlank { "Not reported" }, i, n) }
        }
        Group("Privacy") {
            link(context, "Permission manager", "Control app access to your data", Icons.Outlined.Policy, Intent("android.intent.action.MANAGE_PERMISSIONS"))
            link(context, "Privacy settings", null, Icons.Outlined.PrivacyTip, Intent(Settings.ACTION_PRIVACY_SETTINGS))
            row { i, n ->
                SwitchRow("Show passwords", showPasswords.value, { on ->
                    SystemSettings.putSystemInt(context, Settings.System.TEXT_SHOW_PASSWORD, if (on) 1 else 0); showPasswords.refresh()
                }, i, n, summary = "Briefly show characters as you type", icon = Icons.Outlined.Password)
            }
            link(context, "Install unknown apps", null, Icons.Outlined.InstallMobile, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
        }
    }
}
