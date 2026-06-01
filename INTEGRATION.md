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
  `ACCESS_BACKGROUND_LOCATION` — location pipeline.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` — proof generation
  runs as a foreground service.
- `WAKE_LOCK` — keep the device responsive during long fix windows.
- `ACTIVITY_RECOGNITION` — motion classification.
- `READ_MEDIA_*` and battery-optimization permissions for SDK 30+.

Integrators don't need to copy these into their own manifest. **But
runtime permissions still need to be requested from the user** on
Android 6 (API 23)+:

| Permission | When to request | Notes |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Before `Octet.start(...)`. | SDK refuses to start without it. |
| `ACCESS_BACKGROUND_LOCATION` | After `ACCESS_FINE_LOCATION` is granted, only if background proofs are wanted. | Android 10+ requires this as a separate prompt. |
| `ACTIVITY_RECOGNITION` | Before `Octet.start(...)`. | Android 10+. Motion-classification features degrade gracefully if denied, but request it for full proof confidence. |

---

## Reference implementation

The sample app in [`sample/`](sample/) exercises the minimum viable
permission flow if you need a reference.

---

## Updates to this document

Updates to this document arrive with each SDK release. Re-check it
when upgrading.
