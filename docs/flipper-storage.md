# Flipper Zero — storage layout

> How the Flipper's filesystems are organized: `/int` vs `/ext`, what
> lives where, which file extensions you'll encounter.

## What it is

The Flipper Zero exposes two storage roots:

- **`/int/`** — internal flash on the STM32WB55, a small (~200 KB
  writable) FAT-like partition. Reserved for firmware-owned state and
  factory data. Users rarely touch it.
- **`/ext/`** — the microSD card, FAT32-formatted. Everything
  user-visible — captures, savefiles, apps, wallpapers, updates —
  lives here.

Firmware auto-creates the standard `/ext/` tree on first boot if it's
missing.

## How it works

### `/int/` — internal flash

Not a general filesystem. Firmware writes to `/int/` for:

- Boot flags, factory calibration values.
- One-time-programmable data (unique ID, per-device secrets, U2F
  attestation on some firmwares).
- A small config cache used before the SD card is mounted.

If `/ext/` is absent, some flows fall back to `/int/`, but most apps
refuse to run without an SD card.

### `/ext/` — SD card layout

Canonical directories under `/ext/` (on Momentum; official is a subset):

| Path                          | Purpose                                                                     |
|-------------------------------|-----------------------------------------------------------------------------|
| `/ext/subghz/`                | User-captured `.sub` files and RF savefiles.                                |
| `/ext/subghz/assets/`         | Protocol maps, brute-force dictionaries (Momentum).                         |
| `/ext/nfc/`                   | `.nfc` savefiles across MIFARE / ISO14443A / ISO15693.                      |
| `/ext/nfc/assets/`            | MIFARE key dictionaries (`mf_classic_dict.nfc`, `mf_classic_dict_user.nfc`).|
| `/ext/rfid/`                  | LF RFID `.rfid` files (EM4100, HID, T5577 configs).                         |
| `/ext/ibutton/`               | `.ibtn` files (Dallas DS1990A, CYFRAL, Metakom).                            |
| `/ext/infrared/`              | `.ir` files, custom remotes.                                                |
| `/ext/badusb/`                | `.txt` DuckyScript / BadUSB payloads. See `flipper-fap-apps.md`.            |
| `/ext/apps/`                  | Installed `.fap` applications.                                              |
| `/ext/apps_assets/`           | Read-only asset packs shipped with `.fap` apps.                             |
| `/ext/apps_data/`             | Per-app writable state (settings, cached data).                             |
| `/ext/update/`                | Firmware update `.tgz` bundles (staging for on-device Updater).             |
| `/ext/dolphin/`               | Animation packs / mood state / "dolphin" progression data.                  |
| `/ext/nfc_magic/`             | Magic-card cloning workspace (some firmwares).                              |
| `/ext/wav_player/`            | `.wav` files for the WAV player app.                                        |
| `/ext/music_player/`          | `.fmf` / `.rtttl` for the music player.                                     |
| `/ext/gpio/`                  | GPIO usb-uart-bridge settings, saved pin states.                            |
| `/ext/lfrfid/`                | (Legacy path; Momentum uses `/ext/rfid/`.)                                  |

Some firmwares add more (Xtreme lineage: `/ext/xtreme/`, RogueMaster:
`/ext/rogue/`). Momentum uses `/ext/momentum/` for its per-firmware
settings.

### File formats you'll encounter

| Extension  | Content                             | Reference doc                             |
|------------|-------------------------------------|-------------------------------------------|
| `.sub`     | SubGHz capture (decoded or raw)     | `signal-formats-sub.md`                   |
| `.nfc`     | NFC tag dump / emulation savefile   | `signal-formats-nfc.md`                   |
| `.ir`      | IR remote / signal set              | `signal-formats-ir.md`                    |
| `.rfid`    | LF RFID card savefile               | `rfid-lf-overview.md`                     |
| `.ibtn`    | iButton (1-Wire) savefile           | `ibutton-overview.md`                     |
| `.txt`     | BadUSB DuckyScript payload          | `flipper-fap-apps.md`                     |
| `.fap`     | Flipper Application Package (app binary) | `flipper-fap-apps.md`                |
| `.js`      | Momentum JS runner script           | `flipper-js-runner.md`                    |
| `.fmf`     | Flipper Music Format                | —                                         |
| `.mus`     | Music player alt format             | —                                         |
| `.rtttl`   | Ring Tone Text Transfer Language    | —                                         |
| `.tgz`     | Firmware update bundle              | `firmware-updating.md`                    |
| `.dfu`     | DFU-mode firmware image             | `firmware-updating.md`                    |

All Flipper savefile formats are **plain text** with a `Filetype:` /
`Version:` preamble, except `.fap`, `.tgz`, `.dfu` which are binary.
This is important: you can grep `/ext/` and get meaningful hits.

## Capabilities and limits

- FAT32 filename length is capped at 255 chars, but you should stay
  under ~100 for portability across firmwares.
- Directory listings over CLI and BLE can be slow with thousands of
  files in a single folder — `subghz` collectors especially should
  shard by date or protocol.
- The SD card is hot-swappable **only** when the Flipper is powered
  off. Pulling it live may corrupt open captures.
- Update `.tgz` bundles must land in `/ext/update/` (a single bundle
  per session); the Updater consumes them in place.

## Common tasks

- **List a directory:** `storage list /ext/subghz` from the CLI.
- **Read a file:** `storage read /ext/subghz/mycapture.sub`.
- **Upload from a laptop:** either mount the SD directly in a card
  reader, or use qFlipper's storage tab over USB, or use Vesper's
  `send_file` / `storage.write_file` action.
- **Reset the tree:** delete `/ext/*` except your captures, reboot; the
  standard tree regenerates.
- **Move a capture to the app catalog:** copy `.sub` into
  `/ext/subghz/<subdir>/` and browse from the SubGHz app.

## Gotchas

- **`/ext/apps_data/<app>/` is preserved across app upgrades.**
  Don't put throwaway data there.
- **`/ext/subghz/assets/` and `/ext/nfc/assets/` are firmware-owned.**
  Momentum will re-overwrite user edits on update. If you want a custom
  dictionary, use the `_user.nfc` / `_user.sub` variant, which is
  preserved.
- **Case-sensitivity:** FAT32 is case-preserving but case-insensitive.
  Firmware code assumes case-insensitive; don't rely on
  `MyCapture.sub` vs `mycapture.sub` being distinct.
- **Deleting `/ext/update/` mid-flash bricks the update**, not the
  Flipper — power-cycle and re-copy the `.tgz`.

## Legal & safety notes

Storage layout is not itself regulated. What you capture and store may
have implications — see `legal-and-safety.md` for the RF capture and
credential-cloning notes.

## See also

- `flipper-hardware.md` — SD slot physical layout.
- `flipper-cli.md` — `storage.*` verbs.
- `signal-formats-sub.md`, `signal-formats-nfc.md`, `signal-formats-ir.md`
  — file formats.
- `firmware-updating.md` — how `/ext/update/` is consumed.
- `flipper-fap-apps.md` — `/ext/apps/` layout.

---
*Attribution:* Flipper-Devices file-format documentation
(<https://docs.flipper.net/file-formats>), Momentum firmware repo
(directory conventions). Retrieved 2025-Q3.
