# Marauder — commands

> The command catalog exposed by Marauder firmware over UART, grouped
> by purpose. Each with argument shape and an example.

## What it is

Marauder's CLI is a plain-text command interface over UART at 115 200
8N1 (see `marauder-firmware.md` for framing details). Commands are
one line, arguments are short flags, output is line-oriented text.

The exact command set varies by Marauder release (see the version
notes in `marauder-firmware.md`). This doc reflects **mainline
2025-Q3 releases**; earlier versions may lack some verbs. When in
doubt, `help` on your board is authoritative.

## Command groups

### Scan

Passive information gathering. RX only. No legal issue in most
jurisdictions.

| Command                       | What it does                                                             |
|-------------------------------|--------------------------------------------------------------------------|
| `scanap`                      | Enumerate 2.4 GHz APs — BSSID, ESSID, channel, RSSI.                    |
| `scansta`                     | Enumerate stations (client devices) via probe/association frames.        |
| `scanap -c <ch>`              | Restrict scan to a single channel (1-14).                                |
| `sniffbeacon`                 | Passively log beacon frames on the current channel.                      |
| `sniffprobe`                  | Log probe requests (station SSID probing).                               |
| `sniffdeauth`                 | Detect deauth frames in the air (useful for defense / diagnostics).      |
| `sniffpmkid`                  | Passively capture PMKID material from AP beacons + associations.         |
| `sniffeapol`                  | Capture EAPOL frames (WPA handshake material).                           |
| `sniffbt`                     | Sniff BLE advertisements. Also feeds Wall-of-Flippers detection.         |
| `wof` (newer builds)          | Wall-of-Flippers — detect Flipper Zeros advertising in BLE range.        |
| `list -a` / `list -s`         | List last-scanned APs / stations (buffered on the ESP32).                |
| `select -a <idx>` / `-s <idx>` | Select target from list for subsequent commands.                        |
| `clearlist`                   | Clear the buffered scan results.                                         |
| `channel <ch>`                | Switch active channel (1-14).                                            |

Example:

```
> scanap
0    aa:bb:cc:dd:ee:ff    CoffeeShop     6    -52
1    aa:bb:cc:dd:ee:00    Guest_5G       11   -71
...
> select -a 0
Selected AP: aa:bb:cc:dd:ee:ff (CoffeeShop) ch6
```

### Attack

Active injection. **Illegal to run against networks you don't own or
have written authorization for.** See `legal-and-safety.md`.

| Command                       | What it does                                                             |
|-------------------------------|--------------------------------------------------------------------------|
| `attack -t deauth`            | Deauth flood targeting selected AP/station.                              |
| `attack -t deauth -c <ch>`    | Deauth on a specific channel.                                            |
| `attack -t beacon`            | Beacon spam. Default: random SSIDs.                                      |
| `attack -t beacon -l <list>`  | Beacon spam from `ssidlist.txt` on devboard SD.                          |
| `attack -t beacon -r`         | Rickroll beacon list (built-in).                                         |
| `attack -t rogueap -s <ssid>` | Stand up an evil-twin AP with the given SSID.                            |
| `attack -t probe`             | KARMA — respond to probe requests, luring clients.                       |
| `attack -t bad-msg`           | Malformed-frame stress test.                                             |
| `stop`                        | Stop the current attack / scan / sniff.                                  |

Example:

```
> select -a 0
> attack -t deauth
[deauth] Injecting frames on ch6 targeting aa:bb:cc:dd:ee:ff
[deauth] Sent 128 frames
> stop
[deauth] Stopped.
```

**Deauth against a specific BSSID on channel 6:**

```
> scanap -c 6
> select -a <index-of-target>
> attack -t deauth
```

### BLE spam (newer builds)

| Command                       | What it does                                                             |
|-------------------------------|--------------------------------------------------------------------------|
| `attack -t ble-spam`          | Generic BLE advertisement spam.                                          |
| `attack -t ble-spam-apple`    | Apple continuity spoofing (AirPods pairing prompts, etc.).               |
| `attack -t ble-spam-google`   | Fast Pair spoofing.                                                      |
| `attack -t ble-spam-swift`    | SwiftPair (Windows) spoofing.                                            |
| `attack -t ble-spam-samsung`  | Samsung Easy Setup spoofing.                                             |

Each has been added at various points during 2024–2025; verify
availability against your build with `help`.

### Utility

Administrative / configuration.

| Command                       | What it does                                                             |
|-------------------------------|--------------------------------------------------------------------------|
| `help`                        | List all commands.                                                       |
| `version`                     | Report Marauder version (newer builds).                                  |
| `settings`                    | Show current configuration (channel, TX power, filters).                 |
| `settings save`               | Persist current settings to devboard flash.                              |
| `settings reset`              | Reset to defaults.                                                       |
| `reboot`                      | Reboot the ESP32.                                                        |
| `led -c <r> <g> <b>`          | Set devboard LED (if present).                                           |
| `screen on/off`               | Toggle devboard screen (if present).                                     |
| `sdlist`                      | List files on the devboard's SD card (if present).                       |
| `sddump <path>`               | Dump a file over UART.                                                   |
| `updateupload`                | Enter firmware update mode.                                              |
| `stop`                        | Universal stop.                                                          |

### PCAP capture

If the devboard has a microSD, some Marauder builds save captures:

| Command                       | What it does                                                             |
|-------------------------------|--------------------------------------------------------------------------|
| `sniffpmkid -f <name>`        | Save PMKID capture to `<name>.pcap` on devboard SD.                      |
| `sniffeapol -f <name>`        | Save EAPOL capture.                                                      |
| `sniff -f <name>`             | Raw channel capture.                                                     |

Retrieve via `sddump` or by pulling the SD card physically.

## Argument conventions

- **`-a <idx>`** — AP index from the last `scanap` / `list -a`.
- **`-s <idx>`** — station index from the last `scansta` / `list -s`.
- **`-c <channel>`** — 802.11 channel, 1–14 (2.4 GHz only).
- **`-t <attack_type>`** — attack type name.
- **`-l <list>`** — beacon spam list source: `random`, `rickroll`,
  filename on devboard SD.
- **`-f <name>`** — output filename (extension added automatically).
- **`-p <count>`** — packet count (some sniffers).
- **`-b <duration_s>`** — attack duration (some builds).

Older Marauder builds use inconsistent flags — this table describes
current mainline (2025-Q3). Always `help` first.

## Bridging to Vesper

The Vesper app's `wifi_marauder` action set is currently deferred (see
`plan-deferred-wifi-marauder.md` in the repo root). When it lands, it
will wrap the commands above as typed `execute_command` actions,
enforce risk gates (`RiskAssessor`), and log every Marauder invocation
in the audit trail.

Until then, the "raw UART" path through Momentum's Marauder companion
app is the closest Vesper-adjacent surface. See
`skill-wifi-attack` (added in slice 6.6) for methodology.

## Capabilities and limits

- **Buffer sizes** on the ESP32 side are modest — scan buffers hold a
  few hundred entries. Long-running scans overflow the oldest entries.
- **Simultaneous attacks are not supported.** Send `stop` before
  starting a new one.
- **Some commands are asynchronous.** `attack -t deauth` starts and
  streams output; the ESP32 does not "return" until `stop`.
- **Command echo** varies by build. Some echo your input line before
  responding; some don't.
- **Error handling** is minimal — unknown commands print `Unknown
  command` on newer builds, silently do nothing on older ones.

## Common tasks

- **Deauth on channel 6 targeting one BSSID:**
  ```
  > scanap -c 6
  > select -a <idx>
  > attack -t deauth
  # ... run until enough disruption ...
  > stop
  ```
  Illegal without authorization.
- **Rickroll beacon spam:**
  ```
  > attack -t beacon -l rickroll
  > stop
  ```
  Legal grey area in most jurisdictions.
- **PMKID capture to laptop:**
  ```
  > sniffpmkid -f cap
  # wait for material
  > stop
  > sddump /cap.pcap
  ```
  Legal to capture (in most places); cracking must target your own
  network.
- **Wall of Flippers walk:**
  ```
  > wof
  # walk around the venue; ESP32 prints detected Flippers
  > stop
  ```

## Gotchas

- **`help` output is long.** Older Marauder builds print several
  hundred lines; the UART bridge may not survive it. Increase timeout.
- **Beacon spam interferes with Wi-Fi around you** — including your
  own devices. Expect coffee shop Wi-Fi to become unusable for
  everyone.
- **KARMA / probe response** can trigger real security incidents on
  managed networks. Corporate WIDS will notice.
- **Deauth against WPA3 or PMF-required APs is silently ineffective.**
  If nothing seems to happen, that may be why.
- **`ssidlist.txt` on the devboard SD** — expected in the root or in
  `/marauder/`. Check the current release notes.
- **Some builds' `attack -t rogueap` requires the SSID to be short**
  (~32 chars max, 802.11 limit).
- **Momentum's Marauder companion app** may not surface every mainline
  command; drop to a UART bridge if the app lacks a verb you need.

## Legal & safety notes

**LOUD.** Deauth, beacon spam, evil-twin, KARMA, BLE spam — all
regulated under intentional-interference statutes (US FCC 47 CFR
15.5), computer-misuse acts (many jurisdictions), and
telecommunications-fraud statutes. Every command in the Attack section
carries real prosecution risk if used without authorization. See
`legal-and-safety.md`.

## See also

- `marauder-overview.md` — the capabilities summary.
- `marauder-firmware.md` — CLI framing, version history.
- `marauder-wiring.md` — the UART path from Flipper to ESP32.
- `skill-wifi-attack` (slice 6.6) — methodology.
- `legal-and-safety.md` — 802.11 attack legal posture.

---
*Attribution:* JustCallMeKoko ESP32Marauder release notes + wiki, help
output from Marauder mainline (2025-Q3). Retrieved 2025-Q3.
