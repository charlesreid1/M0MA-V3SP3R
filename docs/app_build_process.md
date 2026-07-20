# Android App Build Process

How to build the Vesper Android app (`:app` module) from source.

## Requirements

| Item | Version / Notes |
|------|-----------------|
| **JDK** | 17+ (both `sourceCompatibility` and `targetCompatibility` are `VERSION_17`, `kotlinOptions.jvmTarget = "17"`) |
| **Android SDK** | `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` (Android 8.0). Install SDK Platform 35 + a matching build-tools revision via `sdkmanager` or Android Studio's SDK Manager. |
| **Android Studio** (optional) | Latest stable. Not required — command-line builds work via the Gradle wrapper. |
| **Gradle** | Provided by the wrapper (`./gradlew`). Wrapper pins Gradle **9.3.1**; do not use a system Gradle. |
| **Android Gradle Plugin** | 9.0.1 (declared in root `build.gradle.kts`). |
| **Kotlin** | 2.2.10, with matching KSP (`2.2.10-2.0.2`), serialization, and Compose plugin versions. |
| **OS** | macOS, Linux, or Windows. Repo is developed on macOS (`darwin`). |
| **Network** | First build downloads Gradle, AGP, Kotlin, and dependencies from `google()`, `mavenCentral()`, and `jitpack.io` (for `usb-serial-for-android`). |
| **`ANDROID_HOME` / `ANDROID_SDK_ROOT`** | Must point at your SDK install if not building through Android Studio. |

The Flipper Zero, an OpenRouter API key, and the Mentra bridge are runtime requirements — none are needed to compile or install the APK.

## Project layout

Single Gradle module:

- Root project: `Vesper` (see `settings.gradle.kts`)
- Application module: `:app` (namespace + `applicationId` `com.vesper.flipper`)
- `buildSrc/` — custom Gradle tasks (schema generator, see below)
- `mentra-bridge/` — separate Node.js project, **not** part of the Gradle build

## Build

### Android Studio

1. `git clone https://github.com/charlesreid1/M0MA-V3SP3R.git && cd M0MA-V3SP3R`
2. Open the repo root in Android Studio and let Gradle sync.
3. **Build → Build APK(s)** (or **Build → Generate Signed Bundle/APK** for a release build).
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

### Command line

Debug APK (unsigned in the CI sense; signed with the Android debug keystore):

```bash
./gradlew assembleDebug
```

Release APK (requires signing config — see below):

```bash
./gradlew assembleRelease
```

Install directly to a connected device / emulator:

```bash
./gradlew installDebug
```

Clean:

```bash
./gradlew clean
```

Outputs land under `app/build/outputs/apk/{debug,release}/`.

## Build types

Defined in `app/build.gradle.kts`:

| Type | Minify | Shrink resources | ProGuard | Signing |
|------|--------|------------------|----------|---------|
| `debug` | off | off | none | Android debug keystore (default) |
| `release` | on | on | `proguard-android-optimize.txt` + `app/proguard-rules.pro` | Release keystore if configured, otherwise unsigned |

`gradle.properties` sets `android.injected.testOnly=false` so `installDebug` produces a normal, persistent install rather than a test-only APK.

## Release signing

Signing is opt-in and driven by `local.properties` (which must **never** be committed). Add:

```properties
RELEASE_STORE_FILE=../keystore/vesper-release.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=vesper
RELEASE_KEY_PASSWORD=your_key_password
```

If `RELEASE_STORE_FILE` is present, `app/build.gradle.kts` registers a `release` signing config and wires it into the release build type. If it's absent, `assembleRelease` still builds — it just produces an unsigned APK you'll need to sign yourself before installing on modern Android.

## Schema generation tasks (custom)

Two Gradle tasks live in `buildSrc/` and keep `docs/execute_command_schema.json` in sync with the `CommandAction` enum in `app/src/main/java/com/vesper/flipper/domain/model/Command.kt`:

```bash
./gradlew :app:generateExecuteCommandSchema   # regenerate docs/execute_command_schema.json
./gradlew :app:verifyExecuteCommandSchema     # fail if the committed schema is stale (CI)
```

Run `generateExecuteCommandSchema` after any change to `CommandAction` or the `execute_command` arg shape and commit the updated schema. `verifyExecuteCommandSchema` is safe to run alongside `generate` — it's ordered to check the committed file before generate would overwrite it.

## Key dependencies

Everything is pinned in `app/build.gradle.kts`. Highlights:

- **UI**: Jetpack Compose (BOM `2024.06.00`), Material 3, Navigation Compose
- **DI**: Hilt 2.59.2 (with KSP compiler + `hilt-navigation-compose` + `hilt-work`)
- **Persistence**: Room 2.7.2 (KSP), DataStore Preferences 1.1.1, `security-crypto` for encrypted prefs
- **Networking / serialization**: OkHttp 4.12.0, kotlinx-serialization-json 1.8.0
- **Concurrency**: kotlinx-coroutines-android 1.10.1, WorkManager 2.9.1 (Ralph campaigns)
- **Flipper protocol**: `protobuf-java` 3.25.5
- **Images**: Coil 2.5.0 (compose + video)
- **USB serial**: `com.github.mik3y:usb-serial-for-android` 3.8.1 (pulled from JitPack)
- **Diffs**: `java-diff-utils` 4.12

## AGP 9.0 compatibility flags

`gradle.properties` sets:

```properties
android.builtInKotlin=false
android.newDsl=false
```

These opt out of AGP 9.0's new Kotlin integration and DSL so the existing build scripts keep working. Plan to migrate before AGP 10.0 (mid-2026).

## Running in the Android emulator

The Android Emulator (AVD) is the fastest way to iterate on UI, app logic, and cloud-facing flows without a physical phone. It will **not** let you exercise Flipper hardware or real BLE/USB — those need a device.

### Setup

1. In Android Studio: **Device Manager → Create Device**. Pick a phone profile (e.g. Pixel 7) and a system image at API 26 or higher (matches `minSdk = 26`). API 34/35 with Google Play images is a reasonable default.
2. Boot the AVD, then either use **Run 'app'** from Android Studio or, from the CLI with the emulator already running:

   ```bash
   ./gradlew installDebug
   adb shell am start -n com.vesper.flipper/.MainActivity
   ```

3. `adb devices` should list the emulator (e.g. `emulator-5554`) before `installDebug` will find a target.

### What you can test in the emulator

- **App logic and navigation** — bottom nav, Chat / Labs / Device / Settings screens, Compose UI states, viewmodels, Hilt wiring.
- **Room database** — all persistence: audit log, vuln triage, campaign findings, permission grants. Inspect live with **App Inspection → Database Inspector** in Android Studio.
- **Encrypted DataStore / Settings** — API key storage, auto-approve toggles, permission scopes, Ralph feature flag, glasses bridge URL. `security-crypto` works on the emulator.
- **OpenRouter API key + chat** — the emulator has full internet (host NAT), so `OpenRouterClient` calls, model fallback, rate limiting, and tool-call parsing all work end-to-end. You can hold a full conversation and watch the `execute_command` tool loop.
- **Vision preprocessing** — the emulator's camera can be pointed at a "virtual scene" or a webcam; photo attachments and the Gemini-Flash description path work.
- **On-device speech recognition** — works if the system image bundles Google speech services (Play Store images do; AOSP-only images may not). Mic input can be routed from your host machine.
- **BadUSB / SubGHz / IR payload authoring** — `PayloadEngine`, `ForgeEngine`, and `ForgeValidator` are pure logic; you can generate, validate, and diff payloads without a Flipper. They just can't be transmitted.
- **Ralph campaign scaffolding** — WorkManager, phase workers, Room-backed finding table, Approval Inbox, notifications, kill switch. Any phase that would call `CommandExecutor` against the Flipper will fail at the BLE layer, but scheduling / state-machine / approval-gate logic is fully exercisable.
- **Audit log, permissions, risk assessment** — `RiskAssessor`, `PermissionService`, `ProtectedPaths`, approval prompts, double-tap HIGH confirmation, and the audit sink all work without a Flipper connection.
- **Skills and schema** — `SkillRegistry` loads the bundled `assets/skills/` playbooks; `execute_command` schema round-trips through `VesperAgent`.

### What the emulator can't do

- **No Flipper Zero connection.** `FlipperBleService`, `FlipperProtocol`, `FlipperFileSystem`, and `MarauderBridge` need a real BLE peer. The emulator has no usable Bluetooth adapter — scans return nothing and connect attempts fail immediately.
- **No BLE recon.** `BleServiceManager` / `BleReconService` scan the phone's own Bluetooth adapter; same limitation.
- **No USB serial.** `usb-serial-for-android` requires host USB OTG hardware, which the emulator doesn't expose.
- **No SubGHz / IR / NFC / RFID / iButton / GPIO** — all of these ultimately dispatch over BLE to the Flipper.
- **No Mentra glasses pipeline** — the glasses bridge WebSocket can connect from the emulator, but there's no glasses hardware and mic/camera behavior differs from a real phone.
- **Notification / background quirks** — WorkManager and foreground services run, but Doze and background limits behave differently on the emulator; long-running Ralph campaigns are best validated on a real device before shipping.
- **Performance is not representative** — Compose recomposition timing, BLE throughput autotune (`CommandPipelineAutotuneStatus`), and TTS latency should be measured on hardware.

### Suggested workflow

Use the emulator for the inner loop (UI changes, chat + tool-calling flows, database migrations, settings, payload authoring, Ralph state-machine work), then flash to a real phone paired with a Flipper for anything that actually needs to touch hardware — hardware command execution, BLE recon, Ops Center diagnostics, and end-to-end runbook / campaign runs.

## Troubleshooting

- **Wrong JDK**: `./gradlew --version` should show `JVM: 17.x`. In Android Studio: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
- **`SDK location not found`**: set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=/path/to/Android/sdk`).
- **Sync/build broken after upgrade**: **File → Sync Project with Gradle Files**, then **Build → Clean Project → Rebuild Project**. If still broken, close Android Studio, delete `.gradle/` at the repo root, and reopen.
- **JitPack resolution failing**: `usb-serial-for-android` is served from JitPack; check network / proxy access to `jitpack.io`.
- **Schema verify fails in CI**: run `./gradlew :app:generateExecuteCommandSchema` locally and commit the updated `docs/execute_command_schema.json`.
