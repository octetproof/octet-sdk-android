# OctetSDK for Android — Integration Prerequisites

What every consumer app needs to provide for the SDK to start cleanly.
The SDK can't ship most of these on the host's behalf — they live in
the host app manifest / runtime by platform mandate.

---

## Runtime permissions

The SDK's `AndroidManifest.xml` already declares everything via
manifest-merge:

- `INTERNET` — license activation hits `api.octetproof.com/v1/activate`.
- `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`,
  `ACCESS_BACKGROUND_LOCATION` — location pipeline (background is a
  first-class SDK feature; see below for the runtime prompt order).
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` — proof generation
  runs as a foreground service.
- `WAKE_LOCK` — keep the device responsive during long fix windows.
- `ACTIVITY_RECOGNITION` — motion classification.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — continuous proof generation
  across Doze / App Standby (the SDK won't prompt automatically;
  integrators raise the standard Settings intent if they want the
  exemption).

Integrators don't need to copy these into their own manifest. The SDK
deliberately does **not** declare media or external-storage
permissions (`READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`,
`MANAGE_EXTERNAL_STORAGE`): all SDK file I/O is to the app's internal
`filesDir` / `cacheDir`, which needs no permission.

**But runtime permissions still need to be requested from the user**
on Android 6 (API 23)+:

| Permission | When to request | Notes |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Before `Octet.start(...)`. | SDK refuses to start without it. |
| `ACCESS_BACKGROUND_LOCATION` | After `ACCESS_FINE_LOCATION` is granted, only if background proofs are wanted. | Android 10+ requires this as a separate prompt. |
| `ACTIVITY_RECOGNITION` | Before `Octet.start(...)`. | Android 10+. Motion-classification features degrade gracefully if denied, but request it for full proof confidence. |

---

## Proof-upload data handling

When you enable proof-upload by setting `OctetConfig.proofUploadUrl`,
the SDK transmits each generated `LocationProof` envelope (proof bytes
+ license id + opaque device fingerprint hash) to the configured
backend. Default off; no proof leaves the device unless the URL is set.

If you point at Octet-hosted `api.octetproof.com`, **uploaded proofs
are retained at most ~24h solely to enable verification, then
permanently deleted; no long-term storage, no backups.** This window
exists so a verifier can audit a freshly-generated proof — it is an
ephemeral verification buffer, not an archive. If your application
needs a longer-lived record of a proof, fetch it from the backend
within the retention window and persist it yourself.

---

## Opt-in TLS certificate pinning

The SDK ships with a public-key pin set for `api.octetproof.com` (the
certificate-authority intermediate plus a backup pin), exposed as a
bundled `network_security_config.xml` resource. Pinning is **off by
default**; opt in by referencing the bundled config from your
`AndroidManifest.xml`'s `<application>` element:

```xml
android:networkSecurityConfig="@xml/octet_network_security_config"
```

Android's network-security-config is manifest-bound at build time, so
the integrator opts in by including the reference in their app's
manifest. Default-off keeps consumers who haven't opted in from seeing
pinning failures surface as opaque connection errors. The pin set is
rotated in lockstep with backend certificate rotations.

---

## Reading the device-key security tier

Every signed proof envelope carries the actual `DeviceKeySecurityLevel`
of the device key used to sign it. Three possible values:

| Level | Meaning |
|---|---|
| `HARDWARE_STRONGBOX` | Tamper-resistant secure element (Android StrongBox, when the device supports it) |
| `HARDWARE_TEE` | Android Keystore without StrongBox (TEE-backed) |
| `SOFTWARE` | Software-stored key (fallback for devices without hardware-backed key storage) |

The level is exposed via the SDK's public attestation surface so a
relying party (your own verifier or the standalone `octet-verify` CLI)
can decide what to accept per the trust requirements of the
integration. The SDK does not refuse to operate when only `SOFTWARE`
storage is available — it generates honest proofs at the level
actually achieved, and the acceptance decision lives at the verifier.

---

## Custom log routing

The SDK emits structured log lines through a pluggable `LogSink`
interface. The platform default is `AndroidLogSink`, which forwards
into `android.util.Log`.

Implement `LogSink` and pass it via `OctetConfig.logSink` to route the
SDK's log lines into your own observability pipeline. Release builds
gate logcat emission behind `BuildConfig.DEBUG || OctetConfig.debugMode`
so coordinates and license fragments do not appear in plain text in
release-build logcat output; use the SDK's debug-mode toggle if you
need them visible during development.

---

## Supported ABIs

The native particle-filter library ships for `arm64-v8a` in this
release. Other ABIs (`armeabi-v7a`, `x86_64`, `x86`) are not
supported; gradle resolution will fail at link time for consumer apps
targeting those ABIs.

---

## Reference implementation

The sample app in [`sample/`](sample/) exercises the minimum viable
permission flow if you need a reference.

---

## Updates to this document

Updates to this document arrive with each SDK release. Re-check it
when upgrading.
