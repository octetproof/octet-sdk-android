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

`OctetConfig.licenseKey` is deprecated. `Octet.start` ignores it, so leave it
out: `OctetConfig()` needs no arguments. It will be removed in the next major
version. (This is separate from Play Integrity, which remains an optional
per-proof device attestation. See "Device attestation" below.)

### Emulator, CI, and non-attesting builds — sandbox bootstrap

A build that can't produce hardware key attestation — an emulator, a CI runner,
or a device without a hardware-backed keystore — activates instead with a
**sandbox bootstrap token** set on `OctetConfig.sandboxBypassToken`, which you
self-serve from your account at [octetproof.com](https://octetproof.com). A
release (Play Store) build cannot use a sandbox token: the SDK refuses it
before the request is built, and the backend rejects a sandbox token for a
release app row.

The side an app lands on follows its signature, not its build type: an Android
release build signed with the debug key lands on sandbox.

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

## The location notification

While the SDK collects location, Android shows an ongoing notification for its
location foreground service. By default it reads "Location active" / "Confirming
your location in the background", with a location icon, and a tap does nothing.
It appears inside your app, so you can set your own:

```kotlin
val config = OctetConfig(
    advanced = AdvancedConfig(
        foregroundNotification = ForegroundNotification(
            title = "Acme",
            text = "Checking your location for this session",
            smallIcon = R.drawable.ic_stat_acme,      // monochrome, not your launcher icon
            channelId = "acme_status",                // optional: your own channel
            contentIntent = openAppPendingIntent,     // optional: what a tap opens
        ),
    ),
)
```

To build the whole notification yourself, set `provider` instead; the SDK then only
shows and removes it. Create its channel before you return it.

Android 14 and later crash an app whose foreground service posts a notification with
a missing or adaptive small icon, or on a channel that doesn't exist. The SDK checks
your settings first. If one would crash, it logs a warning (tag `OctetNotification`)
and shows its default notification instead. `close()` removes the notification.

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

The SDK collects **privacy-preserving usage telemetry** (counters and per-proof events) and reports it
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

**Additional diagnostic signals (remotely gated).** Octet turns these on through
remote configuration, without an app release, and they are on for all licenses.
To keep the base counters but not these, set `flagsEnabled = false`, which stops
the SDK from receiving remote configuration. They add:

- **Per-proof events** — one flat record per proof, every field a bucketed **enum
  label** (the proof level and internal quality labels). The one field beyond pure enums is a coarse
  **region identity** — an **ISO 3166 country / subdivision code** (e.g. `US`,
  `GB-ENG`), never finer than the level the proof already claims and bounded to
  ISO 3166 space, not free text. Still no coordinates.
- **Background proof facts.** Every background proof can also report the country
  or subdivision code the device was resolved to, whether or not your app asked
  about that region. The reports also cover how the location was found (for
  example GPS or the cell network), the proof's confidence and generation time as
  ranges, and the app's location-permission state. Still no coordinates.
- **Configuration exposure.** Which remote-configuration variant the device
  received, with a pseudonymous device key, sent to `/v1/exposure`.
- **Error diagnostics** — a count of `(error type, call site)` pairs for errors
  caught at the SDK's public API boundary, with any message mapped to a fixed enum
  (`license` / `region_decode` / … / `unknown`) — never the raw message, never
  PII; capped at 50 distinct pairs.

**Your control.** All of the above, base and gated, stops entirely when
`telemetryEnabled = false`. No counters are recorded, the persisted file is
deleted, and `/v1/metrics` and `/v1/exposure` are never called. Three requests
continue: the license heartbeat, the remote-configuration fetch (SDK version,
platform, OS version, app ID and a pseudonymous device key), and the daily usage
record described below.

---

## Data collection disclosure (for your Play Data Safety form)

Android has no in-artifact privacy manifest (that's an iOS concept), so this is the
authoritative statement of what the SDK handles — use it to complete your app's Play
Console **Data safety** form. iOS ships the equivalent as a bundled
`PrivacyInfo.xcprivacy` inside the xcframework.

| Data | Collected (leaves device) | Purpose | Linked to identity | Tracking |
|---|---|---|---|---|
| Precise location | Only if you set `proofUploadUrl` (a proof at the default city level contains the exact coordinates), or in a gateway mode (country and state lookups send full-precision coordinates to `{gateway}/ext/geo/reverse`) | App functionality (location proofs) | No¹ | No |
| Coarse location | Yes, as country and subdivision codes in the diagnostic signals, and in uploaded proofs if you set `proofUploadUrl` | App functionality (country-tier proofs), Analytics (region codes in the diagnostic signals) | No¹ | No |
| Device ID (device fingerprint) | Yes — license activation + bound into proofs | App functionality / anti-fraud | No¹ | No |
| Product interaction (usage counters) | Yes, unless `telemetryEnabled = false` | Analytics: usage counters and per-proof events, no coordinates | No | No |
| Product interaction (daily usage record) | Yes, always: one record per device per day | Billing (active-device count), **no location** | No | No |

¹ The device fingerprint is a pseudonymous per-install value, not an account identity;
the SDK itself does not link this data to a user identity. If **your** app associates
proofs with a user account, classify accordingly on your own form.

No data is used for advertising or cross-app/site tracking. With `proofUploadUrl` unset
(the default) and the default direct transport mode, the SDK sends no coordinates to
Octet. Country and state lookups then use the operating system's geocoder.

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
| `/v1/bootstrap/challenge`, `/v1/bootstrap`, `/v1/heartbeat` | `https://api.octetproof.com/v1/…` | activation. Never modify the body. |
| `/v1/flags`, `/v1/metrics`, `/v1/credits/…` | `https://api.octetproof.com/v1/…` | flag bundles are signed — never modify |
| `/v1/proofs`, `/v1/proofs/auth`, `/v1/proofs/challenge` | `https://api.octetproof.com/v1/…` | proof body up to 256 KiB — never modify |
| `/v1/exposure` | `https://api.octetproof.com/v1/…` | **both SDKs**. Usage reporting. Sent only with `telemetryEnabled` on. Body not signed. |
| `/v1/network/observe` | `https://api.octetproof.com/v1/…` | **both SDKs**. A `GET` with no body. |
| `/v1/integrity/decode` | `https://api.octetproof.com/v1/…` | **both SDKs**. Sent only when your app calls `Octet.verifyOnline` with a Play Integrity decoder. Body not signed. |
| `/ext/gnss/…` | `https://cddis.nasa.gov/archive/gnss/data/daily/…` (fallback `https://igs.ign.fr/pub/igs/data/…`) | **Android SDK only** — GNSS ephemeris (large files; may need NASA Earthdata credentials) |
| `/ext/geo/…` | `https://api.octetproof.com/ext/geo/…` | **both SDKs**. **First-party & bearer-authed** — forward it like a `/v1/…` route (preserve `Authorization`, send `Host: api.octetproof.com`), not like the third-party `ext` routes above. |

Forward the third-party `/ext/gnss/` route only if you embed the Android SDK; `/ext/geo/…` is first-party and applies to both.

**Contract:**

- **Preserve the request verbatim** — method, the path after the prefix, the query
  string, and all headers, especially `Authorization`, `Content-Type`, and any
  `X-Octet-…` header.
- **Never modify the body** of `/v1/proofs`, `/v1/flags`, `/v1/bootstrap/challenge`
  or `/v1/bootstrap`. Each signature is over the exact bytes, so any rewrite, even
  re-serialising the JSON, breaks verification. Forward every other body, including
  `/v1/heartbeat`, and the `Authorization` header unchanged as well.
- **Use TLS 1.2 or higher to the upstream**, verify the upstream certificate, and
  send `Host: api.octetproof.com` (and matching SNI) on the first-party routes.
- **Pass status codes and the `Retry-After` header through unchanged** — the SDK
  honours them on `429` and `503`.
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
  temporarily reverts only these calls to Octet directly: activation and heartbeat,
  proof upload, credits, border-data updates, and the `/v1/integrity/decode` call.
  It then re-probes your gateway and switches back once it recovers. Flags, metrics,
  `/v1/exposure`, `/v1/network/observe`, `/ext/ipgeo/` and `/ext/gnss/` never fall
  back. **This suspends the single-domain guarantee while active.** During the outage
  the calls that fall back talk to `api.octetproof.com` directly, so a
  strict-compliance deployment should leave it **off** (the default).

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
- **Forward `/ext/geo/…` too:** in a gateway mode the SDK resolves country and state
  at `{gateway}/ext/geo/reverse`. If that call fails for any reason, including an HTTP
  error such as `404` from a proxy that does not forward the route, state proofs
  return `INDETERMINATE`. Country proofs may still resolve from the cell network.
  With `fallbackToDirect` on, a failed `/ext/geo/reverse` call uses the operating
  system's geocoder instead. This route is the only exception to the rule above: the
  other routes that fall back react to transport errors only, never to an HTTP status.

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

## Confidence score and assurance tier

By default, the SDK reports **no confidence score and no assurance tier**:

- `confidence` on a verdict or proof holds neutral values (`overallScore` and
  `spoofingRiskScore` are `0`, `sensorContributions` and `flags` are empty), and
  `toStr()` prints no `conf=`.
- `proof.spoofingVerdict` is `UNSPECIFIED` (`"UNSPECIFIED"` in JSON).
- `Octet.verify` reports no tier: `ProofVerification.spoofingVerdict` is empty and a
  `requireVerdict` option is ignored (its check reports "not checked").

These fields are deprecated and will be removed in the next major release. The answer
itself (`result`, `level`, `reason`, `achievableLevel`) is unchanged. If you need more
device intelligence or your own scoring logic, Octet offers it as a custom feature or
license.

Disc queries are unaffected: with `OctetConfig.advanced.acceptPlausibleContainment = true` a disc query can return a lower-assurance
`YES`; with it off (the default) it returns `INDETERMINATE` instead. Tight tolerances
only pass with a tight location fix.

---|---|
| `VERIFIED` | Corroborated: independent evidence supports the fix. |
| `PLAUSIBLE` | Lower assurance: the fix is consistent with your query but is **not** independently corroborated. |

Opt in with `OctetConfig.advanced.acceptPlausibleContainment = true` to receive a
`PLAUSIBLE` disc; with it off (the default) a disc query returns `INDETERMINATE`
rather than a lower-assurance `YES`. Gate on the tier your use case requires; do
not treat the region granularity alone as assurance.

---

## Device attestation

When Google Play Integrity is available, the SDK requests a Play Integrity token and
binds it into the proof through its nonce, which is part of the data the device key
signs. The token is opaque on the device, so the proof records its status as
`INTEGRITY_PENDING`, or `INTEGRITY_UNAVAILABLE` when no token could be obtained. With
`osAttestationInGatewayModes = false` in a gateway mode, the SDK requests no token and
the proof carries no Play Integrity status at all. A relying party's verifier decodes the token and decides what it means. A
missing or non-compliant Play Integrity result does not stop the SDK from producing
the proof. No integration code is required. It is part of proof generation. How often a fresh attestation is produced is configurable via
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

To check the binding on the device instead, pass the same nonce to `Octet.verify` with
`VerifyOptions(expectedSessionNonce = loginNonce)`. The `session-binding` check then fails a proof made
for a different nonce, or for none. Your backend still has to make sure each nonce is
used only once.

---

## Content commitments (new in 3.1.0)

Pass `content` to `isWithin`, `isOutside` or `contains`, and the device signs a commitment to
that content into the proof: each item's key, content type, size and `SHA256(salt ‖ value)`.
The content and the salts stay on the device unless you upload them (see below). A verifier who receives the content can confirm
that this attested device asserted exactly that content at the proof's time and place. The
commitment does not prove that the content is authentic or unedited.

```kotlin
val video = Octet.hashContent(videoFile)                   // any size, before the call
val verdict = sdk.loc.isWithin(OctetRegion.country("GB"), content = listOf(
    ContentItem.bytes("note", "text/plain", "valve 7 OK".toByteArray()),
    ContentItem.digest("video", "video/mp4", video),
))
```

- `ContentItem.file(...)` items are hashed in the call up to 64 MiB. Hash larger files with
  `Octet.hashContent` first and pass a `digest` item.
- The SDK salts each item with a random 32-byte salt unless you pass your own. Read it back from
  the item's `salt` (or `ContentDigest.salt`) and keep it with the content. The verifier needs it.
- The SDK hashes exactly the bytes you pass. Strip metadata such as EXIF before you create the
  item, if you want it stripped.
- Content that breaks a rule returns an `INVALID_CALLER_CONTENT` verdict with no proof. Without
  location authorization, a query with content returns `PERMISSION_REQUIRED`.
- To check a commitment on the device, pass the manifest and content in
  `VerifyOptions.callerMetadata`, and accept the proof only when `verdict == ProofVerification.Verdict.VALID`.
- The manifest is the JSON `{"v":1,"entries":[{"key","contentType","size","salt","sha256"}]}`,
  one entry per item in the order of the proof's `contentCommitments`, with `salt` and `sha256`
  in lower-case hex. Build it from `contentCommitments` and the salts you kept. To check content
  on your backend, use `octet-verify` 1.8.0 or later.
- Caller content is off until Octet enables it for your licence. Until then, a query with
  `content` returns `INDETERMINATE` with reason `CALLER_CONTENT_NOT_ENABLED` and no proof.
  Queries without content are unaffected.

### Uploading content to your storage

The SDK can upload the exact bytes it committed to storage you control, for example through a
pre-signed S3, GCS or Azure URL that your backend creates. Octet stores none of the bytes. The
upload starts after the proof is made and never changes it. The proof stays valid whether or
not the upload arrives.

```kotlin
// In Application.onCreate:
Octet.setContentUploadProvider(object : ContentUploadProvider {
    override suspend fun uploadTarget(request: ContentUploadRequest): ContentUploadTarget {
        // Ask your backend for a fresh pre-signed URL for this item.
        val url = myBackend.presignedPut(request.proofId, request.key)
        return ContentUploadTarget(url)
    }
})
Octet.setContentUploadListener { status ->
    Log.d("Uploads", "${status.proofId} ${status.key} ${status.state}")
}

val verdict = sdk.loc.isWithin(OctetRegion.country("GB"), content = listOf(
    ContentItem.file("photo", "image/jpeg", photoFile, upload = true, locator = "site-7/photo.jpg"),
))
```

- Set `upload = true` on a `bytes` or `file` item, or pass `uploadFrom` (the file you hashed)
  on a `digest` item. Items without it are committed only.
- `locator` is an optional label you choose for where the content is kept. The proof lists it
  but doesn't sign it, and your provider receives it. Keep it to 256 bytes. Never put a
  credential, or a URL with a query string, in it.
- The SDK asks your provider for a target before every attempt, so return a freshly signed URL
  each time. `ContentUploadRequest` carries the proof id, key, content type, size, committed
  digest, locator and salt. Store the salt with the file, for example as object metadata. A
  verifier needs it.
- For large files, return `ContentUploadTarget.gcsResumable(sessionUrl)`,
  `ContentUploadTarget.azureBlock(sasUrl)` or `ContentUploadTarget.s3Multipart(uploadId)`. The
  SDK sends the file in chunks and continues after an interruption. For S3 multipart, also
  implement `s3PartTarget` and `s3Complete` in your provider.
- Before each attempt the SDK hashes the content again. If the file changed or is gone, it
  uploads nothing and the item fails with `ContentChanged` or `ContentMissing`. Keep the file
  unchanged until its upload finishes.
- The listener reports each item's state, `Pending`, `Uploading`, `Uploaded` or
  `Failed(failure)`, off the main thread. `Octet.contentUploads(proofId)` returns the same
  states for one proof, and is empty before `Octet.start`. Finished uploads stay listed for
  7 days.
- Network errors, `408`, `429` and `5xx` responses, and errors your provider throws are
  retried with backoff, up to 8 attempts within 24 hours. Any other `4xx` fails the item with
  `Rejected(httpStatus)`. When retries run out, the item fails with `ProviderFailed` if your
  provider kept failing, and with `Exhausted` otherwise. An item not uploaded within 7 days
  also fails with `Exhausted`. Delivery isn't guaranteed, so check the state before you rely on
  the file being in your storage.
- Uploads run in a job the SDK schedules with Android whenever a network is available. They
  continue while your app is closed and after its process is killed. Register the provider in
  `Application.onCreate`, so it is in place when Android runs the job with your app in the
  background. Without a provider, queued uploads wait, for up to 7 days.
- Uploads go straight to the URL your provider returns. A gateway set in `TransportPolicy` and
  its certificate pins don't apply to them, so allow your storage host if you restrict egress.

---

## Keeping your verifier current

If your backend verifies proofs itself with `octet-verify`, upgrade it to
**octet-verify ≥ 1.5.0** before you adopt OctetSDK 3.1.0, which switches
proofs to **semantic-binding v3**. v3 also signs the region a proof was asked about,
so your verifier can read the device's signed inside/outside answer for that region.
A verifier older than 1.5.0 reports the `semantic-binding` check as FAIL for every v3
proof.

1.5.0 is available now and verifies earlier proofs too (v3, then v2, then v1), so
upgrading ahead of time is safe. The SDK's own `Octet.verify` already handles v3.

**The signed tier on your backend.** The tier is still signed inside the proof bytes, and octet-verify
≥ 1.6.0 can enforce it with `--require-verdict verified` (or `plausible`). On the device,
`Octet.verify` reports and enforces it only where the tier is enabled for your licence (see
"Confidence score and assurance tier").

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

Near a border between two subdivisions, a device whose accuracy area crosses that border
can only be proven at the country level. A subdivision query returns `INDETERMINATE` with
`INSUFFICIENT_PRECISION` and `achievableLevel` `COUNTRY`, and a proof of the device's own area
claims the country only. A policy that accepts only one of the two subdivisions will see
such a device as unknown, not as inside or outside. Plan for that answer near
subdivision borders, for example by asking again once the device has a better fix.

---

## Log output

A released SDK writes only lines you can act on to logcat. They go through
`AndroidLogSink`, which forwards into `android.util.Log`:

| Tag | Level | When |
|---|---|---|
| `OctetLicense` | INFO | Every `Octet.start`: which license mode it took: `sandbox` when `sandboxBypassToken` is set, `attested` otherwise. An attested app lands on release when release signed, on sandbox when debug signed. |
| `OctetLicense` | WARN | The SDK version is deprecated, or the license stops working within 30 days. |
| `OctetLicense` | ERROR | `Octet.start` threw a `LicenseError`. Names the case and what to fix. |
| `OctetPlayIntegrity` | WARN | A Play Integrity setup problem you can fix: an invalid cloud project number, a used-up request quota, or the API not enabled. Once per process. |
| `SandboxBootstrap` | WARN | A debuggable build carries a `sandboxBypassToken`, which a release build will refuse. |

Set `OctetConfig.advanced.logLevel` to `LogLevel.WARN` or `LogLevel.ERROR` to
quiet the lower lines. None of these lines carry coordinates, device
identifiers or your sandbox token.

The SDK's internal diagnostic stream is gated behind `BuildConfig.DEBUG`, so
only a debug build of the SDK writes it to logcat. A released SDK never does.

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
