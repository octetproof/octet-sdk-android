# Changelog

All notable changes to the OctetSDK for Android are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

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
- **R8 `mapping.txt`** for the SDK is uploaded to an internal source-
  repo GitHub Release on every tag, to apply when de-obfuscating
  consumer crash reports against this version.

## [0.0.2-alpha] — 2026-06-04

> **v1 license-key cutover.** Wire-breaking: v0-alpha tokens issued
> before this release will not verify against the v1 verifier.
> Existing customers receive re-issued v1 tokens.

### Changed — license model (wire-breaking)

- New v1 PASETO v4.public claim schema: `iss, iat, nbf, exp, lid, sub,
  jti, typ, v, prod, pver, plat, tier, model, limits, feat, ehash,
  meta`. Tolerant-reader per spec R1; fail-closed defaults per R2; 60s
  skew tolerance on `nbf` / `exp`.
- New `LicenseError.VerificationFailed(reason)` carrying a
  `VerificationReason` enum (`BadVendorPrefix`, `UnknownKid`,
  `BadSignature`, `NotYetValid`, `WrongIssuer`, `WrongTyp`,
  `UnsupportedSchema`, `ProductNotLicensed`, `PlatformNotLicensed`,
  `ClockRollback`). The other `LicenseError` subclasses
  (`MalformedKey`, `Expired`, `ActivationWindowClosed`, `Revoked`,
  `Network`, `NoActivation`, `ServerRejected`) are unchanged.
- Activation flow: `/v1/activate` now returns a plain JSON lease (TLS
  is the integrity layer); no more signed PASETO activation tokens.
  `ActivationClient` exposes `activate` / `heartbeat` / `deactivate`
  per the v1 spec. 14-day offline grace after a successful activation.
- New device fingerprint per spec §13:
  `b64url(sha256(install_uuid || platform_hint))` where
  `platform_hint` is `Settings.Secure.ANDROID_ID`.
- Clock anti-rollback per spec §11: `AnchoredClock` persists server-
  timestamp anchors in `EncryptedSharedPreferences` (StrongBox-backed
  master key), raises `ClockRollback` when the wall clock regresses
  past tolerance.
- New v1 production signing kid `octet-2026-05-f99d` embedded in the
  registry. Pre-rotation kid `octet-2026-05-62f1` retained for token
  continuity (still resolves a public key, but its tokens fail the v1
  schema gate).

### Removed

- The v0-alpha activation-token PASETO shape and `octet.activation`
  typ. v1 activation returns plain JSON.
- `Octet.start`'s old `sdkVersion` + `appId` activate-time claims —
  token + device fingerprint are the v1 auth surface.

### Sample app

- `local.properties.example` gains an `octet.activationServerUrl` line
  (defaults to `https://api.octetproof.com`; override for LAN-backend
  testing per the source repo's `REAL_DEVICE_TESTING.md`).
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
