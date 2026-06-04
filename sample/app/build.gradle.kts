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
// running the SDK against a local backend per REAL_DEVICE_TESTING.md
// (LAN-hosted dev backend). API 28+ blocks cleartext to non-loopback
// IPs without a network-security-config exception.
val (octetLicenseKey: String, octetActivationServerUrl: String) = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    val key = props.getProperty("octet.licenseKey", "")
    val url = props.getProperty("octet.activationServerUrl", "https://api.octetproof.com")
    key to url
}

val octetSdkVersion: String = (project.findProperty("octetSdkVersion") as String?) ?: "0.0.2-alpha"

android {
    namespace = "com.octetproof.toy.v1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.octetproof.toy.v1"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Exposed at runtime as BuildConfig.OCTET_LICENSE_KEY +
        // BuildConfig.OCTET_ACTIVATION_SERVER_URL.
        buildConfigField("String", "OCTET_LICENSE_KEY", "\"$octetLicenseKey\"")
        buildConfigField("String", "OCTET_ACTIVATION_SERVER_URL",
            "\"$octetActivationServerUrl\"")
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
