import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Per-developer license key: read from `sample/local.properties`
// (gitignored). See `local.properties.example` for the line to add.
// Missing key → empty string → app throws LicenseError.MalformedKey on
// Octet.start with a clear cause.
val octetLicenseKey: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    props.getProperty("octet.licenseKey", "")
}

val octetSdkVersion: String = (project.findProperty("octetSdkVersion") as String?) ?: "0.0.2"

android {
    namespace = "com.octetproof.toy.v1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.octetproof.toy.v1"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "OCTET_LICENSE_KEY", "\"$octetLicenseKey\"")
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
