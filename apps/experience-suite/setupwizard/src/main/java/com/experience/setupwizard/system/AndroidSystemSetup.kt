package com.experience.setupwizard.system

import android.os.Build
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.IBinder
import android.os.LocaleList
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import com.experience.setupwizard.SetupActivity
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.function.Consumer
import kotlin.coroutines.resume

/**
 * Production [SystemSetup]. Inside Experience UI the wizard is a platform-signed
 * priv-app, so the hidden APIs used here (reached by reflection; platform-signed apps
 * are exempt from hidden-API restrictions) and the signature|privileged permissions
 * are available. Anywhere else each call fails with a readable [SetupException].
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi", "BlockedPrivateApi")
class AndroidSystemSetup(context: Context) : SystemSetup {

    private val context = context.applicationContext
    private val cr = this.context.contentResolver
    private val userId: Int get() = Process.myUid() / PER_USER_RANGE

    private inline fun attempt(what: String, block: () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (e: Throwable) {
        val root = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
        val reason = when (root) {
            is SecurityException -> "permission denied"
            is ClassNotFoundException, is NoSuchMethodException -> "not supported on this system"
            is SetupException -> root.message
            else -> root.message ?: root.javaClass.simpleName
        }
        Result.failure(SetupException("Couldn't $what: $reason", root))
    }

    override fun isProvisioned(): Boolean =
        Settings.Global.getInt(cr, Settings.Global.DEVICE_PROVISIONED, 0) == 1 &&
            Settings.Secure.getInt(cr, USER_SETUP_COMPLETE, 0) == 1

    // --- Language -------------------------------------------------------------------

    override fun applyLocale(locale: Locale) = attempt("change the system language") {
        // com.android.internal.app.LocalePicker.updateLocales(LocaleList) updates the
        // global configuration (requires CHANGE_CONFIGURATION) and persists it.
        Class.forName("com.android.internal.app.LocalePicker")
            .getMethod("updateLocales", LocaleList::class.java)
            .invoke(null, LocaleList(locale))
    }

    // --- Accessibility --------------------------------------------------------------

    override fun fontScale(): Float = Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f)

    override fun setFontScale(scale: Float) = attempt("change the font size") {
        if (!Settings.System.putFloat(cr, Settings.System.FONT_SCALE, scale)) throw SetupException("setting rejected")
    }

    private fun windowManager(): Any =
        Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)
            ?: throw SetupException("window manager unavailable")

    override fun defaultDensity(): Int? = runCatching {
        val wm = windowManager()
        wm.javaClass.getMethod("getInitialDisplayDensity", Int::class.javaPrimitiveType).invoke(wm, Display.DEFAULT_DISPLAY) as Int
    }.getOrNull()?.takeIf { it > 0 }

    override fun currentDensity(): Int = context.resources.configuration.densityDpi

    override fun setDensity(dpi: Int) = attempt("change the display size") {
        // IWindowManager.setForcedDisplayDensityForUser / clearForcedDisplayDensityForUser
        // (WRITE_SECURE_SETTINGS), the same calls Settings > Display size makes.
        val wm = windowManager()
        val int = Int::class.javaPrimitiveType
        if (dpi == defaultDensity()) {
            wm.javaClass.getMethod("clearForcedDisplayDensityForUser", int, int).invoke(wm, Display.DEFAULT_DISPLAY, userId)
        } else {
            wm.javaClass.getMethod("setForcedDisplayDensityForUser", int, int, int).invoke(wm, Display.DEFAULT_DISPLAY, dpi, userId)
        }
    }

    override fun isSecureFlagOn(key: String) = Settings.Secure.getInt(cr, key, 0) == 1

    override fun setSecureFlag(key: String, on: Boolean) = attempt("change this setting") {
        if (!Settings.Secure.putInt(cr, key, if (on) 1 else 0)) throw SetupException("setting rejected")
    }

    // --- Date & time ----------------------------------------------------------------

    override fun isAutoTime() = Settings.Global.getInt(cr, Settings.Global.AUTO_TIME, 1) == 1
    override fun setAutoTime(on: Boolean) = attempt("change automatic time") {
        Settings.Global.putInt(cr, Settings.Global.AUTO_TIME, if (on) 1 else 0)
    }

    override fun isAutoTimeZone() = Settings.Global.getInt(cr, Settings.Global.AUTO_TIME_ZONE, 1) == 1
    override fun setAutoTimeZone(on: Boolean) = attempt("change automatic time zone") {
        Settings.Global.putInt(cr, Settings.Global.AUTO_TIME_ZONE, if (on) 1 else 0)
    }

    override fun setTimeZone(id: String) = attempt("set the time zone") {
        context.getSystemService(AlarmManager::class.java).setTimeZone(id) // SET_TIME_ZONE
    }

    override fun setTime(millis: Long) = attempt("set the time") {
        context.getSystemService(AlarmManager::class.java).setTime(millis) // SET_TIME
    }

    // --- Location -------------------------------------------------------------------

    override fun isLocationEnabled(): Boolean = context.getSystemService(LocationManager::class.java).isLocationEnabled

    override fun setLocationEnabled(on: Boolean) = attempt("change location") {
        val lm = context.getSystemService(LocationManager::class.java)
        try {
            // @SystemApi LocationManager.setLocationEnabledForUser(boolean, UserHandle).
            lm.javaClass.getMethod("setLocationEnabledForUser", Boolean::class.javaPrimitiveType, UserHandle::class.java)
                .invoke(lm, on, Process.myUserHandle())
        } catch (e: ReflectiveOperationException) {
            @Suppress("DEPRECATION")
            Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, if (on) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY else Settings.Secure.LOCATION_MODE_OFF)
        }
    }

    // --- Navigation -----------------------------------------------------------------

    private fun overlayManager(): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "overlay") as? IBinder ?: throw SetupException("overlay service unavailable")
        return Class.forName("android.content.om.IOverlayManager\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder) ?: throw SetupException("overlay service unavailable")
    }

    override fun navMode(): NavMode? = when (runCatching { Settings.Secure.getInt(cr, "navigation_mode", -1) }.getOrDefault(-1)) {
        NAV_MODE_GESTURAL -> NavMode.GESTURE
        NAV_MODE_3BUTTON -> NavMode.THREE_BUTTON
        else -> null
    }

    override fun setNavMode(mode: NavMode) = attempt("change navigation") {
        val pkg = if (mode == NavMode.GESTURE) OVERLAY_GESTURAL else OVERLAY_3BUTTON
        val om = overlayManager()
        val int = Int::class.javaPrimitiveType
        val info = om.javaClass.getMethod("getOverlayInfo", String::class.java, int).invoke(om, pkg, userId)
            ?: throw SetupException("this navigation mode is not available")
        // IOverlayManager.setEnabledExclusiveInCategory enables this overlay and disables
        // the other navbar overlays (CHANGE_OVERLAY_PACKAGES). SystemUI then updates
        // Settings.Secure.navigation_mode itself.
        val ok = om.javaClass.getMethod("setEnabledExclusiveInCategory", String::class.java, int).invoke(om, pkg, userId) as Boolean
        if (!ok && info.javaClass.getMethod("isEnabled").invoke(info) != true) throw SetupException("overlay rejected")
    }

    // --- Status bar -----------------------------------------------------------------

    @SuppressLint("WrongConstant")
    override fun setStatusBarExpansionDisabled(disabled: Boolean) = attempt("lock the status bar") {
        // StatusBarManager.disable(int) is hidden and needs STATUS_BAR.
        val sbm = context.getSystemService("statusbar") ?: throw SetupException("status bar unavailable")
        sbm.javaClass.getMethod("disable", Int::class.javaPrimitiveType).invoke(sbm, if (disabled) DISABLE_EXPAND else 0)
    }

    // --- Completion -----------------------------------------------------------------

    override fun isInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    @Suppress("UNCHECKED_CAST")
    override suspend fun makeDefaultHome(packageName: String): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // The HOME role (RoleManager) is Android 10+. On Android 9 the user picks the
            // default launcher from system settings; there is no in-app way to set it.
            return Result.failure(SetupException("Set the default launcher in Settings on Android 9"))
        }
        val rm = context.getSystemService(RoleManager::class.java)
            ?: return Result.failure(SetupException("Couldn't set the default home app: role service unavailable"))
        val prepared = attempt("set the default home app") {
            // getRoleHolders is @SystemApi (MANAGE_ROLE_HOLDERS).
            val holders = rm.javaClass.getMethod("getRoleHolders", String::class.java).invoke(rm, RoleManager.ROLE_HOME) as List<String>
            if (packageName in holders) throw AlreadyHolder()
        }
        if (prepared.exceptionOrNull()?.cause is AlreadyHolder) return Result.success(Unit)
        if (prepared.isFailure) return prepared
        return try {
            val granted = withTimeout(ROLE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val callback = Consumer<Boolean> { ok -> if (cont.isActive) cont.resume(ok) }
                    // RoleManager.addRoleHolderAsUser(role, pkg, flags, user, executor, callback)
                    rm.javaClass.getMethod(
                        "addRoleHolderAsUser", String::class.java, String::class.java, Int::class.javaPrimitiveType,
                        UserHandle::class.java, java.util.concurrent.Executor::class.java, Consumer::class.java,
                    ).invoke(rm, RoleManager.ROLE_HOME, packageName, 0, Process.myUserHandle(), context.mainExecutor, callback)
                }
            }
            if (granted) Result.success(Unit) else Result.failure(SetupException("Couldn't set the default home app: request refused"))
        } catch (e: TimeoutCancellationException) {
            Result.failure(SetupException("Couldn't set the default home app: no answer from the role service", e))
        } catch (e: Exception) {
            val root = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            Result.failure(SetupException("Couldn't set the default home app: ${if (root is SecurityException) "permission denied" else root.javaClass.simpleName}", root))
        }
    }

    private class AlreadyHolder : RuntimeException()

    override fun markProvisioned() = attempt("mark setup as complete") {
        Settings.Global.putInt(cr, Settings.Global.DEVICE_PROVISIONED, 1)
        Settings.Secure.putInt(cr, USER_SETUP_COMPLETE, 1)
    }

    override fun disableSetupComponent() = attempt("hand over to the launcher") {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SetupActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        const val PER_USER_RANGE = 100000
        /** Settings.Secure.USER_SETUP_COMPLETE (hidden constant). */
        const val USER_SETUP_COMPLETE = "user_setup_complete"
        /** StatusBarManager.DISABLE_EXPAND. */
        const val DISABLE_EXPAND = 0x00010000
        const val NAV_MODE_3BUTTON = 0
        const val NAV_MODE_GESTURAL = 2
        const val OVERLAY_GESTURAL = "com.android.internal.systemui.navbar.gestural"
        const val OVERLAY_3BUTTON = "com.android.internal.systemui.navbar.threebutton"
        const val ROLE_TIMEOUT_MS = 4000L
    }
}
