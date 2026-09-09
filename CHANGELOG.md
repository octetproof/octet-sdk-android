# Changelog

All notable changes to the OctetSDK for Android are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [2.0.0] — 2026-09-09

> Major release, in lockstep with iOS 2.0.0. Two things make it major. First,
> **how the SDK activates has changed**: at `Octet.start` it now proves the device
> with a **hardware key-attestation certificate chain** (StrongBox/TEE, rooting to
> Google's hardware attestation root), and obtains its licence by attested bootstrap,
> replacing the pasted-license-key activation step. Second, the proof surface grows — a public
> on-device **verifier** (`Octet.verify`), a public teardown (`OctetSdk.close()`), a
> cache-bypass (`forceFresh`), an experimental permissionless location estimate —
> and **semantic-binding v2 becomes the default proof form**. **Read _Changed
> (breaking)_ before upgrading:** the activation change is integration-level, and
> two verdicts now refuse where they previously returned a misleading positive.
>
> _Released 2026-09-09._

### Changed (breaking)

- **Attested activation replaces the license-key flow.** `Octet.start` no longer
  activates by sending a pasted license key to an activation endpoint. It now proves
  the device with a **hardware key-attestation certificate chain** (StrongBox/TEE,
  rooting to Google's hardware attestation root) and bootstraps its licence from the
  backend. A host that cannot attest — an emulator, CI, or a device without a
  hardware-backed keystore — sets `OctetConfig.sandboxBypassToken` and bootstraps
  through the sandbox path instead; a device with neither attestation nor a sandbox
  token fails closed. `OctetConfig.licenseKey` remains a field for now but is no
  longer the credential that activates the SDK. **This is an integration-level
  change:** an app that relied on a license key alone must now activate on a real
  device with hardware-backed key attestation (or carry a sandbox token for
  non-device builds).

### Added

- **`Octet.verify(...)` — public on-device proof verification.** An offline
  verifier that checks a proof's signature, freshness, semantic binding, and
  hardware-attestation anchoring, and returns a grouped, human-readable result
  naming every check that ran and every check that could not — and why. It makes no
  network call and needs no backend, so a relying party can reach a local trust
  decision, or triage a proof before forwarding it. The authoritative end-to-end
  verifier remains the standalone `octet-verify` CLI; `Octet.verify` runs the same
  checks in-process.

- **Hardware-attestation anchoring in the verifier.** `Octet.verify` can anchor a
  proof's device key to the platform hardware root — the Google hardware attestation
  root — so a verified proof carries evidence it came from a genuine app instance on
  genuine hardware, not merely that its signature checks out.

- **`OctetSdk.close()` — public teardown.** Stops all SDK activity — location
  monitoring, the background heartbeat, any in-flight upload — and releases the
  handle, so a host that needs the SDK for only part of its lifecycle can shut it
  down cleanly rather than leave it running. Idempotent.

- **`forceFresh` on `isWithin` / `isOutside` / `contains`.** An optional flag that
  bypasses the short-lived proof-reuse cache and forces a freshly measured proof
  for this call — for a caller that needs a just-now measurement rather than a
  buffered one. Omitted (the default), behaviour is unchanged: the cache is
  consulted.

- **Permissionless location estimate (experimental, opt-in).** With
  `advanced.enableLocationEstimator`, a predicate made while the app holds no
  location permission returns a signed, coarse `OctetVerdict.estimate` instead of
  failing outright. `OctetVerdict.proof` is always `null` on that path, and a verdict
  never carries both — so an estimate can never be read as a proof. **This is not a
  proof pathway:** the radii are conservative bounds, not measured error, and
  consent for deriving location remains yours.

- **`regionFromJson`.** The inverse of `OctetRegion.toJson()` / `toJsonl()`:
  decodes the same stable tagged shape the forward direction emits, round-tripping
  every factory. It exists so a non-native caller — React Native, a server payload,
  a stored region — can hand a region across a language boundary without
  re-modelling seven shapes on the far side.

  Input is treated as untrusted, so unlike the region factories it always throws
  `OctetRegion.DecodeError` on malformed, missing, wrong-typed or out-of-range
  input. `disc` has no tag of its own (it round-trips through `ellipse`), and H3
  cells are hex strings rather than JSON numbers, because an H3 index exceeds the
  exact-integer range of a JSON number.

- **`decisionRef` on `isWithin` / `isOutside` / `contains`.** An optional opaque
  decision identifier (host-minted, ≤256 chars) that binds a generated proof to one
  specific authorization decision, letting a relying party fetch and trust a proof
  for exactly that decision. Additive and backwards-compatible: the default `null`
  is unchanged behaviour and the upload envelope stays `schema_version: 2`.

- **Remote configuration — `sdk.flags`.** A read-only surface: `getBoolean` /
  `getInt` / `getString(key, default)` and `getAssignment(experiment)`, backed by a
  signed bundle fetched at `start()` and refreshed on each heartbeat, cached
  last-good. Default-on via `OctetConfig.flagsEnabled`. Entirely fail-safe — a
  disabled subsystem, no bundle, or an unknown or wrong-typed key all return the
  default you supplied.

- **New refusal reason `REGION_UNRESOLVED`.** Returned when a region genuinely
  cannot be resolved at the required confidence — an honest "couldn't measure",
  deliberately distinct from a detected spoof.

### Changed

- **semantic-binding v2 is now the default proof form.** A proof now binds its
  semantic fields — level, region type, integrity status, and the position-
  commitment geometry — under the v2 scheme by default, hardening it against
  post-hoc field edits. A current `octet-verify` accepts both the v1 and v2 forms;
  verifying a v2 proof needs an up-to-date verifier.

- **Telemetry hardened.** The optional anonymous usage counters — on by default, no
  location data, unchanged in shape since 1.1.0 — are now encrypted at rest on the
  device, their upload sample rate can be tuned by remote configuration, and a
  fresh install no longer uploads a report before its first activity. Disable all
  of it with `OctetConfig.telemetryEnabled = false`.

- **A country or subdivision proof now claims where the device *is*, not what was
  *queried*.** `isWithin(country("XX"))` previously stamped the queried region into
  the proof's claim, which made a decision-bound country predicate able to answer
  only `YES`. The claim is now derived from the estimate, with granularity clamped
  to the query — never finer, so no street-level geometry leaks out of a country
  predicate — and decision binding moves entirely onto `challenge_decision_ref` and
  `region_ref`.

  **Expect more refusals at first.** A country-level decision whose honest claim
  differs from the queried country now fails closed. That is the intended
  behaviour, not a regression: the previous answer was tautological.

### Security

Brief — each hardens a case that previously produced a misleading positive:

- **Mock-location detection no longer goes quiet on a stationary device** — a
  detected mock provider is now reported for as long as the last fix seen was
  mock-flagged, instead of clearing itself about ten seconds after the last position
  update while the device stood still. Stricter than 1.2.1.
- **Refusal reasons distinguish adversarial from benign** — a detected spoof carries
  an adversarial reason (`MOCK_LOCATION_DETECTED`, `SPOOFING_DETECTED`, `TAMPERING`,
  `ATTESTATION_FAILED`), machine-distinguishable from the benign couldn't-measure
  reasons (`REGION_UNRESOLVED`, `INSUFFICIENT_PRECISION`, …), so a policy layer can
  treat them differently.

### Fixed

- **A minified release build no longer crashes at startup.** The SDK depends on
  `androidx.work` (and through it Room) and on `protobuf-javalite`, neither of
  which supplies adequate consumer rules of its own: Room ships a class-only keep
  rule that under R8 full mode — the default since AGP 8 — does not retain the
  constructor Room instantiates reflectively, and `protobuf-javalite` is a plain
  JAR, which cannot carry consumer rules at all, so R8 renamed generated message
  fields that are looked up by name.

  Either fault killed the app at process start, before any Octet code ran, and **in
  both cases the build succeeded** — only launching revealed it. Both are now
  compensated in the rules shipped inside the AAR, so no `proguard-rules.pro` entry
  is needed on your side. No upstream version fixes the Room case: room-runtime
  2.6.1 ships the same class-only rule and work-runtime 2.10.1 still depends on it.

- **`toJson()` / `toJsonl()` now emit the real proof.** Both platforms emitted a
  placeholder in place of `proof` and `confidence`, so a JSON consumer received a
  `YES` verdict carrying no proof bytes at all. `proof` now carries `id`, `level`,
  `timestamp_ms`, `sdk_version`, `platform`, `confidence`,
  `position_commitment_b64` and `proof_bytes_b64`, and `achievable_level` is now
  emitted at the top level. `claimedRegion` stays absent — it already travels
  inside the proof bytes. No typed API change.

  Android's flag spellings are unchanged; iOS moved to match them, so the two
  platforms now agree.

- **A decision-bound call no longer returns a proof minted without that decision.**
  `isWithin` / `isOutside` / `contains` with a `decisionRef` could return a
  buffered, non-decision-bound proof, so no decision-scoped proof was ever
  uploaded and a relying party's fetch-by-decision found nothing and denied. The
  freshness buffer is now consulted only when neither a session nonce nor a
  decision reference is set.

- **`Octet.start` no longer triggers an ANR during device attestation.** The
  attestation step now runs off the main thread, so a slow attestation can no longer
  cause an Application Not Responding stall at launch.

- **The first proof upload after a fresh activation is reliable.** A transient
  device-identity mismatch that could make the very first upload retry once before
  succeeding is resolved, so the first proof uploads cleanly.

- **The activation bearer is refreshed against its real expiry.** The SDK assumed a
  ~24 h window against a backend token that actually lives ~1 h, so authenticated
  calls could be rejected with `401` after the first hour of a session. The SDK now
  honours the token's real expiry, treats it as expired 30 s early, and refreshes
  at a cadence derived from that lifetime. Sessions established before this change
  fall back to the previous behaviour, so upgrades are seamless.

## [1.2.1] — 2026-08-03

> **Security hotfix on top of 1.2.0.** No new features and **no change to the proof
> wire format, proof semantics, trust levels, or verdict codes** — a 1.2.1 proof
> means exactly what a 1.2.0 proof means, and every verifier is unaffected. This
> release hardens the shipped release binaries and removes `OctetConfig.debugMode`.
> **All consumers should upgrade**; 1.0.0 / 1.1.0 / 1.2.0 are deprecated.
>
> _Release date stamped at tag time._

### Security

- **Release binaries hardened.** The Android native library is rebuilt with
  developer build-path remapping, `panic = abort`, and symbol stripping, so shipped
  binaries no longer embed developer build-machine paths or internal source
  identifiers.
- **Release CI guards against binary identifier leakage.** A release-pipeline check
  fails the build if any shipped binary contains internal identifiers, so a
  regression cannot silently reintroduce them.

### Fixed

- **Android internal logs no longer include raw coordinates or a device identifier.**
  Two internal diagnostic log lines embedded a raw latitude/longitude and a device
  identifier; both are now redacted.

### Removed (breaking)

- **`OctetConfig.debugMode` (added in 1.2.0).** This opt-in field let an integrator
  mirror the SDK's internal diagnostic logs to the host app's logcat in a **release**
  build; removing it restores the safe default in which a released SDK writes nothing
  to consumer logcat. **Breaking** for anyone who set it — the field existed for only
  one release.

### Deprecated

- **All releases before 1.2.1 are deprecated in favour of 1.2.1** — the four
  `0.0.x-alpha` previews and `1.0.0`, `1.1.0`, `1.2.0`. Their downloadable
  artifacts have been **removed from the GitHub release pages and the Maven
  repository** for security; a build pinned to an old version must move to
  **≥ 1.2.1**.

## [1.2.0] — 2026-07-29

> Feature release on top of 1.1.0. Backwards-compatible, drop-in upgrade: the
> public API additions are additive and the proof wire format is a strict superset
> of 1.1.0 — a proof made without a session nonce is byte-identical, and the new
> optional session-binding stage is ignored (NOT-CHECKED) by a 1.1.0 verifier.
> **Enforcing** session-binding needs octet-verify ≥ 1.2.0. Two other additions
> ship **inert** (SDK-version upgrade gating; the `creditServiceUrl` hook) —
> present but with no runtime effect until their backends turn them on — so
> upgrading changes nothing for existing integrations.

### Added

- **Verifier hardware-root bootstrap — `Octet.attestationEnrolmentBundle()`.**
  Returns this device key's `AttestationEnrolmentBundle` (`jsonString()` /
  `protoData()`). Provided for API symmetry with iOS: on Android, proofs already
  self-carry their full hardware-root evidence (the Key Attestation certificate
  chain) on every proof, so a verifier does not need a separate enrolment step —
  the bundle is a convenience mirror, not a requirement. Cheap and local (a
  Keystore read); returns `null` until the device key has been attested.
- **SDK version reporting + upgrade gating.** The SDK now reports its version and
  platform on every backend request. Two new surfaces:
  `LicenseError.UpgradeRequired(minVersion, message)`, thrown from `Octet.start`
  when the backend rejects an out-of-support version; and non-fatal soft-warning
  hints on `LicenseStatus` — `upgradeRecommended: Boolean` and
  `minSupportedVersion: String?`. **Inert in 1.2.0** — the backend gates no version
  yet, so you will not see these until version policy is enabled.
- **`OctetConfig.creditServiceUrl` (reserved).** Opt-in endpoint for a forthcoming
  credit-consumption subsystem. Default `null` disables it; **metering is not
  active in this release.**
- **Data collection disclosure.** INTEGRATION.md gains a "Data collection
  disclosure" section (what the SDK handles, by purpose) to help you complete your
  Play Console Data Safety form. No behaviour change — documentation only.
- **`OctetConfig.debugMode`.** New opt-in config field (default `false`). When
  `true`, the SDK mirrors its internal log to `android.util.Log` (`OctetInternal`)
  even in a **release** build, so you can surface the SDK's own diagnostic logs
  for a support deep-dive without a debug build. Off by default — a shipped SDK
  writes nothing to your logcat.
- **Session-binding for logins.** `isWithin` / `isOutside` / `contains` gain an
  optional `sessionNonce: ByteArray?`. Pass the one-time nonce your login backend
  issued and it's committed inside the signed proof, so your verifier can confirm
  the proof was made *for that specific login*; forward the returned `verdict.proof`
  to your backend. Only a hash of the nonce is serialized (never the raw bytes);
  omitting it preserves 1.1.0 behaviour exactly. Enforcement needs octet-verify ≥
  1.2.0. Fixes Android LFA logins, which previously failed server-side without a
  bound nonce.

### Changed

- **Public API surface narrowed.** Several internal `com.octetproof.sdk.model` types
  that were public-by-default on Android (but internal on iOS) are now `internal`,
  aligning the two platforms' public surface. `Position` and `GeoBounds` remain
  public. These types were never documented or supported; if you rebuild against
  1.2.0 and referenced one, you'll see "no such type" — switch to the public `Octet`
  API.
- **Stronger GNSS anti-spoofing.** The raw-GNSS witness that cross-checks the fused
  location provider is now fully functional and degrades honestly on weak signal,
  improving spoof resistance for on-Earth proofs. No change to the proof wire
  format or public API.
- **Rolling license-token persistence.** The SDK persists the refreshed license
  token returned on lease responses, so a device holds a fresh token across
  restarts within the offline-grace window.
- **Permission footprint trimmed.** The AAR no longer merges
  `ACCESS_BACKGROUND_LOCATION` or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — neither
  is used by the on-demand foreground proof flow, and both trip Google Play review.
  If you added a `tools:node="remove"` workaround, you can drop it. See the
  permission table in INTEGRATION.md.

### Fixed

- **Hardware-attestation level now reported on the wire.** A serialization bug
  dropped `device_attestation.security_level` from the proof, so a healthy
  TEE / StrongBox device could be rejected as "not hardware-backed" by a
  Strong-gating verifier. Fixed — proofs now carry the true attestation tier.
- **Attestation key re-attested before its chain expires.** The device's
  key-attestation chain has a short-lived (~14-day) intermediate; the SDK now
  regenerates the key before the chain ages out, instead of presenting an expired
  chain that fails verification about two weeks after enrolment.

### Packaging

- **16 KB page-size support.** The bundled native libraries are aligned for the
  16 KB memory-page devices that Android 15+ introduces, so the SDK loads on that
  hardware. No action needed by integrators.

### Build & distribution

- Releases now publish **SHA-256 checksums** and **SLSA build provenance**, an
  **SBOM**, and a **keyless cosign signature** for the AAR. See the "Verifying the
  download" section in `INTEGRATION.md`.

## [1.1.0] — 2026-06-25

> Feature release on top of 1.0.0. Backwards-compatible, drop-in upgrade: the
> public API additions are additive and the proof wire format stays compatible
> (existing proofs remain valid).

### Added

- **Device attestation.** Proofs now carry a Google Play Integrity verdict bound
  into the signed proof chain, so a verifier can confirm a proof came from a
  genuine app instance on a genuine device. Cadence is configurable via
  `OctetConfig.advanced.attestationCadence` (per-session / periodic / per-proof).
  Integrators may bind their own Google Cloud project via
  `AdvancedConfig.playIntegrityCloudProjectNumber` (defaults to the Play
  Console-linked project).
- **Anti-replay protection for uploaded proofs.** Each uploaded proof carries a
  server-issued, single-use upload nonce and a replay-control binding, so the
  backend can reject duplicated or replayed uploads. Proofs generated offline
  still upload and remain valid.
- **Semantic field binding.** A proof's level, region type, and integrity status
  are cryptographically bound into the proof chain and can't be altered after the
  fact without invalidating the proof.
- **Optional usage telemetry.** Aggregated, privacy-preserving usage counters
  (no location data) reported to the license backend. On by default; disable with
  `OctetConfig.telemetryEnabled = false`. Counters are buffered encrypted on
  device and uploaded at most once a day.
- **`OctetVerdict.achievableLevel` + clearer reason codes.** When the SDK can't
  produce a proof at the requested precision, the verdict reports the level it
  *can* reach, plus reason codes that separate a benign precision shortfall
  (`INSUFFICIENT_PRECISION`) from a security refusal (`SPOOFING_DETECTED` /
  `TAMPERING`).

### Changed

- When a location can't be proven at the requested precision, the SDK now returns
  an `INDETERMINATE` verdict carrying the achievable level instead of silently
  emitting a coarser proof — your app decides any fallback.

### Fixed

- Proof uploads no longer stall after an activation lease expires during an
  offline grace period; the SDK re-activates and resumes.
- Warm-start reliability: a stale activation token is refreshed before the first
  proof upload after launch.

## [1.0.0] — 2026-06-11

> **First stable release.** The public API, proof wire format, and license-
> claim schema are committed to under semantic versioning from this release
> forward — backwards-compatible changes ship as 1.x.x. Drop-in upgrade
> from 0.0.4-alpha for consumers using the documented public API.

### Stable surfaces

- **Public API** — `Octet.start(...)`, the `sdk.loc.isWithin(...)` predicate
  surface, `OctetVerdict`, `LicenseStatus`, `OctetRegion` shapes, the
  `LocationProof` envelope, and supporting value types under the
  `com.octetproof.sdk.api` package.
- **Wire format** — `LocationProof` envelope (slim public proto with
  opaque `proof_bytes` plus curated public fields), license PASETO v4.public
  claim schema, activation lease shape.
- **Distribution channels** — `com.octetproof:sdk` Maven artifact, with the
  transitive `com.octetproof:libpf` artifact for the native particle filter.

### Changed — public API surface narrowed

The 1.0 build narrows the visible surface to the documented public API.
Consumers using the public `Octet` API are unaffected. Consumers who had
imported other symbols will see "no such type" on rebuild — those symbols
are not part of the supported surface.

Surfaces that are public from 1.0:
- `DeviceKeySecurityLevel` (`HARDWARE_STRONGBOX` / `HARDWARE_TEE` /
  `SOFTWARE`) — surfaced via the attestation chain so relying parties can
  read the device-key tier per proof.
- `LogSink` + `LogLevel` + the platform default sink (`AndroidLogSink`) —
  implement `LogSink` to route SDK logs into your own pipeline.

### Carry-over from 0.0.4-alpha

If you're upgrading from 0.0.3-alpha or earlier, the 0.0.4-alpha entry
below details the security-hardening pass: opt-in TLS public-key pinning,
hardware-backed key storage (StrongBox-preferred), fail-closed proof
verifier, logcat-emission gated behind debug, hardened URL validation,
reduced default permission set, and a magnetometer-based liveness signal
added to on-Earth proof confidence. All carry forward unchanged.

## [0.0.4-alpha] — 2026-06-11

> **Security-hardening pass.** Every change is opt-in or fail-safer-
> by-default; the public API surface is unchanged. Drop-in upgrade
> from 0.0.3-alpha.

### Added

- **Opt-in TLS public-key pinning** for connections to
  `api.octetproof.com`, available via a bundled
  `network_security_config` resource. Off by default in this
  release; integrators enable it by referencing the SDK's NSC from
  their own `AndroidManifest.xml`. Pin set covers the current
  certificate-authority intermediate and a backup pin; pin expiry is
  tracked.
- **Magnetometer-based liveness signal** is now incorporated into
  on-Earth proof confidence (alongside existing motion / GPS
  signals).
- `DeviceKeySecurityLevel` value exposed on the hardware-attestation
  surface, reporting the actual storage tier the SDK obtained for
  the device key on this run (`HARDWARE_STRONGBOX` /
  `HARDWARE_TEE` / `SOFTWARE`).

### Changed — defaults

- **Logcat mirroring of internal SDK logs is now gated** behind
  `BuildConfig.DEBUG || OctetConfig.debugMode`. Release builds no
  longer emit the SDK's internal log lines to logcat by default —
  integrator-facing errors continue to surface through the normal
  return / exception channels and through the SDK's structured log
  sink, if one is configured.
- **StrongBox is requested** for the device key, with a graceful
  fall-through to TEE-backed and then software-backed keys on
  devices where StrongBox is unavailable. The chosen tier is now
  recorded and reported via `DeviceKeySecurityLevel` rather than
  inferred at attestation time.
- **On-device proof verifier fails closed.** The on-device verifier
  now returns an explicit `VerificationStatus` of
  `VERIFIED` / `SHAPE_VALID_UNVERIFIED` / `INVALID`, with
  `isValid` set only when the signature has been cryptographically
  verified against a trusted key. The authoritative end-to-end
  verifier remains the standalone `octet-verify` CLI.
- **Proof-upload URL validation** tightened. The LAN-HTTP exception
  (RFC 1918 + loopback, when proof-upload is opt-in pointed at a
  development backend) now uses strict numeric-literal parsing
  rather than DNS-resolving string-prefix matches.

### Removed

- **Vestigial storage / media permissions** are no longer declared
  in the SDK's `AndroidManifest.xml` and will no longer be
  manifest-merged into consumer apps. (`READ_MEDIA_*`,
  `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`.) The SDK's
  file I/O is to the app's internal `filesDir` / `cacheDir` and
  needs no permission.

### Build & packaging

- **libpf native libraries no longer ship debug symbols** in the
  release AAR.
- **The SDK and libpf modules now go through R8 minify** in their
  release builds; `consumer-rules.pro` propagates the JNI keep-rule
  so downstream consumers don't need to re-declare it in their own
  ProGuard configuration.

### Sample app

- Sample renamed from the `com.octetproof.toy.v1` package /
  application id to `com.octetproof.sample`. Source tree, namespace
  and `applicationId` updated.

## [0.0.3-alpha] — 2026-06-09

> **Proof upload + heartbeat lease refresh, plus Android attestation-
> chain alignment with iOS.** Opt-in proof upload to an `octet-proofs`
> backend, hardware-backed activation-bearer cache, and a periodic
> heartbeat scheduler now ship. **Wire-affecting:** per-stage
> attestation signatures issued by 0.0.2-alpha-and-earlier Android
> builds will not verify against 0.0.3-alpha. Re-generate proofs on the
> new SDK to maintain validity.

### Added — proof upload

- `OctetConfig.proofUploadUrl: String?` — opt-in proof-upload endpoint.
  Default `null`: upload subsystem disabled entirely (no scheduler, no
  network calls). When set, the SDK uploads each generated
  `LocationProof` to your configured `octet-proofs` backend. HTTPS-only,
  with a LAN-HTTP exception (RFC 1918 + loopback) for local development.
- Authentication, retry-with-backoff, and queuing across app restarts
  are handled by the SDK — nothing additional to wire up.
- `proof.id` is now formatted as a lowercase UUID string to match
  iOS, easing cross-platform tooling.

### Added — heartbeat scheduler + activation-bearer cache

- The activation bearer issued at `/v1/activate` is now persisted in
  `EncryptedSharedPreferences` (StrongBox-backed master key where
  available) so the SDK can refresh license leases and authenticate
  proof uploads across app restarts without re-activating.
- A background scheduler performs periodic lease-refresh pings at the
  cadence the activate response specifies. The device fingerprint stays
  consistent across restarts.

### Changed — Android attestation chain

- The Android per-stage attestation signatures were aligned with iOS
  for cross-platform parity. Per-stage signatures issued by
  0.0.2-alpha-and-earlier Android builds will not verify against
  0.0.3-alpha — re-generate proofs to maintain validity.

### Fixed

- Resolved a false-positive in the SDK's anti-spoof pipeline that
  could block proof generation on release-signed Android builds.
- Restored a set of public-API constructors that had been
  over-stripped by R8 in 0.0.2-alpha (could surface as a runtime
  `NoSuchMethodError` on first use).

### Independent verifier

- The independent proof verifier is now its own repository:
  [`octetproof/octet-verify`](https://github.com/octetproof/octet-verify).
  It verifies a proof from a file or by fetching from a backend, and
  prints what was and was not validated. Designed to be auditable end-
  to-end by anyone integrating against the SDK.

### Documented

- New section in `INTEGRATION.md` on proof-upload data handling — what
  the SDK transmits when upload is enabled, how long uploaded proofs
  are retained on the Octet-hosted backend, the option to fetch and
  persist proofs yourself, and the self-hosted backend configuration.

### Sample app

- Sample updated to demonstrate proof upload against the configured
  activation backend.
- **Release-signed sample APK** is now attached to each GitHub Release.
  The CI-built APK ships without a license key baked in — build from
  source with your own key for a runnable demo.
- **R8 `mapping.txt`** for the SDK is attached as a release asset
  on every tagged release, for use when de-obfuscating consumer
  crash reports against this version.

## [0.0.2-alpha] — 2026-06-04

> **v1 license-key cutover.** Wire-breaking: v0-alpha tokens issued
> before this release will not verify against the v1 verifier.
> Existing customers receive re-issued v1 tokens.

### Changed — license model (wire-breaking)

- New v1 PASETO v4.public claim schema (`iss, iat, nbf, exp, lid, sub,
  jti, typ, v, prod, pver, plat, tier, model, limits, feat, ehash,
  meta`). Tolerant-reader on unknown claims; fail-closed defaults;
  60s skew tolerance on `nbf` / `exp`.
- New `LicenseError.VerificationFailed(reason)` carrying a
  `VerificationReason` value that names the specific failure mode
  (vendor prefix, signature, validity window, schema mismatch,
  product/platform mismatch, clock rollback). The other
  `LicenseError` subclasses (`MalformedKey`, `Expired`,
  `ActivationWindowClosed`, `Revoked`, `Network`, `NoActivation`,
  `ServerRejected`) are unchanged.
- Activation flow: `/v1/activate` now returns a plain JSON lease (TLS
  is the integrity layer); no more signed PASETO activation tokens.
  `ActivationClient` exposes `activate` / `heartbeat` / `deactivate`.
  14-day offline grace after a successful activation.
- Stable device-fingerprint formula:
  `b64url(sha256(install_uuid || platform_hint))` where
  `platform_hint` is `Settings.Secure.ANDROID_ID`.
- Anti-rollback clock: `AnchoredClock` persists server-
  timestamp anchors in `EncryptedSharedPreferences` (StrongBox-backed
  master key), raises `ClockRollback` when the wall clock regresses
  past tolerance.

### Removed

- The v0-alpha activation-token PASETO shape and `octet.activation`
  typ. v1 activation returns plain JSON.
- `Octet.start`'s old `sdkVersion` + `appId` activate-time claims —
  token + device fingerprint are the v1 auth surface.

### Sample app

- `local.properties.example` gains an `octet.activationServerUrl` line
  (defaults to `https://api.octetproof.com`; override to a LAN
  address when running against a self-hosted activation backend).
- `sample/app/build.gradle.kts` reads `octet.activationServerUrl` from
  `local.properties` and exposes it as `BuildConfig.OCTET_ACTIVATION_SERVER_URL`.

### Deprecated

- **[0.0.1-alpha](https://github.com/octetproof/octet-sdk-android/releases/tag/0.0.1-alpha)
  is deprecated.** Tokens from the current production backend will
  fail to verify on 0.0.1-alpha with
  `LicenseError.VerificationFailed(UnsupportedSchema)` at
  `Octet.start`. Upgrade to `0.0.2-alpha` or later.

## [0.0.1-alpha] — 2026-05-28

First public release. Pre-stable: API, naming, and on-disk surface may
still change without notice across `0.0.x`.

### License model

License keys are valid for 90 days from issuance, followed by a 15-day
grace window (the SDK keeps working and surfaces a renewal nudge via
`LicenseStatus.state == GRACE_PERIOD`), then a hard stop at 105 days.
There is no per-device activation cap — a license is bound to its
holder, not to a specific device install.

### SDK distribution

- `com.octetproof:sdk:0.0.1-alpha` published to the orphan `mvn-repo`
  branch. Consumers add the `raw.githubusercontent.com/.../mvn-repo`
  Maven URL to their `settings.gradle.kts` and resolve via standard
  Gradle dependency resolution.
- Native particle-filter + GDAL libraries vendored as a sibling
  `com.octetproof:libpf:0.0.1-alpha` artifact, automatically packaged
  into the consumer app's APK via transitive dependency resolution.
  Consumers don't declare libpf manually.

### Public API

- `Octet.start(context, OctetConfig)` entrypoint; license verification
  + first-run activation against `api.octetproof.com/v1/activate`,
  activation token cached at runtime.
- Predicate API `sdk.loc.isWithin(region, atTime)` with `OctetRegion`
  shapes (country, polygon, circle) and structured `OctetVerdict`
  (result / reason / message / optional cryptographic proof).
- License + activation envelopes use PASETO v4.public.
- Country-tier proofs continue to emit when GPS isn't available but
  cell-tower MCC is — covers indoor / dead-zone / Faraday-cage
  scenarios where the full proof pipeline can't run.

### Known limitations in 0.0.1-alpha

- The sample APK is not attached to this release; the release ships
  the SDK AAR only. You can build and run the sample from this
  repository's [`sample/`](sample/) directory after configuring a
  license key. A signed APK attachment will resume in a follow-up
  release.
