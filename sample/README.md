# OctetSample — Android sample

A Jetpack Compose demo of the public v1 SDK surface, built around two
tabs:

- **Generate** — pick a region, watch the proof pipeline run (*SDK
  initialized → sensors warmed up → location fixed → proof generated*,
  with per-step timings and a map of your position), and get the
  predicate answer (`sdk.loc.isWithin(...)`). The result card shows the
  outcome, a confidence bucket, and the battery consumed by the run.
  Generated proofs are kept in a sample-owned store.
- **Verify** — run `Octet.verify(...)` on any stored or imported proof
  and read the grouped on-device checks (signature, freshness,
  hardware-attestation, …), with a one-line plain-language note on every
  check that couldn't run and how to make it run. Pick a region to check
  the proof's claim against, and see the granularity the proof reveals
  (nothing finer than its level). Proofs can be exported / imported as
  `.octetproof` files via the Android share/Storage-Access layer —
  byte-compatible with the iOS build's format.

## Setup (activation)

**The SDK activates by attesting the app instance — there is no license key
to paste.** On a real device it uses **Google Play Integrity and hardware key
attestation** and bootstraps its licence automatically on first launch. Copy
the local config first:

```bash
# From this directory:
cp local.properties.example local.properties
# Open local.properties and set octet.activationServerUrl. Optionally set
# octet.playIntegrityCloudProjectNumber (your own Google Cloud project number)
# to add a Play Integrity verdict to generated proofs — not required to activate.
```

`local.properties` is gitignored.

**Run it on a real device — this sample is prod-only.** On a device with a
hardware-backed keystore, the SDK attests via **hardware key attestation** and
bootstraps on first launch. An emulator or CI can't produce production
attestation; the SDK's sandbox-bootstrap path for those environments is covered
in the repository's `INTEGRATION.md` but is intentionally not wired into this
sample. Sign up at [octetproof.com](https://octetproof.com).

## Build

This sample is a standalone Gradle project; the SDK
(`com.octetproof:sdk:<version>`) resolves from the Octet Maven repository
configured in `settings.gradle.kts`. From this directory:

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:installDebug           # build + install on a connected device
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Package: `com.octetproof.sample`

> The proof pipeline needs real sensors: GNSS, cellular, Wi-Fi, and
> motion inputs are only present on a **physical device** — an emulator has
> no real GNSS / cellular / motion hardware.

## Files

Compose UI + a single `AndroidViewModel` (`SampleViewModel`) that owns
the SDK lifecycle, the generate pipeline, verify, region selection, and
the map location, under `app/src/main/java/com/octetproof/sample/`:

| Area | Files |
|---|---|
| App shell / model | `MainActivity.kt`, `RootScreen.kt`, `SampleViewModel.kt`, `AppSettings.kt` |
| Generate | `GenerateScreen.kt` (pipeline, map, region sheet, result, curated debug feed) |
| Verify + proofs | `VerifyScreen.kt`, `ProofStore.kt`, `Models.kt`, `Regions.kt` |
| Shared | `Theme.kt`, `Scaffolds.kt` |
