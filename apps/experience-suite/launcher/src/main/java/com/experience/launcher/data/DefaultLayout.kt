package com.experience.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import com.experience.launcher.model.AppKey
import com.experience.launcher.model.HomeLayout

/**
 * First-run layout: the dock gets the default phone, messaging, browser and camera
 * apps (resolved through their intents, so whichever app holds the role is used);
 * the home grid gets the Experience apps that are installed.
 */
object DefaultLayout {

    private val dockIntents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    )

    private val homePackages = listOf(
        "com.experience.settings", "com.experience.contacts", "com.experience.clock",
        "com.experience.calendar", "com.experience.gallery", "com.experience.files",
        "com.experience.notes", "com.experience.calculator", "com.experience.converter",
    )

    fun build(context: Context, apps: List<LauncherApp>): HomeLayout {
        val personal = apps.filter { !it.isWork }
        fun firstOf(pkg: String): AppKey? = personal.firstOrNull { it.component.packageName == pkg }?.key

        val dock = dockIntents.mapNotNull { intent ->
            val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            // "android" is the chooser shown when no default is set; skip it.
            ri?.activityInfo?.packageName?.takeIf { it != "android" }?.let(::firstOf)
        }.distinct().take(HomeLayout.DOCK_CAPACITY - 1)

        val home = homePackages.mapNotNull(::firstOf).filter { it !in dock }
        return HomeLayout(home = home, dock = dock)
    }
}
