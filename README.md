# OctetSDK for Android

> **Cryptographically verifiable location proofs.** Every proof the SDK
> generates is a signed envelope a relying party can verify independently
> with the standalone [`octet-verify`](https://github.com/octetproof/octet-verify)
> CLI — no need to trust the SDK at runtime. Hardware-backed device keys
> where available (StrongBox on Android, Secure Enclave on iOS), with the
> actual key-storage tier reported in every proof so verifiers can decide
> what to accept.

## Quick start

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
    implementation("com.octetproof:sdk:1.2.0")
}
```

In your app code (`Octet.start` and `sdk.loc.isWithin` are `suspend`
functions; call them from a coroutine scope, e.g. `lifecycleScope.launch`):

```kotlin
import com.octetproof.sdk.api.Octet
import com.octetproof.sdk.api.OctetConfig
import com.octetproof.sdk.api.OctetRegion

val sdk = Octet.start(this, OctetConfig(
    licenseKey = "octet_live_v4.public..."  // from sdk.octetproof.com/signup
))

val verdict = sdk.loc.isWithin(OctetRegion.country("US"))
// verdict.result, verdict.reason, verdict.proof (LocationProof)
```

## How it works

The SDK runs a sensor-fusion + anti-spoofing pipeline on-device and emits
a `LocationProof` envelope as the cryptographic output of the
`sdk.loc.isWithin(...)` predicate. The envelope is signed by a
hardware-backed device key (StrongBox / TEE / software-backed, honestly
reported per-proof via `DeviceKeySecurityLevel`).

A relying party verifies the proof with the standalone
[`octet-verify`](https://github.com/octetproof/octet-verify) CLI or by
calling the Octet-hosted backend's verify endpoint. This lets a consumer
separate proof generation from proof acceptance — the SDK signs, an
independent verifier accepts.

## Getting a license key

OctetSDK requires a valid license key to start. Sign up at
[sdk.octetproof.com/signup](https://sdk.octetproof.com/signup) — a free
trial key works for evaluation.

## Requirements

- `minSdk` 30+
- `compileSdk` 34+
- Kotlin 2.1+
- AndroidX

## Host-app integration prerequisites

The SDK's `AndroidManifest.xml` declares the permissions it needs
(INTERNET, location, motion sensors, foreground service, etc.) and they
propagate into consumer apps via manifest-merge automatically — you
don't need to redeclare them.

You **do** still need to request the runtime permissions before calling
`Octet.start(...)`. See [INTEGRATION.md](INTEGRATION.md) for the full
integration guide — runtime permission flow, device attestation (Play
Integrity), optional usage telemetry, opt-in TLS certificate pinning via
`network_security_config`, log routing, verdict reason codes, and reading
the per-proof `DeviceKeySecurityLevel`.

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
