# Marauder — firmware

> Flashing, release cadence, and the UART CLI protocol the ESP32 speaks.

## What it is

**Marauder firmware** is an ESP32 program by JustCallMeKoko that
implements Wi-Fi (and some BLE) attack tools. On the ESP32 side it's a
regular esp-idf project; on the Flipper side, it presents as a
UART-connected CLI at 115 200 8N1 (some newer builds default to
115 200 with an option to raise).

Marauder is separate from Flipper firmware. The Flipper's on-device
"Marauder" app (Momentum ships one; RogueMaster and Unleashed ship
variants) is a UART bridge — it forwards typed commands, displays
responses. All the intelligence is on the ESP32.

## How it works — flashing

Three ways to flash the ESP32:

### 1. The Marauder web installer

By far the easiest. Chromium-family browser, plug in USB to the
devboard's own USB-C (bypass the Flipper), navigate to
<https://flasher.tools/marauder> (or the current maintainer's
installer domain — cite before use), click Flash.

Requirements:

- Chromium-family browser (Chrome, Edge, Brave — WebSerial).
- ESP32 devboard with its own USB port (most third-party carriers have
  one; the official Flipper Wi-Fi Devboard does too via a USB-C on
  the pink board itself).
- Flipper does not need to be connected to the devboard during flash.

The web installer picks the right binary for your ESP32 variant
(WROOM-32, S2, C3) and handles bootloader + partition table + app
image atomically.

### 2. `esptool.py`

Manual command-line path. For CI, headless environments, or when the
web installer is broken:

```
# example — not runnable here
esptool.py --port /dev/tty.usbserial-* --baud 921600 \
  write_flash --flash_size detect \
  0x0 bootloader.bin \
  0x8000 partitions.bin \
  0x10000 marauder.bin
```

Exact offsets vary by ESP32 variant; the release notes list them.

### 3. esp-idf from source

Clone Marauder's repo, `idf.py set-target esp32`, `idf.py flash`. Use
this when developing patches or when targeting an unsupported ESP32
variant.

## Release cadence

Marauder mainline sees monthly releases as of 2025-Q3 (verify
<https://github.com/justcallmekoko/ESP32Marauder/releases> before
promising a cadence). Momentum's *bundled* Marauder version lags
mainline by 1–3 releases — Momentum tests + patches integrations
before shipping.

- **Marauder mainline latest (2025-Q3):** verify at release page.
- **Momentum-bundled Marauder companion app:** ships new features
  behind the current Momentum firmware version. Check
  `applications/external/wifi_marauder/` on Momentum's tree.

## The UART CLI protocol

### Framing

- **Baud:** 115 200 8N1 default (some builds negotiate up).
- **Line ending:** `\n` (LF); CRLF tolerated on RX.
- **Prompt:** `>` after successful command completion. Some builds
  emit no prompt during long-running commands (scans, attacks) — you
  see continuous output until `stop` is sent.
- **Long-running commands:** most attacks (`attack`, `sniff*`,
  `scan*`) stream output until either (a) the caller sends `stop`
  or (b) a timeout / max-target-count kicks in.

### Command shape

```
<verb> [-<flag> <value>] [-<flag> <value>] ...
```

Verbs are unquoted, flags are short. Long options exist on some
builds. Examples:

- `help`
- `scanap` — start scanning APs.
- `scansta` — start scanning stations.
- `stop` — stop the current activity.
- `select -a <index>` — select target from a list.
- `attack -t deauth` — start a deauth attack against selection.

See `marauder-commands.md` for the full catalog.

### Output

- **List output:** plain text, one item per line, tab-separated
  columns. E.g. `0<TAB>BSSID<TAB>ESSID<TAB>Channel<TAB>RSSI`.
- **Status output:** freeform log lines interleaved with the current
  activity.
- **Error output:** prefixed with `Err:` on newer builds; older builds
  just print the message.

### Version detection

`help` on any build lists supported verbs. To fingerprint the version,
`version` (newer builds) returns the release string. Older builds
require inferring from the `help` output shape.

## What commands land in which release (as of 2025-Q3)

Rough historical snapshot; verify against the release notes for your
build:

- **v1.2.x-era** — original core: `scanap`, `scansta`, `attack`
  (deauth, beacon-spam basic), `sniff` (basic).
- **v1.3.x-era** — added evil-twin AP mode (`attack -t rogueap`),
  KARMA (`attack -t probe`), PMKID sniffer.
- **v1.4.x-era (2024)** — Wall of Flippers, extended beacon spam
  (SSID lists from `/marauder/ssidlist.txt`), improved evil-twin.
- **v1.5.x-era (2025)** — BLE spam variants, refined PMKID capture,
  ESP32-S2 support (partial).

The Momentum-bundled companion may lag. Vesper's `wifi_marauder`
action set is currently deferred (see `plan-deferred-wifi-marauder.md`
in the repo root) — the corpus documents what's *possible*, the
action set (when it lands) will document what Vesper actually wires up.

## Capabilities and limits

- **UART is half-duplex-ish for streaming attacks.** You can send
  `stop` mid-stream; some builds echo it, some don't.
- **Buffer sizes** — the ESP32 emits fast; a slow reader on the
  Flipper (or Vesper) can drop output during heavy scans.
- **Multiple simultaneous attacks: no.** Sending `attack -t X` while
  another attack is running either replaces or is rejected, depending
  on build.
- **SD card slot on the devboard** — some devboards have a microSD
  slot Marauder uses for `pcap` captures. Check your board.

## Common tasks

- **Flash Marauder for the first time:** web installer at
  flasher.tools/marauder (verify domain), USB the devboard directly.
- **Update Marauder:** same route.
- **Verify Marauder is talking:** open the Marauder companion app on
  the Flipper → hit `help` → confirm output.
- **Extract a `pcap`:** run capture, mount devboard SD in a laptop,
  open in Wireshark.
- **Roll back Marauder:** flash an older release via `esptool.py`.

## Gotchas

- **Wrong ESP32 variant** — flashing WROOM binary onto S2 will boot
  into "invalid header" and hang. Use the web installer's auto-detect,
  or check the chip yourself with `esptool.py chip_id`.
- **Web installer needs WebSerial.** Firefox lacks it as of 2025-Q3.
- **Old Momentum companion apps** may not know new Marauder verbs.
  Update the app after updating Marauder firmware — or use a raw UART
  bridge (`uart_bridge`).
- **PMKID capture on newer APs** requires the AP to be in the middle
  of a WPA handshake. On sparse networks you may need `deauth`
  concurrently (which is illegal against networks you don't own).
- **Marauder's `settings save`** stores state on the devboard's flash;
  don't confuse it with Flipper `/ext/marauder/`.

## Legal & safety notes

Marauder firmware itself is not illegal to possess or install. Running
its offensive verbs against networks you don't own is. See
`legal-and-safety.md`.

## See also

- `marauder-overview.md` — the big picture.
- `marauder-wiring.md` — physical wiring.
- `marauder-commands.md` — the full command catalog.
- `flipper-cli.md` — Flipper-side `power otg_on`.
- `firmware-momentum.md` — Momentum's built-in Marauder companion app.

---
*Attribution:* JustCallMeKoko ESP32Marauder release notes
(<https://github.com/justcallmekoko/ESP32Marauder/releases>), Marauder
wiki, `flasher.tools`. Retrieved 2025-Q3.
