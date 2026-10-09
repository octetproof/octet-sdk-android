# OctetSDK for Android — Integration Prerequisites

What every consumer app needs to provide for the SDK to start cleanly.
The SDK can't ship most of these on the host's behalf — they live in
the host app manifest / runtime by platform mandate.

---

## Activation

As of 2.0.0 the SDK activates by **attesting the device**: at `Octet.start` it
proves the device with a **hardware key-attestation certificate chain**
(StrongBox/TEE, rooting to Google's hardware attestation root) and bootstraps its
licence from the Octet backend. There is no license key to paste; a device that
cannot attest and carries no sandbox token fails closed.

**What you need**

- A real device with a **hardware-backed keystore** (StrongBox or TEE). An
  emulator can't produce production attestation.
- Your app registered with Octet, so the backend recognises it. Sign up at
  [octetproof.com](https://octetproof.com).

`OctetConfig.licenseKey` is still a field on the config type, but as of 2.0.0 it
is **no longer the credential that activates the SDK** — it is retained for
source compatibility and will be removed in a future release. (This is separate
from Play Integrity, which remains an optional per-proof device attestation — see
"Device attestation" below.)

### Emulator, CI, and non-attesting builds — sandbox bootstrap

A build that can't produce production key attestation — an emulator, a CI runner,
or a device without a hardware-backed keystore — activates instead with a
**sandbox bootstrap token** set on `OctetConfig.sandboxBypassToken`, which you
self-serve from your account at [octetproof.com](https://octetproof.com). A
production (Play Store) build cannot use a sandbox token: the SDK refuses it
before the request is built, and the backend rejects a sandbox token for a
production app row.

---

## Runtime permissions

The SDK's `AndroidManifest.xml` contributes exactly these via manifest-merge —
you don't copy them into your own manifest. Use this table to complete your Play
Console permission declarations + Data Safety form:

| Permission | Why the SDK needs it | Kind | Play notes |
|---|---|---|---|
| `INTERNET` | License activation (attested bootstrap) + proof upload | install | — |
| `ACCESS_FINE_LOCATION` | Core — the location the SDK proves | runtime | request before `Octet.start(...)` |
| `ACCESS_COARSE_LOCATION` | Country-tier proofs / fallback | runtime | paired with fine |
| `ACTIVITY_RECOGNITION` | Motion classification (proof confidence) | runtime (API 29+) | disclose in Data Safety |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Proof generation runs as an **in-use** foreground service | install | declare the `location` FGS type; no video needed (in-use) |
| `WAKE_LOCK` | Keep the device responsive during long fix windows | install | — |

**Intentionally NOT declared (as of SDK 1.2.0):**
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

The SDK collects **aggregate, privacy-preserving usage telemetry** and reports it
to the license backend (`POST /v1/metrics`), indexed by the license the SDK was
activated with. It is **on by default** and disclosed under the Octet Terms &
Conditions; disable it entirely by setting `telemetryEnabled = false` on your
`OctetConfig`.

**What it never contains.** No coordinates, no positions, no region geometry, no
proof bytes, no message text — **no precise location data of any kind** — and **no
new device identifier** (it reuses the opaque fingerprint established at
activation). It is **encrypted at rest** (AES-256-GCM, with a key held in the
platform keystore) and sent over TLS.

**Base counters (whenever telemetry is on).** Aggregate counts of proofs
generated / uploaded / dropped, bucketed by coarse dimensions only — proof
**level**, region **type** (country / city / …), and failure **stage** — plus the
SDK version and platform. Buffered in a single rolling file and uploaded at most
once a day (with a best-effort flush when the app backgrounds); no background
scheduler.

**Additional diagnostic signals (remotely gated — off unless Octet enables
them).** When enabled by remote configuration, the SDK adds a set of richer
**aggregate** signals:

- **Per-proof events** — one flat record per proof, every field a bucketed **enum
  label** (the proof level and internal quality labels). The one field beyond pure enums is a coarse
  **region identity** — an **ISO 3166 country / subdivision code** (e.g. `US`,
  `GB-ENG`), never finer than the level the proof already claims and bounded to
  ISO 3166 space, not free text. Still no coordinates.
- **Error diagnostics** — a count of `(error type, call site)` pairs for errors
  caught at the SDK's public API boundary, with any message mapped to a fixed enum
  (`license` / `region_decode` / … / `unknown`) — never the raw message, never
  PII; capped at 50 distinct pairs.

**Your control.** All of the above — base and gated — stops entirely when
`telemetryEnabled = false`: no counters are recorded, the persisted file is
deleted, and `/v1/metrics` is never called.

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
| Product interaction (daily usage record) | Yes, always: one record per device per day | Billing (active-device count), **no location** | No | No |

¹ The device fingerprint is a pseudonymous per-install value, not an account identity;
the SDK itself does not link this data to a user identity. If **your** app associates
proofs with a user account, classify accordingly on your own form.

No data is used for advertising or cross-app/site tracking. With `proofUploadUrl` unset
(the default), no location leaves the device via the SDK at all.

---

## Usage reporting

Octet bills per active device. To count devices, the SDK sends **one usage record
per device per UTC day**: after the first proof the app generates that day, it
queues a single `device_active` record and sends it with the next background
heartbeat. Later proofs that day send nothing more.

**What it contains.** The operation name, a timestamp and a record ID. The ID is a
one-way SHA-256 hash of the device's opaque activation fingerprint and the date, so it
changes every day and lets the server ignore duplicates. The device is counted from the
activation credentials the SDK already holds. **No location, no coordinates, no proof
data, no user or account identity, and no new device identifier.**

**It never affects a proof.** The record is written after the proof exists and needs
no network at that moment. A failed send is retried on later heartbeats, and a record
that could not be sent within 30 days is dropped. A sandbox session sends nothing.

**It is always on.** Reporting can't be switched off. `OctetConfig.creditServiceUrl` defaults to
`https://credits.octetproof.com`; set it only to point a test build at another
environment. Setting it to `null` does not disable reporting.

---

## Routing all SDK traffic through your own gateway

By default the SDK talks to the Octet backend at `api.octetproof.com` and, for a
couple of enrichment calls, to a third-party host (IP-geolocation on iOS; GNSS
broadcast-ephemeris servers on Android). If your app's network surface is audited
— a firewall allowlist, an app-store network disclosure, or a "the app only talks
to our own domain" policy — you can route **every** SDK request through a reverse
proxy you operate, so the only backend the app visibly contacts is yours.

Choose the mode at `start()`:

```kotlin
val config = OctetConfig(
    licenseKey = "octet_live_v4.…",
    advanced = AdvancedConfig(
        transport = TransportPolicy(
            mode = TransportPolicy.Mode.INTEGRATOR_GATEWAY,
            gateway = "https://api.example.com/octet")))
```

The SDK then sends every request to `{gateway}{path}`; your proxy forwards each
path to its upstream and returns the response unchanged. Activation, verification,
and proof/flag signing still terminate at Octet — the proxy is a plain forwarder,
so trust is unchanged and only the network topology moves.

### Consolidate onto Octet's own gateway instead (`OCTET_GATEWAY`)

If you want a single consolidated backend domain but would rather **not run a proxy
yourself**, select `OCTET_GATEWAY`. The SDK routes every call — first-party *and* the
third-party enrichment — through Octet's own hosted gateway, so
the app's only backend domain becomes that one Octet host:

```kotlin
val config = OctetConfig(
    licenseKey = "octet_live_v4.…",
    advanced = AdvancedConfig(transport = TransportPolicy.octetGateway))
```

There is **no `gateway` to supply** — the host is built in, and any value you set is
ignored — and nothing for you to operate. `OCTET_GATEWAY` uses standard
certificate-authority validation (TLS pinning is **off**); the `pins` field is ignored
in this mode — it applies only to the app→proxy hop of `INTEGRATOR_GATEWAY`. The
`fallbackToDirect` option and the `/ext/geo/…` note below apply here just the same. Everything from here on describes `INTEGRATOR_GATEWAY`, the
run-your-own-proxy mode.

### What your proxy must forward

| The SDK sends (under your gateway) | Forward it to | Notes |
|---|---|---|
| `/v1/activate`, `/v1/heartbeat`, `/v1/deactivate` | `https://api.octetproof.com/v1/…` | signed bodies — never modify |
| `/v1/flags`, `/v1/metrics`, `/v1/credits/…` | `https://api.octetproof.com/v1/…` | flag bundles are signed — never modify |
| `/v1/proofs`, `/v1/proofs/auth`, `/v1/proofs/challenge` | `https://api.octetproof.com/v1/…` | proof body up to 256 KiB — never modify |
| `/ext/gnss/…` | `https://cddis.nasa.gov/archive/gnss/data/daily/…` (fallback `https://igs.ign.fr/pub/igs/data/…`) | **Android SDK only** — GNSS ephemeris (large files; may need NASA Earthdata credentials) |
| `/ext/geo/…` | `https://api.octetproof.com/ext/geo/…` | **both SDKs**. **First-party & bearer-authed** — forward it like a `/v1/…` route (preserve `Authorization`, send `Host: api.octetproof.com`), not like the third-party `ext` routes above. |

Forward the third-party `/ext/gnss/` route only if you embed the Android SDK; `/ext/geo/…` is first-party and applies to both.

**Contract:**

- **Preserve the request verbatim** — method, the path after the prefix, the query
  string, and all headers, especially `Authorization`, `Content-Type`, and any
  `X-Octet-…` header.
- **Never modify the body** of `/v1/proofs`, `/v1/flags`, or `/v1/activate`: each
  signature is over the exact bytes, so any rewrite — even re-serialising the JSON
  — breaks verification.
- **Use TLS 1.2 or higher to the upstream**, verify the upstream certificate, and
  send `Host: api.octetproof.com` (and matching SNI) on the first-party routes.
- **Pass status codes and the `Retry-After` header through unchanged** — the SDK
  honours `429`.
- **Don't cap the request body below 256 KiB**, and give the upstream at least as
  generous a timeout as you give the SDK (activation and attestation can be slow).

### Reference configurations

Replace `api.example.com/octet` with your own gateway base URL, and keep only the
`ext` block for the platform(s) you ship.

**nginx**

```nginx
# All first-party routes are under /v1/ — activation, flags, metrics, credits,
# proofs (incl. /v1/proofs/auth + /v1/proofs/challenge), and network-observe.
location /octet/v1/ {
    proxy_pass                  https://api.octetproof.com/v1/;
    proxy_ssl_server_name       on;
    proxy_set_header Host       api.octetproof.com;
    proxy_pass_request_headers  on;
    client_max_body_size        256k;
}
# Android apps only:
location /octet/ext/gnss/  { proxy_pass https://cddis.nasa.gov/archive/gnss/data/daily/; proxy_ssl_server_name on; }
# All apps (first-party, bearer-authed):
location /octet/ext/geo/   { proxy_pass https://api.octetproof.com/ext/geo/; proxy_ssl_server_name on; proxy_set_header Host api.octetproof.com; }
```

**Cloudflare Worker**

```js
const UPSTREAM = {
  "/v1/":        "https://api.octetproof.com/v1/",                   // all first-party (incl. /v1/proofs/auth, /v1/proofs/challenge)
  "/ext/gnss/":  "https://cddis.nasa.gov/archive/gnss/data/daily/",  // Android apps
  "/ext/geo/":   "https://api.octetproof.com/ext/geo/",              // all apps (first-party)
};

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/octet/, "");   // strip your gateway prefix
    const match = Object.entries(UPSTREAM).find(([prefix]) => path.startsWith(prefix));
    if (!match) return new Response("not found", { status: 404 });
    const [prefix, base] = match;
    const target = base + path.slice(prefix.length) + url.search;
    // Forward method, headers, and body untouched; return the upstream response as-is.
    return fetch(target, {
      method: request.method,
      headers: request.headers,
      body: request.body,
      redirect: "follow",
    });
  },
};
```

**AWS** — two common patterns:

- **API Gateway (HTTP API):** add one HTTP-proxy integration per prefix — e.g.
  route `ANY /octet/v1/{proxy+}` to `https://api.octetproof.com/v1/{proxy}`, and
  one each for `/octet/ext/geo/` (all apps) and `/octet/ext/gnss/` (Android). Leave
  request-parameter mapping untouched so headers and the query string pass through
  unchanged.
- **CloudFront:** define one origin per upstream and one cache behaviour per path
  pattern (`/octet/v1/*`, `/octet/ext/geo/*`, `/octet/ext/gnss/*`). Attach an origin-request policy that forwards **all**
  headers, query strings, and cookies, and the managed `CachingDisabled` cache
  policy so nothing is buffered or rewritten. Set each origin to HTTPS-only.

### Two things to know

- **Certificate pinning:** the default pin set (next section) pins Octet's
  certificate, which no longer applies once traffic flows through your proxy — the
  app sees **your** certificate, not Octet's. To keep pin-level protection on the
  app→gateway hop, pass your proxy's own SPKI pins in the transport policy:

  ```kotlin
  transport = TransportPolicy(
      mode = TransportPolicy.Mode.INTEGRATOR_GATEWAY,
      gateway = "https://api.example.com/octet",
      pins = listOf("<base64-sha256-of-your-proxy-SPKI>", "<backup-pin>"))
  ```

  Compute a pin with `openssl x509 -in cert.pem -pubkey -noout | openssl pkey
  -pubin -outform DER | openssl dgst -sha256 -binary | openssl enc -base64`.
  **Pin a stable point in the chain — a CA intermediate or root — not an
  auto-rotating leaf:** a managed certificate (Cloudflare, ACME / Let's Encrypt)
  re-keys on renewal, which breaks a leaf-SPKI pin the next time it rotates. Always
  include a backup pin. Leave `pins` empty (the default) to rely on standard
  certificate-authority validation only.
- **Availability — optional fallback to direct (`fallbackToDirect`, default off):**
  if you'd rather the SDK keep working through a gateway *outage* than fail closed,
  set `fallbackToDirect = true`. After repeated transport-level failures to your
  gateway (connect / TLS / DNS / timeout — never HTTP status errors), the SDK
  temporarily reverts the **critical calls** (activation, proof upload, credit) to
  Octet directly, then re-probes your gateway and switches back once it recovers.
  **This suspends the single-domain guarantee while active** — during the outage
  those calls talk to `api.octetproof.com` directly — so a strict-compliance
  deployment should leave it **off** (the default). Telemetry + flags always stay
  gateway-only.

  ```kotlin
  transport = TransportPolicy(
      mode = TransportPolicy.Mode.INTEGRATOR_GATEWAY,
      gateway = "https://api.example.com/octet",
      fallbackToDirect = true,     // opt in to outage resilience (default false)
      fallbackAfterFailures = 3)   // consecutive transport failures before it trips
  ```
- **Zero Google attestation calls, at the cost of un-attested proofs (`osAttestationInGatewayModes`, default on):**
  in a gateway mode the SDK still runs per-proof **Play Integrity** (→Google) by default —
  an OS call that can't be proxied. Set `osAttestationInGatewayModes = false` to skip it,
  so the app makes **zero** Google attestation calls. The trade-off is deliberate and
  visible on the wire: those proofs are signed by the device key but are not
  hardware-*attested*, and a verifier reports them `attested = false` — its
  attestation-required gate **fails** them. Gate your own acceptance on the proof's
  `attested` flag, **not** on validity; any "must be hardware-attested" policy belongs in
  your verifier / license issuance, not in what the SDK emits. Bootstrap/activation still
  attests, so the app still activates.

  ```kotlin
  transport = TransportPolicy(
      mode = TransportPolicy.Mode.OCTET_GATEWAY,
      osAttestationInGatewayModes = false)   // skip Play Integrity → un-attested proofs
  ```
- **Forward `/ext/geo/…` too:** without it, country and state proofs return
  `INDETERMINATE` unless `fallbackToDirect` is on.

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

## Reading a containment result's assurance tier

`contains(...)` / `isWithin(.disc(...))` return a disc-shaped area proof, and
that proof carries an **assurance tier** you should read before treating a `YES`
as authoritative. Read it from `proof.spoofingVerdict` (or `spoofing_verdict` in
`proof` JSON):

| Tier | What a `YES` means |
|---|---|
| `VERIFIED` | Corroborated: independent evidence supports the fix. |
| `PLAUSIBLE` | Lower assurance: the fix is consistent with your query but is **not** independently corroborated. |

Opt in with `OctetConfig.advanced.acceptPlausibleContainment = true` to receive a
`PLAUSIBLE` disc; with it off (the default) a disc query returns `INDETERMINATE`
rather than a lower-assurance `YES`. Gate on the tier your use case requires; do
not treat the region granularity alone as assurance.

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
is dormant until enabled server-side, so you will not see these in 2.0.0 yet —
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

## Keeping your verifier current

If your backend verifies proofs itself with `octet-verify`, upgrade it to
**octet-verify ≥ 1.5.0** before you adopt a future OctetSDK release that switches
proofs to **semantic-binding v3**. v3 also signs the region a proof was asked about,
so your verifier can read the device's signed inside/outside answer for that region.
A verifier older than 1.5.0 reports the `semantic-binding` check as FAIL for every v3
proof.

1.5.0 is available now and verifies earlier proofs too (v3, then v2, then v1), so
upgrading ahead of time is safe. The SDK's own `Octet.verify` already handles v3.
Release notes will say which OctetSDK release turns v3 on.

**Gate on the signed verdict, not on `isValid` alone.** A disc answer (`contains()` or
`isWithin(.disc(...))`) can be signed `VERIFIED` or `PLAUSIBLE`, and both verify. On your
backend, octet-verify ≥ 1.6.0 enforces a tier with `--require-verdict verified` (or
`plausible`). On the device, `Octet.verify` does the same with
`VerifyOptions(requireVerdict = RequiredVerdict.VERIFIED)`, and reports the signed tier as `ProofVerification.spoofingVerdict`.

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
`.sigstore.json` build-provenance bundle is attached to the release. Current releases
ship **without** one, so skip step 3 if no bundle is present. Steps 2–3 use the attached files offline —
the GitHub CLI and cosign are needed, but no special repository access.

---

## Reference implementation

The sample app in [`sample/`](sample/) exercises the minimum viable
permission flow if you need a reference.

---

## Updates to this document

Updates to this document arrive with each SDK release. Re-check it
when upgrading.
