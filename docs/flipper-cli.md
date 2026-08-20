# Flipper Zero — on-device CLI

> The serial shell exposed by every Flipper: verbs, categories, what's
> safe, what's destructive. This is the primary interface Vesper's
> `execute_command` action targets.

## What it is

Every Flipper Zero firmware exposes a text-based command-line interface
over USB serial (CDC-ACM) and, on some firmwares, over BLE (Nordic UART
Service). Connect with qFlipper, `picocom`, `screen`, `tio`, or over
BLE via the Vesper app.

The shell is single-user, synchronous. Commands are one line, terminated
by CR/LF. Output is line-oriented text until a `>: ` prompt reappears.

Speed: **115 200 8N1** on USB by default. Some Momentum builds bump USB
to 921 600.

## How it works

### Connecting

- **USB:** plug in USB-C; the Flipper enumerates as a CDC-ACM device
  (`/dev/ttyACM*` on Linux, `/dev/tty.usbmodem*` on macOS). Any
  115 200 8N1 terminal works.
- **BLE:** pair the Flipper via the Vesper app; the app uses the
  Nordic UART Service to funnel CLI bytes.
- **qFlipper:** ships a built-in CLI tab.

Once connected: press Enter → you should see `>: `. Type `help` for the
top-level verb list.

### Category structure

Verbs are grouped by category. The exact set varies by firmware family
(see `firmware-families.md`), but the categories are stable:

| Category      | Purpose                                        |
|---------------|------------------------------------------------|
| `help`        | List all commands.                             |
| `?`           | Alias for `help`.                              |
| `device_info` | Print firmware, HW revision, unique ID, battery. |
| `power`       | Battery, OTG (5V on GPIO), reboot, off, DFU.   |
| `storage`     | Filesystem ops on `/int` and `/ext`.           |
| `subghz`      | SubGHz TX / RX / decode.                       |
| `ir` / `infrared` | IR TX / RX / decode.                       |
| `nfc`         | NFC read / emulate / dump.                     |
| `rfid`        | LF RFID read / emulate / write.                |
| `ibutton`     | 1-Wire read / emulate / write.                 |
| `gpio`        | Pin read/write, mode set, i2c/spi probes.      |
| `loader`      | Launch a `.fap` or a built-in app.             |
| `badusb`      | Play a DuckyScript payload as HID.             |
| `led`, `vibro`| Direct hardware control.                       |
| `log`         | Log level and follow.                          |
| `bt`          | BLE control (advertise, disconnect, log).      |
| `music_player`| Play `.fmf` / `.rtttl`.                        |
| `crypto`      | Some firmwares — key derivation helpers.       |

Momentum adds `js` (JavaScript runner), and expanded `subghz` /
`nfc` verbs. Unleashed / RogueMaster add region-unlock toggles under
`subghz`.

### The most-used verbs

The ones an assistant will name repeatedly:

**Discovery**

- `help` — full verb tree.
- `?` — same.
- `device_info` — canonical way to identify a Flipper. Prints firmware
  family (`hardware_name`, `firmware_commit`), HW rev, `radio_stack`,
  unique 96-bit chip ID, battery %, temperature. `FirmwareCompatibilityProfile`
  parses this — see `firmware-compatibility-profile.md`.
- `date` — current RTC time.

**Power**

- `power off` — shutdown.
- `power reboot` — reboot.
- `power reboot2dfu` — reboot into ST DFU (recovery). Not reversible
  without USB.
- `power otg_on` / `power otg_off` — 5V on GPIO pin 17.
- `power info` — voltages, currents, charge %.

**Storage**

- `storage list <path>` — directory listing.
- `storage read <path>` — dump a file's bytes.
- `storage write <path>` — start a write session; ends on Ctrl-C.
- `storage remove <path>` — delete.
- `storage mkdir <path>` — create directory.
- `storage stat <path>` — size, timestamps.
- `storage info /ext` — total / free bytes.
- `storage format /ext` — **destructive.** Reformats SD; erases all
  user data. Almost never what you want from a script.
- `storage md5 <path>` — hash a file.

**SubGHz**

- `subghz tx_from_file <path>` — retransmit a `.sub` file. RPC-preferred
  on firmwares that support it.
- `subghz tx <hex> <freq> <preset>` — transmit a raw hex packet.
  Region-locked on stock.
- `subghz rx <freq> <preset> [duration_ms]` — passive receive; prints
  decoded packets.
- `subghz decode_raw <path>` — decode a RAW_Data `.sub` capture.

**IR**

- `ir tx <path>` — transmit a saved `.ir` file.
- `ir rx` — start recording; press remote buttons.

**NFC / RFID / iButton**

- `nfc detect` — read one tag.
- `nfc emulate <path>` — play back a saved `.nfc` file.
- `rfid read` — one-shot LF read.
- `rfid emulate <path>` — emulate.
- `rfid write <path>` — write to a T5577 (destructive).
- `ibutton read` — capture a 1-Wire key.
- `ibutton emulate <path>` — emulate.
- `ibutton write <path>` — write a compatible blank.

**GPIO**

- `gpio` — list all pins with mode + level.
- `gpio set <pin> <0|1>` — force a pin high/low (mode must be output).
- `gpio mode <pin> <in|out|analog>` — set direction.
- `gpio read <pin>` — sample a pin.

**BadUSB**

- `badusb <path>` — play a DuckyScript `.txt` payload as USB HID. The
  Flipper must be tethered to a target computer over USB-C.

**Application loader**

- `loader open <appname>` — launch a built-in or `.fap` app.
- `loader signal_send <event>` — send a signal to the running app.
- `loader list` — list runnable apps.

**Log**

- `log` — toggle debug log follow.
- `log <level>` — set (`default`, `none`, `error`, `warn`, `info`,
  `debug`, `trace`).

## Capabilities and limits

- **CLI is not sandboxed.** Anything typed runs with full firmware
  privilege. Vesper's transport allowlists verbs specifically because of
  this (see `architecture.md`).
- **Some verbs are RX-preferred, some are TX-preferred.** The CLI
  reflects that — `subghz rx` streams for `duration_ms`, but `subghz tx`
  fires-and-forgets. Assistants should not "keep polling for output".
- **Line length caps** around 256–512 bytes on most firmware; longer
  BadUSB payloads must be executed by path, not inlined.
- **No shell features.** No pipes, no redirection, no globbing. Escape
  spaces with `\ ` or wrap paths in quotes on firmwares that support it.
- **BLE has stricter framing than USB.** Long `storage read` results
  chunk across notification frames; a naïve client can drop bytes.

## Safe vs destructive verbs

**LOW-risk** (read-only or reversible in seconds):

- `help`, `?`, `device_info`, `power info`, `date`.
- `storage list`, `storage read`, `storage stat`, `storage md5`,
  `storage info`.
- `subghz rx`, `subghz decode_raw`.
- `ir rx`, `nfc detect`, `rfid read`, `ibutton read`.
- `gpio` (list), `gpio read`.
- `log` toggle.

**MED-risk** (writes to SD, playback of user data — reversible but
attributable):

- `storage write`, `storage mkdir`, `storage remove` on user paths.
- `ir tx`, `nfc emulate`, `rfid emulate`, `ibutton emulate`.
- `subghz tx_from_file` (region-legal frequencies only).
- `loader open`, `badusb <path>`.
- `power otg_on` / `otg_off`.

**HIGH-risk** (destructive to Flipper state, hard to recover, or
regulated by law):

- `storage format` — wipes the SD.
- `power reboot2dfu` — leaves the Flipper in bootloader mode; needs
  reflash to recover.
- `subghz tx <hex>` on region-restricted frequencies.
- `rfid write`, `ibutton write` — writes to physical media, sometimes
  irreversibly (T5577 password-lock).
- Anything that emulates access-control credentials against a live
  reader.

Vesper's `execute_command` schema encodes exactly this risk stratification;
see `docs/execute_command_schema.json` (topic id `execute-command-schema`
is JSON, not markdown, so it doesn't appear in `list_topics`).

## Common tasks

- **Identify a Flipper:** `device_info`. Parse `hardware_name`, `firmware_commit`,
  and Momentum-specific keys like `momentum_firmware_commit`.
- **Capture then replay a SubGHz remote:**
  1. `subghz rx 433920000 AM650 10000` (10 s).
  2. If decoded, save via GUI or `subghz tx_from_file` against the
     produced `.sub`.
- **Deploy a BadUSB payload:** upload the `.txt` to
  `/ext/badusb/`, then `badusb /ext/badusb/mypayload.txt`.
- **Emergency reboot:** hold BACK for ~10 s (physical), or `power reboot`.
- **Recover from a soft-brick:** `power reboot2dfu` and reflash via
  qFlipper. See `firmware-updating.md`.

## Gotchas

- **The prompt sometimes swallows the first character** after reboot.
  Send a bare Enter first.
- **`storage write` is interactive** — it reads until Ctrl-C on the
  terminal. Automation should prefer BLE storage RPC or qFlipper's
  file transfer.
- **`subghz rx` blocks for the duration** you specify. Passing `0`
  means "until interrupted", which will hang a script.
- **CLI over BLE is slower** than USB and drops output under load.
  Prefer USB for `storage read` on large captures.
- **`log trace`** produces a firehose that can fill the CLI buffer and
  cause dropped bytes; use sparingly.
- On **Official firmware**, some verbs listed here (e.g. `js`) don't
  exist. Detect firmware with `device_info` first — see
  `firmware-compatibility-profile.md`.

## Legal & safety notes

`subghz tx*`, `nfc emulate`, `rfid emulate`, `ibutton emulate`, and
`badusb` are the tripwires. Even reading (`nfc detect`) in some
jurisdictions is regulated when the target isn't yours. See
`legal-and-safety.md`.

## See also

- `flipper-hardware.md` — the MCU behind these verbs.
- `firmware-families.md` — verb availability by firmware.
- `firmware-compatibility-profile.md` — how Vesper decides what to send.
- `flipper-gpio-pinout.md` — `gpio` verb + pin numbering.
- `subghz-overview.md`, `nfc-overview.md`, `ir-overview.md`,
  `rfid-lf-overview.md`, `ibutton-overview.md` — per-radio commands.

---
*Attribution:* Flipper-Devices CLI reference
(<https://docs.flipper.net/development/cli>), Momentum command
additions (Momentum GitHub repo `applications/main/cli/`).
Retrieved 2025-Q3.
