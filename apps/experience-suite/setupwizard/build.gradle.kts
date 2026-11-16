plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.experience.setupwizard"
    defaultConfig {
        applicationId = "com.experience.setupwizard"
        // "Setup preview" launcher entry for trying the wizard on an already set-up phone.
        // Off for ROM builds; enable with ./gradlew -Pexperience.setupPreview=true.
        resValue("bool", "experience_setup_preview",
            providers.gradleProperty("experience.setupPreview").getOrElse("false"))
    }
    buildFeatures { resValues = true }
}

dependencies {
    implementation(project(":core-design"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
