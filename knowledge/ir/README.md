# Infrared — overview

> The Flipper Zero's IR transmit + receive stack: standard protocols,
> the two-diode RX layout, universal-remote workflow.

## What it is

The Flipper Zero includes an infrared transmit LED and two infrared
receive diodes. IR carriers in the **38–56 kHz** range are the target;
these cover essentially all consumer-electronics remotes (TVs, air
conditioners, projectors, sound systems, some IoT accessories).

Unlike SubGHz, IR is line-of-sight, unregulated, and short-range —
typically 5–8 m in open air. There are no "region unlocks" for IR
because there's nothing to regulate.

## How it works

### Hardware

- **TX:** one wide-angle IR LED at the top edge. Modulated by firmware
  at 38, 40, 56 kHz (etc.) carriers, gated by mark/space patterns.
- **RX:** two diodes flanking the TX LED. The two-diode arrangement
  gives ~180° angular coverage — you don't have to point at the target
  from a specific angle to capture.

### Standard protocols

The Flipper decodes and encodes several well-known consumer protocols:

| Protocol       | Carrier   | Framing                                  | Typical devices                    |
|----------------|-----------|------------------------------------------|-------------------------------------|
| **NEC**        | 38 kHz    | 32-bit (address + inverted address + command + inverted command) | Vast majority of Asian-brand TVs.   |
| **NECext**     | 38 kHz    | Extended NEC with 16-bit address.        | Many devices; extended addressing.  |
| **Samsung32**  | 38 kHz    | Samsung-specific 32-bit variant of NEC.  | Samsung TVs and STBs.               |
| **RC5** / **RC6** | 36 kHz | Philips protocols, biphase Manchester.   | Older Philips, Marantz, TiVo.       |
| **Sony SIRC 12/15/20** | 40 kHz | Sony-specific pulse-width encoding.  | Sony TVs, Blu-ray, PS remotes.      |
| **Kaseikyo**   | 36.7 kHz  | 48-bit; also known as "Panasonic".       | Panasonic, Denon, JVC, older Sharp. |
| **Universal remote database** | mixed | Curated `.ir` collection of manufacturer codes for common brands. | Any TV in the database. |
| **Raw / custom** | any    | Arbitrary carrier + mark/space list.     | Air conditioners (very long frames), custom hardware. |

Air conditioner remotes are the classic "raw" case: many manufacturers
use protocols with 100+ bits of state (temperature, mode, fan speed,
timer, sensor state) that don't fit any of the standard framings.
Capture as raw; replay whole.

### Universal remote

The Flipper's Universal Remote menu is a curated `.ir` file catalog
covering common TV, air-conditioner, audio and other device brands. The
catalog ships with firmware (Official is smaller, Momentum's is
substantially larger). It works by cycling common IR codes for a chosen
brand — you press "power" and the Flipper transmits a handful of
candidate codes until the target reacts.

## Capabilities and limits

- **Duty cycle** — IR is unregulated; you can transmit continuously.
  Battery drain is the practical limit.
- **Range** — ~5–8 m for typical consumer devices; less for weak-diode
  targets, more with a dedicated IR blaster.
- **Two-diode RX** — angular coverage is broad but sensitivity varies
  slightly with angle. If capture is spotty, try rotating.
- **Cannot receive Bluetooth-based "IR replacements"** (some 2024+ TVs
  drop IR). Vesper cannot control those.
- **Some remotes use dual-frequency carrier switching** (rare, mostly
  learning-remote systems). Flipper handles single-carrier framing
  well; dual-carrier requires raw capture.

## Common tasks

- **Capture then replay a TV remote:** IR app → Learn New Remote →
  press a button → save. `.ir` file lands in `/ext/infrared/`.
- **Use the universal remote:** IR app → Universal Remotes → TV
  (or A/C, audio, digital sign) → try common brand codes until one
  works.
- **Blast a "power off" to any TV in range:** IR app → Universal → TV →
  Power. Iterates through the database.
- **Replay an A/C remote:** capture as raw (long framing), save,
  replay. IR app handles raw fine; standard protocols get an easier
  UX.
- **Send via CLI:** `ir tx /ext/infrared/tvpower.ir` (see
  `flipper-cli.md`).
- **Reset an IR-controlled projector:** capture manufacturer codes,
  script the sequence.

## Gotchas

- **The "TV kills prank"** — sending Power to every TV brand code
  in a public space is illegal in some jurisdictions (harassment,
  interference with commercial operation). Fun ≠ legal.
- **Some devices react only to *held* codes.** A single-shot IR TX
  might not adjust volume the way the physical remote does. Try
  `Repeat: N`.
- **Air-conditioner captures** are notoriously long — the whole
  state (temp, mode, fan) is retransmitted every button press.
  Save as raw, or the Flipper may misidentify it as NEC-like and
  truncate.
- **NEC vs NECext confusion** — some remotes emit 16-bit addresses
  that the decoder attributes to NECext but Momentum labels as NEC.
  Both decode fine on both firmwares; the label difference is
  cosmetic.
- **RC5 vs RC6** — biphase Manchester with different start-bit
  handling. Confused decoders may report "raw" instead. Manually
  choose the protocol type in the app if you know it.

## Legal & safety notes

IR itself is unregulated. Using an IR blaster to disable displays or
kiosks you don't own may still be a criminal offense in many
jurisdictions. See `legal-and-safety.md`.

## See also

- `signal-formats-ir.md` — the `.ir` file format.
- `flipper-hardware.md` — TX LED + RX diode physical layout.
- `flipper-cli.md` — `ir tx` / `ir rx` verbs.
- `flipper-storage.md` — `/ext/infrared/` layout.

---
*Attribution:* Flipper-Devices Infrared docs
(<https://docs.flipper.net/infrared>), IRremote (Arduino) protocol
references, Momentum universal remote packs. Retrieved 2025-Q3.
