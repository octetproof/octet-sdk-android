# OctetV1Toy — Android sample app

Minimal demo of the OctetSDK public API: country dropdown, live device
map, one verdict button. Use this to confirm your environment is set
up correctly and to play with the predicate API without the noise of a
full app.

This is a **standalone Gradle project** — `git clone`, configure your
license key, and build. It consumes the published OctetSDK from the
parent repository's Maven branch; no source dependency.

## What it does

- Requests `ACCESS_FINE_LOCATION` at runtime.
- Calls `Octet.start(context, OctetConfig(licenseKey = ...))` — the SDK
  verifies your license, activates against
  `api.octetproof.com/v1/activate` on first run, caches the activation
  token, and brings up the proof pipeline.
- Renders the device's live GPS position on an OpenStreetMap tile view
  via [osmdroid](https://github.com/osmdroid/osmdroid) (no API key
  required).
- On button tap, runs
  `sdk.loc.isWithin(OctetRegion.country(isoCode), Instant.now())`
  against the country you picked from the dropdown and renders the
  verdict.

## Prerequisites

- Android 11 (API 30) or newer on the device.
- Android Studio Hedgehog (2023.1.1) or newer **OR** JDK 17 + the
  Android SDK command-line tools (for the CLI workflow).
- A valid OctetSDK license key — free trial from
  [sdk.octetproof.com/signup](https://sdk.octetproof.com/signup).

## 1. Configure your license key

```bash
cp local.properties.example local.properties
```

Open `local.properties` and set:

- **`octet.licenseKey`** — your license key. Missing key isn't a build
  error; the app launches and throws `LicenseError.MalformedKey` at
  `Octet.start` instead.
- **`sdk.dir`** — the Android SDK install path. Android Studio writes
  this automatically the first time you open the project, so **IDE
  users can leave the line commented out**. **CLI users** on a machine
  that hasn't run Studio against this project must uncomment the line
  and set it (e.g. `/Users/<you>/Library/Android/sdk` on macOS), or
  export `ANDROID_HOME` in their shell.

`local.properties` is gitignored — your edits stay local.

## 2a. Build & run via Android Studio (IDE workflow)

1. Launch Android Studio → **File → Open** → select this `sample/`
   directory (not its parent).
2. Wait for the initial Gradle sync to complete. Studio will write
   `sdk.dir` into `local.properties` automatically.
3. Plug in your device (USB debugging on, see below) and pick it from
   the device dropdown.
4. Press **Run ▶︎** on the `app` configuration. Studio builds, signs
   the debug APK, installs, and launches in one step.

To enable USB debugging on the device:
**Settings → About → tap *Build number* 7×** to unlock Developer
options, then enable **USB debugging**. Confirm the device is visible
via `adb devices`.

## 2b. Build & run via command line (CI / scripting workflow)

From this `sample/` directory:

```bash
# Build + install + launch in one step:
./gradlew :app:installDebug

# Or build the APK and sideload manually:
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a release APK (unsigned by default in this sample — signing is the
responsibility of consumers of the SDK, not the SDK itself):

```bash
./gradlew :app:assembleRelease
```

## First-launch permissions

You'll see a prompt for **Location**. Grant it; the SDK refuses to
start otherwise. Then tap the button to fire a verdict.

## What the SDK ships as

A single AAR (`com.octetproof:sdk:VERSION`) resolved from the
`mvn-repo` branch of this repository — Maven repo configured in
`settings.gradle.kts`. The AAR bundles its own native dependencies
(GDAL + particle filter); your app doesn't declare them.

For the full SDK API surface, see the parent repository's
[README](../README.md) and [INTEGRATION.md](../INTEGRATION.md).

## License

The OctetSDK and this sample app are released under the terms in
[LICENSE](../LICENSE).
