# Firmware families

> The Flipper Zero has several actively-maintained firmwares. What
> each one is, where they differ, and which one Vesper is targeting.

## What it is

The Flipper Zero runs FURI, a FreeRTOS-derived RTOS built by
Flipper-Devices. The **application layer** on top of FURI — apps, CLI
verbs, protocol decoders, GUI, `.sub`/`.nfc`/`.ir` parsers — is where
firmware forks diverge. All families listed here are drop-in
replacements: you flash one, reboot, and use the Flipper normally.

Four families matter in 2025:

| Family        | Upstream                                                     | Maintenance status (as of 2025-Q3)         |
|---------------|--------------------------------------------------------------|--------------------------------------------|
| **Official**  | `flipperdevices/flipperzero-firmware` (GitHub)               | Actively maintained by Flipper-Devices.    |
| **Momentum**  | `Next-Flip/Momentum-Firmware` (community, successor of Xtreme) | Actively maintained; primary M0MA target. |
| **Unleashed** | `DarkFlippers/unleashed-firmware`                            | Actively maintained; region-unlock focus.  |
| **RogueMaster** | `RogueMaster/flipperzero-firmware-wPlugins`                | Actively maintained; kitchen-sink build.   |

Xtreme is the ancestor of Momentum; it's still on GitHub but archived
in favor of Momentum. If someone mentions Xtreme in 2025, they usually
mean "Momentum with older packs."

## How it works

### Where they diverge

Every fork inherits the upstream Flipper-Devices base and then patches
**four rough axes**:

1. **SubGHz region policy.** Stock Official respects the FCC/ETSI
   region tables and refuses to TX outside them. Momentum / Unleashed /
   RogueMaster remove or soften this: they let you tune anywhere the
   CC1101 physically can, and TX on any window. This is the single
   most-cited reason to run a fork.
2. **App set.** Forks preinstall community apps that Flipper-Devices
   won't ship — extra `.sub` protocol packs, extra IR remote databases,
   extra NFC parsers, brute-force tools (mfkey, KeyLogger, etc.), a
   JavaScript runner (Momentum), sub-brute helpers, WPA-PSK utilities
   that assume a Marauder devboard is attached.
3. **Assets.** Custom animation packs, extra dolphin moods, alternative
   font packs, boot logos.
4. **CLI verbs.** Forks add convenience verbs — most commonly a
   region-toggle, a `js` runner (Momentum), and expanded `nfc` /
   `subghz` subcommands.

The **base radio stack (BLE, USB, IPCC, FURI kernel)** is identical
across all four. Compatibility with the Vesper app is therefore mostly
a matter of *which CLI verbs exist* — see
`firmware-compatibility-profile.md`.

### Feature compatibility matrix

Rough summary. Not a substitute for `device_info` at runtime.

| Feature                                    | Official | Momentum   | Unleashed | RogueMaster |
|--------------------------------------------|----------|------------|-----------|-------------|
| SubGHz region unlock (worldwide TX)        | No       | Yes        | Yes       | Yes         |
| Extra `.sub` protocol packs                | No       | Yes        | Yes       | Yes         |
| MIFARE key dictionary preinstalled         | Small    | Extensive  | Extensive | Extensive   |
| Hardnested / Nested attack helpers on-device| No      | Yes        | Yes       | Yes         |
| JavaScript runner (`js`)                   | No       | Yes        | Partial   | Partial     |
| BadUSB expanded (extra layouts, HID+)      | Basic    | Yes        | Yes       | Yes         |
| ESP32 companion CLI passthrough            | Manual   | Yes        | Yes       | Yes         |
| Momentum `assets/` manifest system         | No       | Yes        | No        | No          |
| Custom animation packs                     | No       | Yes        | Yes       | Yes         |
| FapHub / catalog access                    | Yes      | Yes (extended) | Yes   | Yes         |
| Auto-OTG on devboard detect                | No       | Yes        | No        | Partial     |

**⭐ Momentum is the primary M0MA target.** When we say "the firmware"
without qualification, we mean Momentum. See `firmware-momentum.md`.

### How Vesper detects which one is running

`FirmwareCompatibilityProfile` (in `app/src/main/java/com/vesper/flipper/ble/`)
reads `device_info` on connect and matches strings to a `FirmwareFamily`
enum: `UNKNOWN | OFFICIAL | MOMENTUM | UNLEASHED | ROGUEMASTER | XTREME`.
Each family maps to a *transport mode* (CLI-only, RPC-only, both) and a
route policy for potentially-dangerous commands. See
`firmware-compatibility-profile.md` for the exact mechanics.

## Capabilities and limits

- All families accept the same `.tgz` update format. A Momentum `.tgz`
  cannot be flashed on top of Official (it will be rejected as
  signature-mismatched on Official's Updater); DFU-flashing is the
  cross-family path.
- No family can add hardware. Wi-Fi still requires a devboard on the
  GPIO header regardless of firmware.
- Region unlocks are jurisdictionally illegal to *use* in most
  countries. Flashing the firmware isn't the crime; TXing outside the
  legal window is. See `legal-and-safety.md`.
- Forks lag Official on radio-stack updates by weeks. If Flipper-Devices
  ships a BLE fix, Momentum absorbs it in a follow-up release, not the
  same day.
- Some app catalogs are family-specific: a `.fap` compiled against
  Momentum's tree may not run on Official (missing API symbols).

## Common tasks

- **Switch from Official to Momentum:**
  1. Back up `/ext/` to a laptop (copy the SD contents).
  2. Download the current Momentum release `.tgz`.
  3. Use the Momentum web installer, or place the `.tgz` in
     `/ext/update/` and boot the on-device Updater.
  4. On first boot, Momentum recreates the standard `/ext/` tree —
     your `/ext/subghz`, `/ext/nfc` etc. captures are preserved.
- **Verify what's running:** connect via `flipper-cli.md`,
  `device_info`. Look at `hardware_name`, `firmware_commit`, and
  Momentum-specific keys like `momentum_firmware_commit`.
- **Roll back:** flash the Official `.tgz` the same way. Your `/ext/`
  data survives (family-specific paths like `/ext/momentum/` become
  orphaned but not harmful).
- **Add a new fork to Vesper's profile table:** edit
  `FirmwareCompatibilityProfile.kt` (see
  `firmware-compatibility-profile.md`), extend the enum, ship a
  matching profile row.

## Gotchas

- **"Xtreme" is not a fifth family.** It's the Momentum predecessor.
  Momentum's `device_info` still says `xtreme_firmware_commit` on some
  older builds — hence the profile enum includes `XTREME`.
- **Downgrading may lose per-firmware data.** RogueMaster keeps state
  under `/ext/rogue/`; Momentum keeps state under `/ext/momentum/`.
  Deleting these is safe on rollback but wipes any custom pack
  preferences.
- **Region unlocks are not universal.** Momentum lets you tune to
  868 MHz and TX. Whether that's *legal* depends on where you are.
- **Fap ABI drift** — a `.fap` built for one family may crash on
  another. FapHub versions apps per-family.
- **Official's Updater refuses fork bundles.** If you tried to install
  a Momentum `.tgz` on Official and it silently did nothing, that's
  why. Use the web installer or DFU.

## Legal & safety notes

Choosing a firmware family is a legal choice as well as a technical one.
Region unlocks let the Flipper *technically* TX on prohibited bands;
using them may violate FCC 47 CFR Part 15 (US), ETSI SRD rules (EU),
or equivalents elsewhere. See `legal-and-safety.md`.

## See also

- `firmware-momentum.md` — the primary M0MA target, in depth.
- `firmware-compatibility-profile.md` — how Vesper picks a profile.
- `firmware-updating.md` — flashing, DFU, brick recovery.
- `flipper-cli.md` — verbs whose availability varies by family.
- `legal-and-safety.md` — region policy consequences.

---
*Attribution:* Flipper-Devices firmware repo
(<https://github.com/flipperdevices/flipperzero-firmware>), Momentum
firmware repo (<https://github.com/Next-Flip/Momentum-Firmware>),
Unleashed firmware repo (<https://github.com/DarkFlippers/unleashed-firmware>),
RogueMaster firmware repo. Retrieved 2025-Q3.
