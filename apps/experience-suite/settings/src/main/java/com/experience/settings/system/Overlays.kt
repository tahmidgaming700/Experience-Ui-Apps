package com.experience.settings.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process

/** One installed runtime resource overlay (RRO). */
data class OverlayEntry(
    val packageName: String,
    val category: String?,
    val enabled: Boolean,
    val label: String,
)

/**
 * Access to the platform OverlayManagerService through the hidden
 * android.content.om.IOverlayManager binder interface, via reflection.
 *
 * Android 11 has no public theming API; AOSP's ThemePicker and Settings switch
 * accent colour, icon shape, font and navigation mode by enabling one overlay per
 * "category" with setEnabledExclusiveInCategory. That requires
 * CHANGE_OVERLAY_PACKAGES (signature|privileged), which Experience Settings holds as a
 * platform-signed priv-app. Everywhere else every call fails and returns
 * false/empty, so the UI hides or disables those controls.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class Overlays(private val context: Context) {

    companion object {
        const val CATEGORY_ACCENT = "android.theme.customization.accent_color"
        const val CATEGORY_ICON_SHAPE = "android.theme.customization.adaptive_icon_shape"
        const val CATEGORY_FONT = "android.theme.customization.font"
        const val CATEGORY_NAVBAR = "android.theme.customization.navbar"
        const val NAV_GESTURAL = "com.android.internal.systemui.navbar.gestural"
        const val NAV_THREE_BUTTON = "com.android.internal.systemui.navbar.threebutton"
        const val NAV_TWO_BUTTON = "com.android.internal.systemui.navbar.twobutton"
        const val TARGET_ANDROID = "android"

        /** User id of this process (UserHandle.myUserId() is hidden; uid / PER_USER_RANGE). */
        val userId: Int get() = Process.myUid() / 100000
    }

    private val service: Any? by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "overlay") as IBinder
            Class.forName("android.content.om.IOverlayManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        }.getOrNull()
    }

    val available: Boolean get() = service != null

    /** Overlays targeting [target], optionally limited to one [category]. */
    fun list(category: String? = null, target: String = TARGET_ANDROID): List<OverlayEntry> {
        val om = service ?: return emptyList()
        val infos = runCatching {
            om.javaClass.getMethod("getOverlayInfosForTarget", String::class.java, Int::class.javaPrimitiveType)
                .invoke(om, target, userId) as? List<*>
        }.getOrNull() ?: return emptyList()
        return infos.mapNotNull { info ->
            if (info == null) return@mapNotNull null
            runCatching {
                val cls = info.javaClass
                val pkg = cls.getField("packageName").get(info) as String
                val cat = cls.getField("category").get(info) as String?
                val enabled = cls.getMethod("isEnabled").invoke(info) as Boolean
                OverlayEntry(pkg, cat, enabled, label(pkg))
            }.getOrNull()
        }.filter { category == null || it.category == category }
    }

    /** Enables [packageName] and disables the other overlays of its category. */
    fun enableExclusive(packageName: String): Boolean {
        val om = service ?: return false
        return runCatching {
            om.javaClass.getMethod("setEnabledExclusiveInCategory", String::class.java, Int::class.javaPrimitiveType)
                .invoke(om, packageName, userId) as Boolean
        }.getOrDefault(false)
    }

    fun setEnabled(packageName: String, enabled: Boolean): Boolean {
        val om = service ?: return false
        return runCatching {
            om.javaClass.getMethod("setEnabled", String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(om, packageName, enabled, userId) as Boolean
        }.getOrDefault(false)
    }

    /** Returns to the platform default of a category by disabling all of its overlays. */
    fun disableCategory(category: String): Boolean =
        list(category).filter { it.enabled }.all { setEnabled(it.packageName, false) }

    private fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrNull()?.takeIf { it != pkg } ?: prettify(pkg)

    /**
     * Accent colour defined by an accent overlay: the resource it overrides,
     * android:color/accent_device_default_light, is also named that way inside the
     * overlay package (this is how AOSP ThemePicker previews accent overlays).
     */
    @SuppressLint("DiscouragedApi")
    fun accentColor(pkg: String, dark: Boolean = false): Int? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val name = if (dark) "accent_device_default_dark" else "accent_device_default_light"
        val id = res.getIdentifier(name, "color", pkg)
        if (id == 0) null else res.getColor(id, null)
    }.getOrNull()

    /** Icon-shape overlays override config_icon_mask: an SVG path in a 100x100 box. */
    @SuppressLint("DiscouragedApi")
    fun iconMask(pkg: String): String? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val id = res.getIdentifier("config_icon_mask", "string", pkg)
        if (id == 0) null else res.getString(id)
    }.getOrNull()

    /** Icon mask of the framework itself (the current default shape). */
    @SuppressLint("DiscouragedApi")
    fun systemIconMask(): String? = runCatching {
        val res = context.packageManager.getResourcesForApplication("android")
        val id = res.getIdentifier("config_icon_mask", "string", "android")
        if (id == 0) null else res.getString(id)
    }.getOrNull()

    fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS); true
    }.getOrDefault(false)
}

/** "com.experience.theme.accent.ocean_blue" -> "Ocean blue". */
fun prettify(pkg: String): String = pkg.substringAfterLast('.').replace('_', ' ').replace('-', ' ')
    .replaceFirstChar { it.uppercase() }
