# Marauder — wiring

> The physical wiring between a Flipper Zero and an ESP32 Marauder
> devboard: TX/RX/GND/3V3 routing, official pink board vs third-party
> carriers, common wiring failures.

## What it is

The Flipper Zero exposes its GPIO header on the top edge (see
`flipper-gpio-pinout.md`). A Marauder-flashed ESP32 devboard plugs
into that header and communicates over UART. The wiring is minimal —
**four wires** (TX, RX, GND, 3V3) plus optionally a boot pin.

The physical form factor of the devboard determines whether you plug
it in directly (drop-on-header PCBs) or wire it up manually (bare
ESP32 modules).

## The four essential connections

Numbers below refer to Flipper GPIO header pins (see
`flipper-gpio-pinout.md`):

| Flipper pin | Flipper name  | ESP32 pin                   | Notes                                      |
|-------------|---------------|------------------------------|--------------------------------------------|
| 13          | **TX** (PB6)  | **ESP32 RX** (usually GPIO3 or similar) | Cross-over. Flipper's TX → ESP32's RX.   |
| 14          | **RX** (PB7)  | **ESP32 TX** (usually GPIO1 or similar) | Cross-over.                              |
| 8 (or 11/18)| **GND**       | **ESP32 GND**                | Common ground; at least one.              |
| 9           | **3V3**       | **ESP32 3V3**                | Power. See "power routing" below.         |

Optional:

- **Flipper pin 7 (1-Wire / PB14) → ESP32 BOOT pin** on some builds
  that route Flipper-side reset/boot. Not required for basic Marauder
  use; used only for auto-flashing rigs.

## Power routing

Two paths to power the ESP32:

### Path A — from the Flipper's 3V3 rail

- Flipper pin 9 (3V3) → ESP32 3V3.
- ESP32 draws from the Flipper battery.
- Simplest wiring; sufficient for scanning + light attacks.
- Under Wi-Fi TX bursts (deauth, beacon spam), current draw can spike
  to 300+ mA, sagging the 3V3 rail. If the Flipper reboots during
  attacks, this is the cause.

### Path B — from Flipper's 5V OTG + on-devboard regulator

- Flipper pin 17 (5V/OTG) → ESP32 devboard's 5V input.
- ESP32 devboard has its own LDO (linear regulator) stepping 5V→3V3.
- Requires `power otg_on` from the Flipper CLI (or auto-OTG on
  Momentum, which recognizes some devboard IDs and enables OTG on
  attach).
- Better under load; still limited by Flipper battery capacity.

**The official pink Wi-Fi Devboard uses Path B.** Third-party carriers
vary — check the silkscreen. Some have a jumper to select 3V3 vs 5V
input.

## Layout — the official Flipper Wi-Fi Devboard ("pink" board)

- Bright pink PCB with an ESP32-WROOM-32 module (some late-2024+ runs
  are ESP32-S2).
- Drops directly onto the Flipper's GPIO header (18-pin socket
  underneath); pin 1 alignment marked on both sides.
- Its own USB-C on the side of the pink board — for flashing the ESP32
  or standalone Marauder use.
- Small BOOT / RESET buttons on the pink board's side.

Wiring is fixed: pin 13 (Flipper TX) → GPIO16 (ESP32 RX); pin 14
(Flipper RX) → GPIO17 (ESP32 TX); 5V from pin 17 → regulator → ESP32
3V3. No user routing decisions.

## Layout — third-party carriers

Vary widely. Common patterns:

- **Bare-ESP32 breakout on a stripboard** with header pins matching
  the Flipper. Same TX/RX/GND/3V3 as the pink board, sometimes with
  a jumper for 3V3-direct vs 5V-through-LDO.
- **Fully-featured competitors** (e.g. the "M5Stack Cardputer variant"
  workflows). Different silkscreen names; verify with a multimeter
  before assuming.
- **Handmade jumper wires** direct from a Flipper header to an ESP32
  Devkit-C. Four wires; not aesthetically great but works.

## Alternate header orientations

Some third-party PCBs are laid out for **either** the standard pin-1-on-left
orientation or a **flipped** orientation to expose different pins as
the header edge. Silkscreen labels indicate. Getting this wrong doesn't
damage anything — the ESP32 just doesn't boot (wrong pin sees 3V3).

## Common wiring failures

Roughly in order of frequency:

1. **TX and RX swapped.** Marauder appears to be running (LEDs on
   ESP32 blink) but no commands elicit a response. Fix: swap 13 and
   14.
2. **No ground connection.** Both TX and RX referenced to floating
   ground → garbled UART, occasional characters through. Fix: solid
   GND from any of Flipper pins 8/11/18.
3. **Undervolt on 3V3.** ESP32 boots but crashes on TX. Enable OTG
   (`power otg_on`) and use Path B.
4. **Missing BOOT pin routing** — if you want auto-flash rigs, some
   builds require the BOOT pin held at the right level during reset.
   Not needed for normal use.
5. **Header pin 1 misalignment** — devboard seated off-by-one. Fix:
   reseat carefully; pin 1 marker on Flipper (usually a small dot on
   the case) → pin 1 on devboard.
6. **Third-party carrier with reversed silkscreen.** ESP32 boots but
   Marauder emits no output; power is on the wrong side. Rotate 180°.

## Capabilities and limits

- **Baud** — Flipper's USART1 is fine up to a few Mbps. Marauder
  defaults to 115 200; some builds go to 921 600. Both sides must
  match.
- **Full-duplex UART** — TX and RX run independently. In practice,
  Marauder's protocol is half-duplex-ish (command in, response out).
- **UART framing** — 8N1 always. Do not attempt hardware flow control;
  neither side wires RTS/CTS on standard rigs.
- **Physical robustness** — the 18-pin header on the Flipper is not
  designed for hundreds of insert/remove cycles. Consider a
  "pass-through" adapter if you swap devboards often.
- **Antenna orientation** — ESP32 PCB antennas are directional.
  Rotating the devboard changes RX gain noticeably.

## Verifying wiring is correct

- **Power:** LED on the ESP32 lights up when the Flipper's 3V3 or OTG
  is on.
- **UART round-trip:** launch Marauder companion app on the Flipper →
  type `help` → get the command list back. If yes, wiring is good.
- **No response:** run a UART bridge from the Flipper (Momentum has
  `uart_bridge`) and send bytes manually; visible garbage suggests
  baud mismatch, no response suggests TX/RX swap or floating GND.
- **Multimeter check:** with everything unpowered, continuity between
  Flipper pin 8 GND and ESP32 GND; and between Flipper pin 9 3V3 and
  ESP32 3V3 (or Path B: pin 17 5V and devboard 5V in).

## Common tasks

- **First-time wiring for a bare ESP32:** solder headers, hand-jumper
  the four wires per the table above, `power otg_on`, launch app.
- **Verify a suspected wiring failure:** power off, unplug, verify
  continuity, reseat.
- **Reflash the ESP32 in-place:** the ESP32 devboard's own USB
  bypasses the Flipper. Just plug USB into the devboard, use the
  Marauder web installer (see `marauder-firmware.md`).
- **Add a boot-hold rig** for auto-flash: wire Flipper pin 7 → ESP32
  BOOT with a level-shifter if needed. Not standard.

## Gotchas

- **Damaging the Flipper GPIO header** by yanking a stuck devboard is
  a common irreversible failure. Rock, don't pull.
- **5V OTG off** silently means devboard doesn't power up. First
  troubleshooting step for any "devboard dead" symptom.
- **Momentum auto-OTG** is convenient but not universal — some
  devboard IDs aren't in Momentum's table. `power otg_on` explicitly
  before relying.
- **UART pin electrical spec** — Flipper is strict 3V3 logic. Some
  ESP32 boards level-shift on-board; some don't. Modern ESP32-WROOM
  is 3V3-native; older ESP32-Devkit modules with USB-serial converters
  might be borderline.
- **The pink board's screws** — early revs used screw terminals.
  Loose screws cause intermittent UART. Tighten firmly.

## Legal & safety notes

Wiring is neutral. What you run on the wired-up Marauder is where the
legal notes on `marauder-overview.md` and `legal-and-safety.md` apply.

## See also

- `flipper-gpio-pinout.md` — the pin table.
- `marauder-overview.md` — what this connection enables.
- `marauder-firmware.md` — flashing the ESP32 independently.
- `flipper-cli.md` — `power otg_on`, `gpio` for verification.
- `flipper-gpio-extensions.md` — other add-ons on the same header.

---
*Attribution:* Flipper Wi-Fi Devboard product docs, JustCallMeKoko
Marauder wiki, community wiring diagrams. Retrieved 2025-Q3.
