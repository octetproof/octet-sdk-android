# Changelog

All notable changes to the OctetSDK for Android are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

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
