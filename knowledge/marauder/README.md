# Marauder — overview

> ⭐ M0MA priority. The Wi-Fi devboard's primary firmware: what it is,
> what it can do, why 802.11 needs a companion at all.

## What it is

**Marauder** is an ESP32-based Wi-Fi attack framework by
**JustCallMeKoko** (<https://github.com/justcallmekoko/ESP32Marauder>).
It runs on a companion **ESP32** board attached to the Flipper Zero's
GPIO header via UART. Once flashed, the Flipper drives it as a Wi-Fi
attack tool: scan for networks and clients, deauth, beacon spam,
evil-twin AP, KARMA probe response, PMKID harvest, Wall-of-Flippers
detection, and a growing catalog of related capabilities.

Marauder is why the "M0MA" in this repo's name stands for *Momentum +
**Marauder***: the Vesper app assumes a Marauder-equipped devboard is
attached when Wi-Fi actions are requested.

## Why 802.11 needs a companion at all

The Flipper Zero's main MCU is an **STM32WB55**, which has an on-die
2.4 GHz radio — but that radio is **BLE-only**. It cannot speak
802.11. The chip physically lacks the baseband + protocol engine for
Wi-Fi.

To do Wi-Fi anything — scan, connect, attack — the Flipper needs a
separate radio. The Wi-Fi devboard on the GPIO header is exactly that
separate radio: an **ESP32** MCU with 802.11a/b/g/n baked in. The
Flipper sends UART commands, the ESP32 executes them and streams
results back.

## The hardware

Two revisions of the Flipper Wi-Fi Devboard sold by Flipper-Devices,
plus third-party carriers:

| Rev / vendor                       | ESP32 module           | Notes                                            |
|------------------------------------|------------------------|--------------------------------------------------|
| Flipper Wi-Fi Devboard (2022–2023) | ESP32-WROOM-32          | Screaming pink; screw-terminals on early runs.   |
| Flipper Wi-Fi Devboard (2024+)     | ESP32-S2 in some batches; ESP32-WROOM-32 in others | Cite the current FZ product page before firmware choice. |
| Third-party carrier PCBs           | Varies (ESP32-WROOM-32, ESP32-S2, ESP32-C3) | Wiring identical if TX/RX/GND/3V3 match. |

Marauder targets the **ESP32 (classic and S2)**; ESP32-C3 support is
partial as of 2025-Q3. Confirm before flashing.

## What Marauder does — capability summary

| Capability            | What it does                                                                       | Detection    | Legal to use? |
|-----------------------|------------------------------------------------------------------------------------|--------------|---------------|
| **Scan AP**           | Enumerates 2.4 GHz APs in range (BSSID, ESSID, channel, RSSI).                    | Passive.     | Yes.          |
| **Scan Station**      | Enumerates clients (probe requests, association frames).                          | Passive.     | Yes.          |
| **Sniff**             | Raw 802.11 capture on a channel.                                                  | Passive.     | Yes (RX only).|
| **Deauth**            | Injects deauthentication frames to a target AP/client pair.                       | Loud.        | No — DoS.     |
| **Beacon Spam**       | Emits fake beacon frames advertising arbitrary SSIDs. Rickroll / SSID lists.      | Loud.        | Grey — depends on jurisdiction. |
| **Evil Twin / AP**    | Stands up a rogue AP with a chosen SSID (captive portal optional).                | Very loud.   | No without authorization. |
| **KARMA / Probe Response** | Responds to probe requests with matching SSID, luring clients to associate.  | Loud.        | No without authorization. |
| **PMKID capture**     | Harvests PMKIDs from AP beacons + associations. Feeds `hashcat -m 16800`.         | Passive.     | Grey — capture legal in some jurisdictions, crack only against your own network. |
| **WPA handshake capture** | Captures the 4-way handshake on a target BSSID.                              | Passive.     | Same as PMKID.|
| **EAPOL logging**     | Logs 802.1X EAPOL frames.                                                         | Passive.     | Same.         |
| **Wall of Flippers**  | Detects nearby Flipper Zeros advertising via BLE.                                 | Passive BLE. | Yes.          |

**802.15.4 / Bluetooth** capabilities are on the ESP32 hardware but not
part of the Marauder feature set as of 2025-Q3.

## Firmware and installation

See `marauder-firmware.md` for the flashing workflow, release cadence,
and CLI protocol details.

## Wiring

See `marauder-wiring.md` for the physical wiring: TX/RX/GND/3V3
routing, common failure modes, "screaming pink" official variant vs
third-party carriers.

## Commands

See `marauder-commands.md` for the full CLI catalog exposed by Marauder
firmware over UART.

## Capabilities and limits

- **2.4 GHz only.** Marauder does not do 5 GHz. Modern APs on 5 GHz-only
  channels are unreachable.
- **Single channel at a time.** 802.11 attacks target a specific
  channel; scan first, then attack.
- **Range** — an ESP32-WROOM has ~+18 dBm TX and reasonable RX. With
  the on-board PCB antenna, expect 30–50 m in open air, less indoors.
- **Deauth is trivially detectable** by any WIDS worth its name. Public
  networks may have monitoring; corporate networks almost certainly do.
- **No monitor mode** for full packet capture with radiotap in the sense
  of a laptop with an Atheros card. Marauder's sniff mode is capable
  but constrained.
- **PMKID / handshake capture is passive** — you don't need to attack
  the network to get the material. Cracking is off-device on a laptop.
- **Beacon spam and KARMA are Wi-Fi standard violations.** Modern
  clients often mitigate.

## Common tasks

- **Scan APs:** `scanap` from Marauder CLI. Or via Vesper's Marauder
  bridge (see `architecture.md`).
- **Scan clients:** `scansta`.
- **Deauth a target BSSID:** `select -a <BSSID>` then `attack -t deauth`
  (or similar; see `marauder-commands.md` for the exact syntax).
- **Beacon spam a Rickroll list:** `attack -t beacon -l 5` (list-mode).
- **Set up an evil twin:** `attack -t rogueap -s <ssid>`.
- **Capture PMKID:** `sniffpmkid`.
- **Wall of Flippers:** `sniffbt` (or `wof` on newer builds — verify).

## Gotchas

- **The ESP32 is powered from the Flipper's 3V3 rail.** Under Wi-Fi TX
  bursts the rail can sag. Enable OTG (`power otg_on`) if the devboard
  supports 5V input; check its silkscreen.
- **Baud mismatch** — Marauder's default is 115 200 8N1. Some builds
  bump to 921 600. Check the release notes for your version.
- **Third-party carriers with wrong RX/TX orientation** just don't
  boot. See `marauder-wiring.md`.
- **Momentum's built-in "Marauder companion" app** wraps the UART
  protocol but sometimes lags behind Marauder's mainline features. If
  a command works on Marauder's own CLI but not via the Momentum app,
  update the companion app.
- **"Wall of Flippers"** requires nearby Flippers to be *actively
  advertising*. A Flipper with BLE off is invisible.
- **Deauth stops working on WPA3/PMF-required networks** — Protected
  Management Frames block deauth spoofing. This is by design.

## Legal & safety notes

**802.11 deauth is illegal to transmit in most jurisdictions** without
explicit authorization from the network owner. FCC 47 CFR 15.5 (US)
treats it as intentional interference; EU / UK equivalents apply.
Beacon spam and evil-twin APs additionally violate computer-misuse and
telecommunications-fraud statutes in many places. This is not
theoretical — people have been prosecuted. See `legal-and-safety.md`.

## See also

- `marauder-firmware.md` — flashing, releases, CLI protocol.
- `marauder-wiring.md` — physical wiring specifics.
- `marauder-commands.md` — the full command catalog.
- `flipper-gpio-pinout.md` — GPIO pins consumed by the devboard.
- `flipper-gpio-extensions.md` — other add-ons on the same header.
- `firmware-momentum.md` — Momentum's Marauder integration.
- `legal-and-safety.md` — 802.11 attack legal posture.

---
*Attribution:* JustCallMeKoko ESP32Marauder
(<https://github.com/justcallmekoko/ESP32Marauder>), Flipper Wi-Fi
Devboard product page, Marauder wiki. Retrieved 2025-Q3.
