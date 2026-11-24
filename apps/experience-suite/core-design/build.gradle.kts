plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.experience.design"
    compileSdk = 35

    defaultConfig {
        // Experience UI is an Android 11 (API 30) operating system.
        minSdk = providers.gradleProperty("experience.minSdk").map { it.toInt() }.getOrElse(30)
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        checkDependencies = false
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    api(libs.compose.material3.window)
    api(libs.compose.icons.extended)
    api(libs.compose.ui.tooling.preview)
    api(libs.androidx.core.ktx)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.kotlinx.coroutines.android)
    // Material Components supplies the View-system Material 3 window themes and the
    // material-color-utilities (HCT) implementation used for tonal palettes.
    api(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
