import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Per-developer license key + (optional) activation server URL: read
// from `sample/local.properties` (gitignored). See
// `local.properties.example` for the lines to add.
//
// Missing key → empty string → app throws LicenseError.MalformedKey
// on Octet.start with a clear cause.
//
// `activationServerUrl` defaults to production. Override only when
// running the SDK against your own self-hosted activation backend
// on a LAN host. API 28+ blocks cleartext to non-loopback IPs by
// default — either use https or add a network-security-config
// exception in your app's manifest for the LAN host.
val (octetLicenseKey: String, octetActivationServerUrl: String, octetPiCloudProject: String) = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    val key = props.getProperty("octet.licenseKey", "")
    val url = props.getProperty("octet.activationServerUrl", "https://api.octetproof.com")
    // (Optional) Google Cloud project NUMBER for Play Integrity. Absent / empty /
    // non-numeric → "0" → the SDK skips Play Integrity. Use YOUR OWN project.
    val pi = (props.getProperty("octet.playIntegrityCloudProjectNumber") ?: "")
        .trim().toLongOrNull()?.toString() ?: "0"
    Triple(key, url, pi)
}

val octetSdkVersion: String = (project.findProperty("octetSdkVersion") as String?) ?: "0.0.2-alpha"

android {
    namespace = "com.octetproof.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.octetproof.sample"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Exposed at runtime as BuildConfig.OCTET_LICENSE_KEY +
        // BuildConfig.OCTET_ACTIVATION_SERVER_URL.
        buildConfigField("String", "OCTET_LICENSE_KEY", "\"$octetLicenseKey\"")
        buildConfigField("String", "OCTET_ACTIVATION_SERVER_URL",
            "\"$octetActivationServerUrl\"")
        // Play Integrity cloud project number (0L = disabled). Set your own via
        // octet.playIntegrityCloudProjectNumber in local.properties.
        buildConfigField("Long", "OCTET_PI_CLOUD_PROJECT", "${octetPiCloudProject}L")
    }

    buildFeatures {
        buildConfig = true
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

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // OpenStreetMap tile renderer for the live device-location map.
    // Free, no API key required.
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
