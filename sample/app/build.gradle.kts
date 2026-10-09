import java.util.Properties

// App module of the PUBLIC consumer sample (octet-sdk-android `sample/app/`).
// release-android.yml copies this file into the public repo on every SDK
// release, alongside the mirrored `src/`. It is the standalone, single-variant
// equivalent of the source sample's `public` flavor
// (samples-public/android-sample/build.gradle.kts): keep plugins, dependencies
// and buildConfigFields in step with that file.
// scripts/check-sample-buildconfig-drift.sh enforces this in CI and before a
// release publishes anything.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Per-developer settings, read from `sample/local.properties` (gitignored).
// See `local.properties.example` for the lines to add. Every value is
// optional and falls back to a safe default.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val octetLicenseKey: String = localProps.getProperty("octet.licenseKey", "")
// Defaults to production. Override only when running the SDK against your own
// activation backend. API 28+ blocks cleartext to non-loopback hosts by
// default, so use https or add a network-security-config exception.
val octetActivationServerUrl: String =
    localProps.getProperty("octet.activationServerUrl", "https://api.octetproof.com")
// (Optional) Google Cloud project NUMBER for Play Integrity. Absent / empty /
// non-numeric → "0" → the SDK skips Play Integrity. Use YOUR OWN project.
val octetPiCloudProject: String =
    (localProps.getProperty("octet.playIntegrityCloudProjectNumber") ?: "")
        .trim().toLongOrNull()?.toString() ?: "0"
// (Optional) Your own OpenCelliD API key (free at opencellid.org) for real
// tower positions in the cell demo. Absent / blank → the demo falls back to
// estimated positions, shown with a badge.
val octetOpenCellIdKey: String = localProps.getProperty("octet.openCellIdKey", "")

val octetSdkVersion: String = (project.findProperty("octetSdkVersion") as String?) ?: "2.0.0"

android {
    namespace = "com.octetproof.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.octetproof.sample"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "OCTET_LICENSE_KEY", "\"$octetLicenseKey\"")
        buildConfigField("String", "OCTET_ACTIVATION_SERVER_URL",
            "\"$octetActivationServerUrl\"")
        // Play Integrity cloud project number (0L = disabled).
        buildConfigField("Long", "OCTET_PI_CLOUD_PROJECT", "${octetPiCloudProject}L")
        // OpenCelliD key for the cell demo (empty = estimated fallback).
        buildConfigField("String", "OPENCELLID_API_KEY", "\"$octetOpenCellIdKey\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.octetproof:sdk:$octetSdkVersion")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // AppCompat supplies the activity's DayNight NoActionBar theme; Compose renders inside it.
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // OpenStreetMap map view. Free, no API key.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
