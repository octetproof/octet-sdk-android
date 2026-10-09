// Root build of the PUBLIC consumer sample (octet-sdk-android `sample/`).
// Source of truth for the public repo; release-android.yml copies this
// directory over `sample/` on every SDK release. Toolchain matches the SDK
// build (android/build.gradle.kts + android/gradle.properties): AGP 8.9.1 on
// Gradle 8.11.1, compileSdk 36.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
}
