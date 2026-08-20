# Momentum firmware — deep dive

> ⭐ M0MA priority. The primary firmware target for M0MA-V3SP3R:
> what Momentum is, how it's structured, what it unlocks, and how the
> Vesper app treats it.

## What it is

**Momentum** is a community fork of the Flipper-Devices firmware,
maintained at <https://github.com/Next-Flip/Momentum-Firmware>. It's
the direct successor to the Xtreme firmware lineage (Xtreme's
maintainers migrated the codebase to a new organization in 2024). As of
2025-Q3 it's the most actively maintained fork with the largest
community app + asset catalog.

Momentum is where **M0MA-V3SP3R** puts its bets: the "M0MA" prefix
literally refers to *Momentum + Marauder* as the assumed operating
environment.

## How it works

### Install flow

Three routes:

1. **Web installer** — <https://momentum-fw.dev/> (or the Momentum
   organization's official domain — cite before use). Pairs the Flipper
   over WebUSB and flashes in-browser. Easiest.
2. **qFlipper + `.tgz`** — download the release bundle, drop it into
   qFlipper's Update tab, click Install. Under the hood this stages the
   `.tgz` to `/ext/update/` and calls the on-device Updater.
3. **`uFBT`** — for people building from source; `ufbt flash` after a
   `git clone` + `ufbt`.

All three exit into Momentum's boot animation and recreate the standard
`/ext/` tree if needed. Existing captures survive.

### Repository / build layout

The Momentum tree mirrors Flipper-Devices' with additions:

- `applications/main/` — core apps (subghz, nfc, ir, badusb, storage,
  etc.), forked and extended.
- `applications/external/` — Momentum-specific external apps.
- `applications/system/` — system apps.
- `applications/plugins/` — plugin apps (extra decoders, tools).
- `assets/` — the asset packs system (see below).
- `furi/` — the RTOS base, minimal Momentum-only patches.
- `lib/` — vendor libraries (subghz protocols, NFC libs, IR libs).
- `scripts/` — build tooling, uFBT integration.

Compared to Official, Momentum's diff is concentrated in
`applications/` and `assets/`; the FURI kernel is nearly identical.

### The `assets/` manifest system

Momentum introduces an asset-pack framework: each subdirectory under
`assets/dolphin/`, `assets/backgrounds/`, `assets/rgb/`, etc. has a
`manifest.txt` that declares its contents. This lets Momentum ship
multiple animation packs, boot logos, and RGB LED profiles in a single
release and lets users A/B them at runtime.

For the corpus this means: `/ext/dolphin/`, `/ext/assets/`, and
Momentum-owned `manifest.txt` files are **firmware-managed**. User
customization goes in `_user`-suffixed variants.

### Unlocked features

The Momentum-only functionality assistants routinely surface:

- **Worldwide SubGHz TX.** The CC1101 tuning table is broad; region
  checks are advisory, not enforced. You can TX on 868 MHz in the US,
  315 MHz in the EU, etc. — legality is *your* problem, not the
  firmware's. See `subghz-overview.md` for the physics and
  `legal-and-safety.md` for the caveats.
- **Extended NFC parsers.** MIFARE Classic dictionary attacks
  (mfkey32, hardnested, static-encrypted) are integrated into the NFC
  app UI. `.nfc` reads more parsers (DESFire partial, NTAG21x, iso15693
  richer support).
- **Extended BadUSB.** Additional keyboard layouts, HID+MSC combinations,
  DuckyScript extensions (loops, variables, delays with sub-second
  precision on some builds).
- **JS runner.** A JavaScript execution environment (`js` CLI verb) —
  see `flipper-js-runner.md`. Access to storage, subghz, notification,
  gpio, math, keyboard APIs.
- **Extra apps preinstalled.** SubBrute (SubGHz brute-force helper),
  KeyLogger (BadUSB variant), UARTerminal (Marauder-style bridge),
  Signal Generator, several NFC tools. All are Momentum-included and
  ship as built-ins, not `/ext/apps/*.fap` additions.
- **Extended graphics packs.** Multiple dolphin animation packs, boot
  logos, RGB LED profiles.
- **Auto-OTG on devboard detect.** If Momentum sees a specific
  devboard ID at boot, it flips OTG (5V on GPIO) on automatically.
- **Region-unlocked audio / vibration.** Cosmetic.

### `device_info` fingerprint

On Momentum, `device_info` returns keys that identify the fork:

```
hardware_name       : Flipper Zero
firmware_commit     : <sha>
firmware_target     : f7
firmware_branch     : release
firmware_version    : dev
momentum_firmware_commit : <momentum sha>   # ← Momentum-only
momentum_release_channel : stable            # ← Momentum-only
radio_stack_type    : full
```

The two `momentum_*` keys are how `FirmwareCompatibilityProfile.kt`
positively identifies the family and routes commands. See
`firmware-compatibility-profile.md`.

### What the Vesper `FirmwareCompatibilityProfile` selects on Momentum

When `FirmwareFamily.MOMENTUM` is detected:

- **Transport mode:** `CLI_AND_RPC`. Vesper prefers RPC (BLE
  app-bridged) for TX-heavy commands (`subghz tx`, `badusb`, `nfc
  emulate`) and CLI for read-only introspection.
- **`prefersRpcBridge()` verbs:** `badusb `, `subghz tx `, `subghz
  tx_from_file `, `ir tx `, `infrared tx `, BLE-spam variants, `nfc
  emulate `, `nfc emu `, `rfid emulate `, `ibutton emulate `.
- **Parser tolerance:** `.sub` parser accepts Momentum-only
  `Preset: FuriHalSubGhzPresetCustom` variants; `.nfc` parser accepts
  extended dictionary hints; `.ir` parser is permissive of
  Momentum-added metadata fields.
- **Confidence:** `HIGH` — Momentum's `momentum_*` keys are
  authoritative.

Practical consequence: an assistant advising a Vesper user on Momentum
can assume every `.sub` / `.nfc` in the "official" catalog will parse,
and can assume every legal SubGHz preset works.

## Capabilities and limits

- **Momentum ≠ magic.** The CC1101 still has hard physical limits.
  Momentum removes *software* restrictions, not physical ones.
- **Region unlocks travel with the device.** If you flash Momentum in
  the EU and travel to the US, the firmware still lets you TX
  wherever — it does not geofence.
- **App ABI stability** — Momentum ships weekly builds. `.fap` apps
  compiled against last month's Momentum may fail against this
  month's; recompile or use FapHub.
- **JS runner is Momentum-specific.** Scripts that run on Momentum will
  not run on Official. Cross-compile intent must be explicit.
- **DFU recovery** is identical to Official — Momentum uses the same
  ST bootloader. No fork-specific recovery step.

## Common tasks

- **Install Momentum from scratch:** `momentum-fw.dev` web installer,
  or drop the release `.tgz` into `/ext/update/` and run the Updater.
- **Update Momentum:** same path. `.tgz`s ship weekly.
- **Read what version you're on:** `device_info` → `momentum_firmware_commit`.
- **Enable a devboard:** Momentum handles OTG automatically for
  supported IDs; otherwise `power otg_on`. See
  `flipper-gpio-pinout.md`.
- **Run a JS script:** upload the `.js` to `/ext/apps_data/js_app/`
  (or the current path — check the JS runner app's UI), open the JS
  runner, select. Or: `js <path>` from the CLI on Momentum builds that
  expose it. See `flipper-js-runner.md`.
- **Verify Vesper is speaking to a Momentum Flipper:** in the app,
  check the connect toast; if `FirmwareCompatibilityProfile` matched
  `MOMENTUM`, all Momentum-only CLI verbs are enabled in the
  action allowlist.

## Gotchas

- **The Momentum SubGHz TX button is fast.** Because region checks
  don't block, an accidental button-mash can TX. Vesper's `RiskAssessor`
  still enforces its own gates — that's the safety net, not the
  firmware.
- **Some Momentum "extras" are just repackages.** Extended NFC parsers
  are upstream contributions Momentum ships early; Official often
  absorbs them within months. Don't assume "Momentum-only" is a
  permanent axis.
- **Web installer requires WebUSB.** Chromium-family browsers only;
  Firefox will silently do nothing.
- **Momentum's `.tgz` cannot install on Official's Updater.** Rollback
  requires flashing Official's `.tgz` separately. See
  `firmware-updating.md`.
- **`momentum_firmware_commit` is not present on Xtreme.** Old Xtreme
  builds set `xtreme_firmware_commit` instead. Both map to
  `FirmwareFamily.MOMENTUM` (with `XTREME` as the enum branch) in
  Vesper's profile table.

## Legal & safety notes

Momentum removes software region checks; you become the enforcer. TXing
on 868 MHz in the US is *technically* possible but violates FCC Part 15.
Cloning access-control credentials on Momentum is no more or less legal
than on any other firmware — see `legal-and-safety.md`.

## See also

- `firmware-families.md` — where Momentum sits among the alternatives.
- `firmware-compatibility-profile.md` — exact Vesper detection mechanics.
- `firmware-updating.md` — install / rollback / DFU.
- `flipper-js-runner.md` — the Momentum JS runtime.
- `subghz-overview.md` — what "region unlocked" actually means.
- `marauder-firmware.md` — Marauder integration Momentum ships with.

---
*Attribution:* Momentum Firmware GitHub
(<https://github.com/Next-Flip/Momentum-Firmware>), Momentum web
installer (<https://momentum-fw.dev/>), Momentum wiki. Retrieved
2025-Q3.
