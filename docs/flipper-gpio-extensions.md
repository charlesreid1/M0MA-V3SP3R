# Flipper Zero — GPIO extensions

> Common add-on boards attached to the 18-pin GPIO header: Wi-Fi
> devboard, NRF24, external antennas, VGM, DAP-Link. Which pins they
> consume, and single-board-at-a-time rule.

## What it is

The Flipper Zero's 18-pin GPIO header (see `flipper-gpio-pinout.md`)
takes exactly *one* physical add-on at a time. The ecosystem of add-ons
falls into four rough buckets: Wi-Fi companions, sub-GHz co-processors,
debug interfaces, and general-purpose expansion boards.

This doc surveys the common ones and points at their dedicated docs
where they exist.

## How it works — add-on families

### Wi-Fi devboard (⭐ M0MA priority)

Adds a **Cortex-M4-class MCU with 802.11 radio**, wired to the Flipper
over UART. The Flipper Zero has no on-board Wi-Fi; this is how it
gets 802.11 capabilities.

Two main variants in the wild:

- **Official Flipper Wi-Fi Devboard** (screaming pink, screw-terminals
  on some revs, ESP32-WROOM-32 module). Sold by Flipper-Devices. Ships
  with Blackmagic firmware by default; users typically reflash to
  Marauder.
- **Third-party carriers** — bare ESP32 devboards on custom PCBs,
  often ESP32-S2 or ESP32-WROOM-32E. Same wiring, same firmware
  options.

Firmware options for the ESP32:

- **Blackmagic** — SWD-over-Wi-Fi debug adapter (unrelated to attack
  workflows).
- **Marauder** — the JustCallMeKoko Wi-Fi attack framework. This is the
  M0MA target.
- **ESP-IDF custom** — you can build your own.

See the Marauder docs for depth: `marauder-overview.md`,
`marauder-firmware.md`, `marauder-wiring.md`, `marauder-commands.md`.

### NRF24 module

A **Nordic Semiconductor nRF24L01+** 2.4 GHz radio on a small SPI
carrier board. Not to be confused with the Flipper's on-die BLE — the
NRF24 is a separate radio speaking Nordic's Shockburst protocol,
useful for logitech / rf24 keyboards, generic 2.4 GHz IoT devices, and
"mouse jackers."

- **Pins consumed:** 3V3 (9), GND (8/11/18), SPI1 (1/2/3/4), plus CE
  and IRQ on remaining GPIO.
- **Firmware support:** Momentum, Unleashed, and RogueMaster ship
  dedicated NRF24 apps (sniffer, jammer, Mousejack).
- **OTG:** not required — NRF24 runs off 3V3.

### VGM external antenna

The **Video Game Module** ships with an SMA connector for an external
SubGHz antenna. Aftermarket "VGM antenna" boards do the same in
standalone form:

- **Pins consumed:** typically none (the antenna is a passive RF
  extension off the CC1101 trace via a coaxial jump).
- **Firmware support:** any firmware; it's a hardware-only
  modification.
- **Use case:** dramatically improved SubGHz RX/TX range and
  sensitivity, at the cost of the built-in antenna.

Note: attaching an external antenna requires opening the case in some
form; official Flipper support is best-effort. See
`subghz-overview.md`.

### DAP-Link JTAG adapter

A small ARM CMSIS-DAP debug probe wired to the SWD pins (10/11/12).
Used for:

- **Recovering hard-bricked Flippers** (see `firmware-updating.md`).
- **Developing firmware from source** — attach a DAP-Link, run
  `openocd`, single-step FURI code.
- **Reflashing over SWD** when DFU isn't reachable.

Pins consumed: SWC (10), SWDIO (12), GND (11). Nothing else.

### Video Game Module (VGM)

Third-party expansion board from Flipper-Devices containing an
**STM32H7** MCU, extra SubGHz antenna connector, and a small OLED. Not
strictly a "Flipper add-on" in the same sense — the VGM runs its own
firmware and uses the Flipper as a controller. Wide pin consumption
(SPI + several control lines).

Firmware for the VGM is separate from Flipper firmware; see the
Flipper-Devices VGM documentation.

### GPIO breakout / generic

Bare pin-header breakouts with jumpers, LED arrays, or breadboard
adapters. Used for prototyping — no fixed pinout, whatever you wire.

## The single-board-at-a-time rule

**Only one add-on can occupy the 18-pin header at a time.** There is
no multiplexer, no daisy-chain support, no "shield stack." If you need
Wi-Fi + NRF24 simultaneously, you're out of luck without custom
hardware.

Consequences:

- Marauder + NRF24 workflows are sequential: attack Wi-Fi, unplug,
  attack NRF24.
- Debug (DAP-Link) blocks all other add-ons. Reflashing an ESP32 via
  the Wi-Fi devboard while the DAP-Link is attached is not possible;
  the ESP32 is unpowered.
- Some third-party PCBs try to combine (e.g. a carrier with an ESP32
  + NRF24 + LoRa on one board sharing GPIO through a mux). These are
  rare and firmware-specific.

## Capabilities and limits

- **Header power:** 3V3 rail ~500 mA. Powering a Wi-Fi devboard mid-TX
  can sag the rail; some carriers add a large decoupling cap for this
  reason.
- **OTG (5V rail) is off by default.** Wi-Fi devboards that need 5V
  require `power otg_on` (see `flipper-cli.md`). Momentum
  auto-enables OTG for known devboard IDs.
- **UART:** exactly one bus. Wi-Fi devboards, VGM, and other
  UART-first accessories collide.
- **SPI:** one bus (SPI1). NRF24, some breakout boards use it.
- **I2C:** one bus (I2C1). Rarely used by Flipper add-ons; more
  common in DIY breakouts (I2C displays, sensors).

## Common tasks

- **Attach a Wi-Fi devboard:** align the header, seat it, `power otg_on`
  if needed, verify UART traffic by launching the Marauder app.
- **Swap Wi-Fi for NRF24:** power off the Flipper (`power off`),
  swap boards, power on, launch NRF24 app.
- **Attach DAP-Link for recovery:** power off, seat the DAP-Link,
  connect to a laptop, `openocd` with the appropriate cfg for STM32WB55.
- **Identify unknown add-on:** check its silkscreen labels against
  `flipper-gpio-pinout.md`. TX/RX + power = Wi-Fi devboard variant;
  8-pin SPI header = NRF24; SMA jack = external antenna.

## Gotchas

- **Wi-Fi devboard reversed** — some third-party carriers are
  reversible; the ESP32 boots but Marauder emits no output. Rotate 180°.
- **Two power sources** — some devboards have their own USB port for
  standalone Marauder. If you plug both the Flipper's OTG and USB
  power in, you can create ground loops or backfeed. Use one.
- **3V3 sag under Wi-Fi TX** — deauth attacks can pull 300+ mA in
  bursts. If the Flipper reboots mid-attack, this is likely.
- **NRF24 CE line varies by app** — Momentum, RogueMaster, and
  community-fork apps sometimes assign CE to different GPIO. Check
  the current app's config before assuming.
- **DAP-Link + firmware update via qFlipper** conflict — qFlipper drives
  the USB DFU path; DAP-Link is the SWD path. Don't run both.

## Legal & safety notes

The add-on hardware is unregulated on its own. What the add-on lets
you *do* is where legality matters — Marauder's 802.11 attacks in
particular are illegal in most jurisdictions without authorization. See
`legal-and-safety.md` and `marauder-overview.md`.

## See also

- `flipper-gpio-pinout.md` — the pin table.
- `marauder-overview.md` — the Wi-Fi devboard's primary use case.
- `marauder-wiring.md` — Wi-Fi devboard wiring specifics.
- `subghz-overview.md` — external SubGHz antenna context.
- `firmware-updating.md` — DAP-Link for brick recovery.
- `flipper-cli.md` — `power otg_on`, `gpio` verbs.

---
*Attribution:* Flipper-Devices GPIO / modules docs
(<https://docs.flipper.net/gpio-and-modules>), Flipper Wi-Fi Devboard
product page, JustCallMeKoko Marauder wiki, Nordic nRF24L01+ datasheet.
Retrieved 2025-Q3.
