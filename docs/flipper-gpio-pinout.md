# Flipper Zero — GPIO pinout

> The 18-pin expansion header on the top of the Flipper: what each pin
> does, what the common add-on boards consume, voltage tolerances, SWD.

## What it is

An **18-pin, 2.54 mm-pitch, single-row header** on the top edge of the
Flipper Zero, labelled pin 1 (leftmost when looking at the screen) through
pin 18 (rightmost). It exposes selected STM32WB55 GPIOs plus power rails.
It is the primary way to attach an external board — Wi-Fi devboard (see
Marauder docs), NRF24, VGM (Video Game Module), DAP-Link SWD adapter,
custom shields.

## Pin table

Numbering matches Flipper-Devices' silkscreen. Direction is with the LCD
facing you; pin 1 is on the left.

| Pin | Silk  | STM32 name  | Function on Flipper                                         | Notes                                       |
|-----|-------|-------------|-------------------------------------------------------------|---------------------------------------------|
| 1   | PA7   | PA7         | GPIO / SPI1_MOSI / TIM17_CH1                                | 3V3 logic; usable for SPI to add-on.        |
| 2   | PA6   | PA6         | GPIO / SPI1_MISO                                            | 3V3 logic.                                  |
| 3   | PA4   | PA4         | GPIO / SPI1_NSS                                             | 3V3 logic; commonly SPI CS to add-on.       |
| 4   | PB3   | PB3         | GPIO / SPI1_SCK / SWO trace                                 | 3V3 logic.                                  |
| 5   | PB2   | PB2         | GPIO                                                        | 3V3 logic.                                  |
| 6   | PC3   | PC3         | GPIO / ADC1_IN4                                             | 3V3 logic; analog-capable.                  |
| 7   | 1W    | PB14        | 1-Wire bus (iButton)                                        | Shared with on-board iButton connector.     |
| 8   | GND   | —           | Ground                                                      |                                             |
| 9   | 3V3   | —           | 3V3 out (~500 mA max)                                       | Regulated; enabled by firmware.             |
| 10  | SWC   | PA14 (SWCLK)| SWD clock                                                   | Debug; used by DAP-Link.                    |
| 11  | GND   | —           | Ground                                                      |                                             |
| 12  | SIO   | PA13 (SWDIO)| SWD data                                                    | Debug; used by DAP-Link.                    |
| 13  | TX    | PB6         | USART1_TX                                                   | 3V3 UART TX to add-on.                      |
| 14  | RX    | PB7         | USART1_RX                                                   | 3V3 UART RX from add-on.                    |
| 15  | PC1   | PC1         | GPIO / I2C1_SDA                                             | 3V3 logic; I2C data.                        |
| 16  | PC0   | PC0         | GPIO / I2C1_SCL                                             | 3V3 logic; I2C clock.                       |
| 17  | 5V    | —           | 5 V OTG out                                                 | See "OTG behavior" below.                   |
| 18  | GND   | —           | Ground                                                      |                                             |

**All GPIO on this header is 3V3 logic.** Do not drive 5 V logic into any
pin except 5V/OTG.

## Power rails

- **3V3 (pin 9)** is a regulated 3.3 V rail sourced from the Flipper's
  boost/buck power tree. It's rated for around 500 mA sustained; long
  transient sags happen if you actively transmit on the on-board radios
  at the same time. Under-volting an ESP32 devboard on this rail is a
  classic Marauder wiring bug.
- **5V (pin 17)** is USB-OTG-style output. It is only present when the
  Flipper is configured to source it — either off USB pass-through when
  a host is charging, or via the "5V on GPIO" toggle in the on-device
  power menu / `power` CLI. Do not assume 5V is live by default.
- **GND (pins 8, 11, 18)** are the same ground; connect at least one.

## OTG behavior

The 5V rail is disabled at boot. To enable:

- **GUI:** Settings → Power → 5V on GPIO → ON.
- **CLI:** `power otg_on` (`power otg_off` to disable). See
  `flipper-cli.md`.
- **Auto:** some firmwares auto-enable OTG when a specific expansion
  board is detected. Momentum in particular has an add-on manifest
  system (`flipper-fap-apps.md` cross-refs this).

If you attach a Marauder devboard and the ESP32 refuses to boot, the
first thing to check is that OTG is on.

## SWD debug

Pins 10 (SWCLK) and 12 (SWDIO) expose the Cortex-M4 SWD interface. This
is how a **DAP-Link** or similar adapter attaches for JTAG-style debug,
firmware development, or recovery from a soft-brick that DFU can't
handle. Debug requires the "SWD access" toggle in advanced settings
under some firmwares; on Momentum it's on by default.

## Which pins the common add-ons consume

| Add-on              | Pins used                                     | Also needs OTG? |
|---------------------|-----------------------------------------------|-----------------|
| Wi-Fi devboard (Marauder / Blackmagic / stock) | 3V3 (9), GND (8/11/18), TX (13), RX (14), typically pin 7 (BOOT), pin 15/16 (JTAG for the ESP32 sometimes)                  | Yes, for 5V-hungry variants; official rev runs from 3V3. |
| NRF24 module        | 3V3, GND, SPI1 (PA7 MOSI / PA6 MISO / PB3 SCK / PA4 CS), CE + IRQ on remaining GPIO | No             |
| SubGHz VGM external antenna | Uses coaxial off-board; consumes no GPIO in most cases | No             |
| DAP-Link JTAG       | SWC (10), SIO (12), GND (11)                  | No             |
| Video Game Module   | Many pins including SPI + control lines       | Yes            |
| Generic breakout    | Whatever the shield defines                   | Depends        |

**Only one board can occupy the header at a time.** There is no
mechanism for multiplexing; the pinout is single-ended.

## Capabilities and limits

- SPI (pins 1/2/3/4) is one SPI bus (SPI1 on the STM32WB55). Fast enough
  for most sensors and NRF24-style radios; up to ~10 MHz in practice.
- I2C (pins 15/16) is one I2C bus (I2C1). 100 kHz / 400 kHz supported;
  fast-mode-plus untested.
- UART (pins 13/14) is USART1. Common baud rates 115 200 / 921 600.
  Marauder firmware exposes its CLI at 115 200 by default; some builds
  raise it. Confirm before you dump.
- **No 5V-tolerant pins.** All GPIO is strictly 3V3.
- **Timing precision** is fine for bit-banging up to a few Mbps but you
  will lose determinism if the FURI scheduler is busy.

## Common tasks

- **Enable 5V for an add-on:** `power otg_on`, then re-check with
  `power info`.
- **Verify pins from CLI:** `gpio` (list state), `gpio set <pin> 1`,
  `gpio set <pin> 0`. Silkscreen names apply (`pa7`, `pb2`, etc.).
- **Wire a Marauder devboard:** see `marauder-wiring.md` for the exact
  mapping (TX↔RX cross, GND, 3V3, boot pin).
- **Recover a bricked Flipper:** attach DAP-Link on pins 10/11/12, use
  `openocd` or ST's tools to reflash over SWD.

## Gotchas

- **Silkscreen numbering ≠ STM32 pin numbering.** Callers routinely
  confuse "pin 1" (PA7 on the header) with "PA1". This table is
  canonical; don't trust random forum posts.
- **TX and RX cross over.** The Flipper's `TX` (pin 13) drives the
  add-on's `RX`, and vice versa. Marauder wiring failures are almost
  always a swapped pair.
- **3V3 is not always live.** Some firmware paths gate it. If a devboard
  briefly powers up then browns out, suspect a sag on 3V3, not the
  devboard.
- **Do not** drive 5 V into any pin marked as GPIO — you'll damage the
  MCU.
- **SWD SWO** (trace output) is on PB3 (pin 4). If you use SWO for
  debug, that pin can't simultaneously be your SPI clock.

## Legal & safety notes

None specific to the GPIO header — plugging boards in is not itself a
regulated activity. What you *run* on those boards may be; see
`legal-and-safety.md`.

## See also

- `flipper-hardware.md` — the MCU and radios these pins connect to.
- `flipper-gpio-extensions.md` — commonly attached boards, in more depth.
- `marauder-wiring.md` — Wi-Fi devboard wiring specifics.
- `flipper-cli.md` — `gpio` and `power` verbs.

---
*Attribution:* Flipper-Devices GPIO documentation
(<https://docs.flipper.net/gpio-and-modules>), STM32WB55RG reference
manual (RM0434). Retrieved 2025-Q3.
