# SubGHz — overview

> The CC1101-based SubGHz radio on the Flipper Zero: what it does, what
> frequencies and modulations it can reach, why some captures decode
> and others don't.

## What it is

The **SubGHz** subsystem is the Flipper's <1 GHz radio. It runs on a
**Texas Instruments CC1101** transceiver connected to the STM32WB55
over SPI, driven by firmware in `applications/main/subghz/`. It's the
subsystem most often reached for: garage-door remotes, key fobs, weather
stations, doorbells, IoT bridges, kill-switches — all live here.

## How it works

### Radio (CC1101)

The CC1101 is a well-known chip: OOK/ASK, FSK (2-FSK, 4-FSK, GFSK),
MSK; on-chip packet handler, sensitivity in the low −100s of dBm on
narrow filters, TX power up to +10 dBm. On the Flipper it's fed via
SPI1 and an internal or PCB-trace antenna.

The chip **physically** covers three windows:

- 300–348 MHz
- 387–464 MHz
- 779–928 MHz

Firmware exposes a *tuning* range that's broader than what any single
region allows to *transmit* — depending on firmware, you can tune from
approximately **280 MHz to 930 MHz**, but the CC1101 will silently mis-behave
outside its official windows.

### Region policy

- **Official firmware:** enforces regional TX tables. In the US you
  can TX on ISM bands (315, 433.92, 915 MHz); in the EU you can TX on
  433.92, 868, 869.5 MHz. Outside → RX-only.
- **Momentum / Unleashed / RogueMaster:** no software enforcement.
  Every frequency the CC1101 can tune, you can *technically* TX on.
  See `firmware-families.md`.

### Presets

A "preset" is a bundled CC1101 register configuration. The Flipper's
built-in presets:

| Preset name        | Modulation | Deviation / bandwidth              | Typical use                              |
|--------------------|------------|------------------------------------|------------------------------------------|
| `AM270`            | OOK/ASK    | 270 kHz RX filter                  | Narrow OOK devices (many 433 MHz remotes).|
| `AM650`            | OOK/ASK    | 650 kHz RX filter                  | Wideband OOK; typical garage remotes.    |
| `FM238`            | 2-FSK      | 2.38 kHz deviation, 60 kHz BW      | Narrowband FSK (some weather stations).  |
| `FM476`            | 2-FSK      | 47.6 kHz deviation, 250 kHz BW     | Wideband FSK.                            |
| `FuriHalSubGhzPresetCustom` | *depends* | User-defined registers           | Advanced captures; not universally decodable. |

The preset a `.sub` file uses is part of its header (see
`signal-formats-sub.md`). Playing back with the wrong preset makes the
signal audible-but-wrong on the receiver.

### TX vs RX sensitivity asymmetry

The Flipper's RX path is tuned to be broadly permissive — it will
capture anything with enough SNR in the passband. The TX path has to
match the target device's expectation for modulation, deviation, and
data rate. A common failure: you capture a remote successfully, but
retransmit with the wrong preset and the target ignores it.

### Antenna

The on-board antenna is a compromise across all three windows. For
serious work — long range, weak signals — an external antenna helps.
Some VGM (Video Game Module) and dedicated SubGHz antenna add-ons
route through the GPIO expansion. See `flipper-gpio-extensions.md`.

## Why some captures RX but don't decode

The most common assistant question. Reasons in rough frequency:

1. **The preset didn't match the modulation.** RX on `AM650` when the
   target is FSK → you'll see energy in the raw waveform but no bits.
   Try `FM238` / `FM476`.
2. **The frequency was slightly off.** 433.87 vs 433.92 MHz is a
   noticeable offset for a narrow receiver. Try adjacent test
   frequencies.
3. **The protocol isn't in the Flipper's decoder table.** The Flipper
   ships decoders for a few dozen protocols (see
   `subghz-protocols.md`). Anything else RX'd goes into RAW_Data,
   which you can replay but the Flipper can't parse.
4. **It's rolling code.** KeeLoq, Security+, etc. capture and replay
   *once*. See `subghz-protocols.md`.
5. **The signal is FHSS/CSS.** LoRa, some baby monitors, some game
   controllers use spread-spectrum. CC1101 can't demodulate those.
6. **The signal is 2.4 GHz.** Different radio subsystem. Flipper Zero
   has no 2.4 GHz protocol receiver on the SubGHz side (BLE is
   separate).

## Capabilities and limits

- **RX with duration** — `subghz rx <freq> <preset> <ms>` streams
  captured packets (decoded or raw) to the CLI. See `flipper-cli.md`.
- **Passive RX with save** — the SubGHz app can save captures to
  `.sub` files in `/ext/subghz/`.
- **TX from file** — `subghz tx_from_file` replays a `.sub`. Preferred
  for anything more complex than a single-bit toggle.
- **TX from hex** — `subghz tx <hex> <freq> <preset>` transmits a raw
  hex payload. Region-locked on stock; unlocked on forks.
- **Simultaneous TX and RX: no.** The CC1101 is half-duplex.
- **Bandwidth limits:** protocols wider than the CC1101's ~1 MHz
  effective bandwidth (LoRa, CSS baby monitors) are unrecoverable.
- **Duty-cycle limits:** ISM regulations often cap TX duty cycle
  (~1% on 868 MHz in the EU). The Flipper does not enforce these.

## Common tasks

- **Scan for a remote's frequency:** SubGHz app → Frequency Analyzer.
  Hold the remote near the antenna and press the button.
- **Capture a decoded remote:** SubGHz → Read → press remote → save
  as `.sub`. Flipper picks a protocol from its decoder table.
- **Capture a raw remote:** SubGHz → Read RAW → press remote → save.
  Produces a `.sub` with `RAW_Data`. Can be replayed but not
  meaningfully edited.
- **Replay a saved capture:** SubGHz → Saved → pick file → TX. Or from
  CLI: `subghz tx_from_file /ext/subghz/mycap.sub`.
- **Brute-force a fixed-code remote:** SubBrute app (Momentum
  built-in). Only useful for fixed protocols like Princeton — see
  `subghz-protocols.md`.

## Gotchas

- **Frequency Analyzer needs strong signal proximity** — a remote 2 m
  away won't register. Get within 10-30 cm.
- **`Preset=Custom` in `.sub` files** is Momentum-specific and won't
  parse on Official. Fingerprint the firmware first — see
  `firmware-compatibility-profile.md`.
- **Some `.sub` files claim `Frequency: 433920000` but were captured
  at 433870000.** Trust the *waveform*, not the header, if replay
  fails.
- **`subghz tx <hex>` with no preset defaults to `AM650`** on stock
  firmware and to whatever-was-last on Momentum. Always specify.
- **Long RAW captures fill the SD.** A 60 s wideband capture can be
  megabytes; batch captures to short windows.
- **RX buffer size** — the Flipper's SubGHz app truncates raw captures
  at ~512 KB. Long-running packet trains get chopped.

## Legal & safety notes

**TX below 300 MHz, or on non-ISM bands within the CC1101's range, is
illegal without a license in most jurisdictions.** Even ISM-band TX has
duty-cycle and power caps. See `legal-and-safety.md`.

## See also

- `subghz-protocols.md` — protocol-by-protocol reference.
- `signal-formats-sub.md` — the `.sub` file format.
- `firmware-families.md` — region policy differences.
- `flipper-cli.md` — `subghz` CLI verbs.
- `flipper-gpio-extensions.md` — external antennas.
- `skill-signal-analysis` (added in slice 6.6) — methodology.

---
*Attribution:* TI CC1101 datasheet (SWRS061I), Flipper-Devices SubGHz
docs (<https://docs.flipper.net/sub-ghz>), Momentum SubGHz app source.
Retrieved 2025-Q3.
