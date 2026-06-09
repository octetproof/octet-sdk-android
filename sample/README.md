# app-v1-toy — Android dev sample

Minimal demo app exercising only the public v1 SDK surface
(`Octet.start(...)` + `sdk.loc.isWithin(...)`). One button, one
verdict. Pairs with `samples-public/ios-sample/` on the iOS side.

This is the **dev-time copy**. It consumes the SDK via the Gradle
`project(":sdk")` project dependency, so SDK changes propagate
immediately — no release roundtrip required. Use it to smoke-test new
SDK features as you write them.

A **consumer-facing copy** lives at `octet-sdk-android/sample/` in
the distribution repo. It consumes the published AAR via the maven
URL (`com.octetproof:sdk:<version>`); the release workflow mirrors
source changes from here on every tagged release (see
`.github/workflows/release-android.yml`).

## Setup (license key)

The SDK won't start without a v1 license key. Get one at
[api.octetproof.com/signup](https://api.octetproof.com/signup), or
issue one from a local backend per `REAL_DEVICE_TESTING.md`. Then:

```bash
# In samples-public/android-sample/ (preferred):
cp local.properties.example local.properties
# Open local.properties and paste your key into the octet.licenseKey line.
```

Alternatively, put the line in the Gradle multi-project's root
`octet-sdk/android/local.properties` — the build checks both
locations and prefers the sample-local one. Either works; sample-local
keeps the toy's license key separate from the wider Android SDK
config (Android Studio writes `sdk.dir` into the root file too).

`local.properties` is gitignored in either location. The build wires
the key into `BuildConfig.OCTET_LICENSE_KEY`; the toy reads that
constant. Missing key → empty string → runtime
`LicenseError.MalformedKey` at `Octet.start` (loud, clear) — *not* a
build failure.

## Build

From the `android/` root:

```bash
source environ.sh                            # sets JAVA_HOME and ANDROID_HOME
./gradlew :app-v1-toy:assembleDebug          # build
./gradlew :app-v1-toy:installDebug           # build + install on connected device
```

The Gradle module is wired into `android/settings.gradle.kts` with an
explicit `projectDir` override pointing at this directory:

```kotlin
include(":app-v1-toy")
project(":app-v1-toy").projectDir = file("../samples-public/android-sample")
```

APK output: `samples-public/android-sample/build/outputs/apk/debug/app-v1-toy-debug.apk`

Package: `com.octetproof.toy.v1`

## What it does

1. Requests `ACCESS_FINE_LOCATION`.
2. Calls `Octet.start(this, OctetConfig(licenseKey =
   BuildConfig.OCTET_LICENSE_KEY))` (suspending) inside
   `lifecycleScope.launch { … }`. The SDK verifies the license key,
   hits `/v1/activate` if needed, caches the activation token, then
   brings up the proof pipeline.
3. On tap, runs `sdk.loc.isWithin(OctetRegion.country("US"),
   Instant.now())` and renders the verdict (`result` / `reason` /
   `message` / whether a proof attached).

Source: `src/main/java/com/octetproof/toy/v1/MainActivity.kt`.
