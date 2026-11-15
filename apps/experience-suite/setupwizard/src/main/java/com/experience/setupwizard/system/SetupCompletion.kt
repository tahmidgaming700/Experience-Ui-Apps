package com.experience.setupwizard.system

/**
 * The end-of-setup sequence, kept separate from the activity so it is unit-testable:
 *
 * 1. DEVICE_PROVISIONED and USER_SETUP_COMPLETE,
 * 2. Experience Launcher becomes the HOME role holder (if installed), so no HOME
 *    chooser appears; Trebuchet stays installed as the recents provider,
 * 3. the status bar is unlocked,
 * 4. the wizard's own HOME activity is disabled.
 *
 * Every step runs even if an earlier one fails; the failures are returned so the
 * Done page can list them. The caller then starts the HOME intent.
 */
class SetupCompletion(private val system: SystemSetup, private val launcherPackage: String = SystemSetup.LAUNCHER_PACKAGE) {

    suspend fun run(): List<String> {
        val failures = mutableListOf<String>()
        fun Result<Unit>.collect() = exceptionOrNull()?.let { failures += it.message ?: it.toString() }

        system.markProvisioned().collect()
        if (system.isInstalled(launcherPackage)) system.makeDefaultHome(launcherPackage).collect()
        system.setStatusBarExpansionDisabled(false).collect()
        system.disableSetupComponent().collect()
        return failures
    }
}
