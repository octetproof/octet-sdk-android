# OctetSDK for Android

Binary distribution of the Octet SDK for Android — Maven manifests plus
tagged AAR releases hosted on the orphan
[`mvn-repo`](../../tree/mvn-repo) branch of this repository.

> ⚠️ **`0.0.1-alpha` is deprecated.** The v1 license-key schema cutover
> shipped in **`0.0.2-alpha`** (2026-06-04). Tokens issued by the current
> production backend will fail to verify on `0.0.1-alpha` with
> `LicenseError.VerificationFailed(UnsupportedSchema)` at `Octet.start`.
> Upgrade to `0.0.2-alpha` or later. See [CHANGELOG.md](CHANGELOG.md)'s
> `[0.0.2-alpha]` entry for details.

## Installation

In your project's root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://raw.githubusercontent.com/octetproof/octet-sdk-android/mvn-repo")
        }
    }
}
```

In your app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.octetproof:sdk:0.0.2-alpha")
}
```

## Getting a license key

OctetSDK requires a valid license key to start. Sign up at
[sdk.octetproof.com/signup](https://sdk.octetproof.com/signup) to obtain
one — a free trial key works for evaluation.

## Requirements

- `minSdk` 30+
- `compileSdk` 34+
- Kotlin 2.1+
- AndroidX

## Host-app integration prerequisites

The SDK's `AndroidManifest.xml` declares the permissions it needs (INTERNET,
location, motion sensors, foreground service, etc.) and they propagate into
consumer apps via manifest-merge automatically — you don't need to redeclare
them.

You **do** still need to request the runtime permissions before calling
`Octet.start(...)`. See [INTEGRATION.md](INTEGRATION.md) for the full list
and the conditional permissions for background-location use.

Minimum runtime requests:

- `ACCESS_FINE_LOCATION` — SDK refuses to start without it.
- `ACTIVITY_RECOGNITION` — needed for motion-classification features
  (graceful degradation if denied, but request it for full confidence).

## Sample app

A standalone demo lives in [`sample/`](sample/) — clone, drop in your
license key, build. See [`sample/README.md`](sample/README.md) for both
command-line and Android Studio workflows.

## License

See [LICENSE](LICENSE).
