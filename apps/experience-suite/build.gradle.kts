import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val experienceVersionName = providers.gradleProperty("experience.versionName").get()
val experienceVersionCode = providers.gradleProperty("experience.versionCode").get().toInt()

/*
 * Shared configuration for every Experience application module.
 *
 * - minSdk = targetSdk = 30: the apps ship inside Experience UI (Android 11) and
 *   are built against that platform's behaviour.
 * - Release APKs are left unsigned on purpose: the ROM build re-signs them with the
 *   platform certificate (android_app_import, presigned: false). Standalone release
 *   signing is applied by CI from repository secrets.
 */
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk = 35
            defaultConfig {
                minSdk = providers.gradleProperty("experience.minSdk").map { it.toInt() }.getOrElse(30)
                targetSdk = 30
                versionCode = experienceVersionCode
                versionName = experienceVersionName
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            val ksPath = System.getenv("EXPERIENCE_KEYSTORE")
            if (!ksPath.isNullOrBlank()) {
                signingConfigs.create("experience") {
                    storeFile = file(ksPath)
                    storePassword = System.getenv("EXPERIENCE_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("EXPERIENCE_KEY_ALIAS")
                    keyPassword = System.getenv("EXPERIENCE_KEY_PASSWORD")
                }
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootProject.file("proguard-experience.pro"))
                    signingConfigs.findByName("experience")?.let { signingConfig = it }
                }
            }
            buildFeatures { compose = true; buildConfig = true }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions { unitTests.isIncludeAndroidResources = true }
            packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin") }
            lint {
                abortOnError = true
                checkReleaseBuilds = true
                // Experience apps are system apps of an Android 11 OS: targetSdk 30 is
                // deliberate, and signature|privileged permissions are granted through
                // privapp-permissions-experience.xml in the ROM.
                disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi", "ProtectedPermissions", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "QueryAllPackagesPermission")
            }
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
