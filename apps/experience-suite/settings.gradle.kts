pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "experience-suite"

include(":core-design")

// Each Experience application is an independent APK module. The list is the
// single source of truth used by scripts/rom/collect-app-prebuilts.sh.
val experienceApps = listOf(
    "launcher", "settings", "setupwizard", "dialer", "messages", "contacts",
    "notes", "clock", "calendar", "calculator", "files", "gallery", "camera", "converter",
)
experienceApps.filter { file(it).isDirectory }.forEach { include(":$it") }
