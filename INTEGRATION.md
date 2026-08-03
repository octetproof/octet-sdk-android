# OctetSDK for Android — Integration Prerequisites

What every consumer app needs to provide for the SDK to start cleanly.
The SDK can't ship most of these on the host's behalf — they live in
the host app manifest / runtime by platform mandate.

---

## Runtime permissions

The SDK's `AndroidManifest.xml` contributes exactly these via manifest-merge —
you don't copy them into your own manifest. Use this table to complete your Play
Console permission declarations + Data Safety form:

| Permission | Why the SDK needs it | Kind | Play notes |
|---|---|---|---|
| `INTERNET` | License activation (`api.octetproof.com/v1/activate`) + proof upload | install | — |
| `ACCESS_FINE_LOCATION` | Core — the location the SDK proves | runtime | request before `Octet.start(...)` |
| `ACCESS_COARSE_LOCATION` | Country-tier proofs / fallback | runtime | paired with fine |
| `ACTIVITY_RECOGNITION` | Motion classification (proof confidence) | runtime (API 29+) | disclose in Data Safety |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Proof generation runs as an **in-use** foreground service | install | declare the `location` FGS type; no video needed (in-use) |
| `WAKE_LOCK` | Keep the device responsive during long fix windows | install | — |

**Intentionally NOT declared (as of SDK 1.2.0, #154):**
`ACCESS_BACKGROUND_LOCATION` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. The
supported flow is on-demand, **foreground** proof generation — every proof is
taken while your app is in the foreground (e.g. on a login / consent screen), so
neither is needed. Both are Google Play review landmines (mandatory
background-location review; restricted battery permission with a narrow
allowed-use list), so the SDK no longer merges them into your app. If a future
release adds background proof generation it will be **opt-in**, not a blanket
merge.

The SDK also deliberately does **not** declare media / external-storage
permissions (`READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`):
all SDK file I/O is to the app's internal `filesDir` / `cacheDir`, which needs no
permission.

**Runtime permissions to request from the user** (Android 6 / API 23+):

| Permission | When to request | Notes |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Before `Octet.start(...)`. | SDK refuses to start without it. |
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

## Usage telemetry

The SDK collects **aggregate, privacy-preserving usage counters** — e.g. how many
proofs were generated, uploaded, or couldn't be produced, by coarse level and
region type — and reports them to the license backend, indexed by your license.
This is **on by default**; disable it with
`OctetConfig(licenseKey = …, telemetryEnabled = false)`.

The counters contain **no location data** — no coordinates, region IDs, or proof
contents; only aggregate integers and coarse enum labels. They're buffered in an
encrypted file in the app's private storage and uploaded at most once a day (plus
a best-effort flush when the app backgrounds); the SDK schedules no background
work for this. Disabling deletes any buffered file.

---

## Data collection disclosure (for your Play Data Safety form)

Android has no in-artifact privacy manifest (that's an iOS concept), so this is the
authoritative statement of what the SDK handles — use it to complete your app's Play
Console **Data safety** form. iOS ships the equivalent as a bundled
`PrivacyInfo.xcprivacy` inside the xcframework.

| Data | Collected (leaves device) | Purpose | Linked to identity | Tracking |
|---|---|---|---|---|
| Precise location | Only if you set `proofUploadUrl` (proofs uploaded) | App functionality (location proofs) | No¹ | No |
| Coarse location | Only if you set `proofUploadUrl` | App functionality (country-tier proofs) | No¹ | No |
| Device ID (device fingerprint) | Yes — license activation + bound into proofs | App functionality / anti-fraud | No¹ | No |
| Product interaction (usage counters) | Yes, unless `telemetryEnabled = false` | Analytics — aggregate counters, **no location** | No | No |

¹ The device fingerprint is a pseudonymous per-install value, not an account identity;
the SDK itself does not link this data to a user identity. If **your** app associates
proofs with a user account, classify accordingly on your own form.

No data is used for advertising or cross-app/site tracking. With `proofUploadUrl` unset
(the default), no location leaves the device via the SDK at all.

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

## Device attestation

Every signed proof carries a hardware-backed **device attestation** via Google
Play Integrity, so a relying party can confirm the proof came from a genuine app
instance on a genuine device. No integration code is required; it is part of
proof generation. How often a fresh attestation is produced is configurable via
`OctetConfig.advanced.attestationCadence` — `PerSession`, `Periodic(intervalSeconds)`
(default), or `PerProof` (highest assurance, highest cost).

Verifying a Play Integrity token needs a Google Cloud project. By default the SDK
uses the project linked to your app in the Play Console. To bind a specific one,
set `OctetConfig.advanced.playIntegrityCloudProjectNumber` to your Google Cloud
project **number** (not the project ID).

### Bootstrapping your verifier — `attestationEnrolmentBundle()`

`Octet.attestationEnrolmentBundle()` returns this device key's
`AttestationEnrolmentBundle` (`jsonString()` / `protoData()`):

```kotlin
Octet.attestationEnrolmentBundle()?.let { bundle ->
    val json = bundle.jsonString()   // canonical v:1 envelope
    // POST json to your verifier's enrolment endpoint
}
```

On Android this is provided **for API symmetry with iOS**: every Android proof
already self-carries its full hardware root (the Key Attestation certificate
chain), so a verifier does not need a separate enrolment step — use the bundle
only if your enrolment flow wants the root out-of-band. It returns `null` until
the device key has been attested (after the first proof of the install). The call
is cheap and local (a Keystore read); the bundle is attestation evidence, not a
secret.

### Handling an unsupported-version error

`Octet.start(...)` can throw `LicenseError.UpgradeRequired(minVersion, message)`
when the backend stops supporting the running SDK version. Handle it by prompting
the user to update the app; a live session already running is unaffected.
`LicenseStatus` also exposes non-fatal hints — `upgradeRecommended` and
`minSupportedVersion` — to nudge an upgrade before the hard cutoff. (Version gating
is dormant until enabled server-side, so you will not see these in 1.2.1 yet —
wiring the handler now keeps you ready.)

---

## Session-binding (per-login proofs)

To turn a location proof into an authentication factor — "in this region, *for this
login, right now*" — pass the one-time nonce your login backend issued as
`sessionNonce`:

```kotlin
val verdict = sdk.loc.isWithin(OctetRegion.country("US"), Instant.now(),
                               sessionNonce = loginNonce)
// forward verdict.proof to your login backend, which verifies the binding
```

The SDK commits `SHA256("octet-session-binding-v1" ‖ len ‖ nonce)` into the signed
proof and forces a fresh (uncached) proof — **the raw nonce never leaves the
device**. Your verifier (octet-verify ≥ 1.2.0), given the same expected nonce,
confirms the proof was made for that specific login; an older verifier simply
ignores the binding (NOT-CHECKED). `sessionNonce` must be **1…512 bytes** — empty or
larger returns an `invalidSessionNonce` verdict with no proof and no network call.
Omit it entirely for normal, cacheable proofs (behaviour is unchanged from 1.1.0).

---

## Interpreting a verdict — reason codes & achievable level

`isWithin` / `isOutside` / `contains` return an `OctetVerdict` whose `result` is a
trichotomy — `YES` / `NO` / `INDETERMINATE`. `INDETERMINATE` means "can't answer
right now"; never silently treat it as `NO`. The `reason` says why:

| Reason | Meaning | Typical handling |
|---|---|---|
| `INSUFFICIENT_PRECISION` | Conditions can't support a proof at the requested precision. `achievableLevel` names the best level the SDK *could* reach. | Re-request at `achievableLevel`, or apply your own fallback — the SDK never silently down-levels. |
| `SPOOFING_DETECTED` / `TAMPERING` | A positive security signal — suspected spoofing, or device tampering. | Treat as untrusted; don't retry blindly. |
| `NO_FIX` / `STALE_FIX` / `NO_PROOF_AT_RESOLUTION` | No fresh fix yet / time outside the proof's validity window / cached proof too coarse for the query. | Retry shortly. |

When `result` is `INDETERMINATE` with reason `INSUFFICIENT_PRECISION`, read
`verdict.achievableLevel` to decide whether the coarser level is acceptable
before re-requesting.

---

## Custom log routing

The SDK emits structured log lines through a pluggable `LogSink`
interface. The platform default is `AndroidLogSink`, which forwards
into `android.util.Log`.

Implement `LogSink` and pass it via `OctetConfig.logSink` to route the
SDK's log lines into your own observability pipeline. Release builds
gate logcat emission behind `BuildConfig.DEBUG`
so coordinates and license fragments do not appear in plain text in
a released build's logcat output; a released SDK does not write to the host
app's logcat at all — the internal diagnostic stream is emitted only by a
debug build of the SDK.

---

## Supported ABIs

The native particle-filter library ships for `arm64-v8a` in this
release. Other ABIs (`armeabi-v7a`, `x86_64`, `x86`) are not
supported; gradle resolution will fail at link time for consumer apps
targeting those ABIs.

---

## Verifying your OctetSDK download

Every release publishes a SHA-256 manifest and a build-provenance
attestation so you can confirm the library you pulled is the genuine,
unmodified Octet artifact built by our release pipeline.

**Gradle verifies the library automatically.** Each artifact on the
`mvn-repo` branch ships with a `.sha256` next to it, which Gradle checks
on resolution — so the normal Maven-based install needs no extra step.

**Manual verification.** The release also attaches the published AARs
(`sdk-<version>.aar` plus the transitive `libpf-<version>.aar`), a
consolidated `SHASUMS256.txt`, and a provenance bundle
(`octet-android.sigstore.json`). Download them into one directory, then:

```sh
# 1. Confirm the bytes match the published SHA-256.
shasum -a 256 -c SHASUMS256.txt

# 2. Confirm the checksums were signed by the official release workflow
#    (keyless Sigstore signature over the manifest).
cosign verify-blob \
  --certificate SHASUMS256.txt.pem \
  --signature SHASUMS256.txt.sig \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp '^https://github.com/octetproof/octet-sdk/\.github/workflows/release-android\.yml@' \
  SHASUMS256.txt

# 3. Confirm the AAR was built by the official release workflow.
gh attestation verify sdk-<version>.aar \
  --bundle octet-android.sigstore.json \
  --repo octetproof/octet-sdk
```

Steps 1–2 (checksum + keyless cosign signature) are the required verification and
must both report success. Step 3 (`gh attestation verify`) applies only when a
`.sigstore.json` build-provenance bundle is attached to the release — 1.2.1 ships
**without** one (a private-source-repo limitation, tracked in `octetproof/octet-sdk#169`),
so skip step 3 if no bundle is present. Steps 2–3 use the attached files offline —
the GitHub CLI and cosign are needed, but no special repository access.

---

## Reference implementation

The sample app in [`sample/`](sample/) exercises the minimum viable
permission flow if you need a reference.

---

## Updates to this document

Updates to this document arrive with each SDK release. Re-check it
when upgrading.
