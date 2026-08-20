# Flipper Zero — hardware

> The physical device: MCU, radios, sensors, storage, power. Read this
> first if you don't yet know what a Flipper Zero is.

## What it is

The Flipper Zero is a handheld multi-tool for wireless research. It packs a
dual-core MCU, four independent radios (SubGHz, NFC, IR, 1-Wire), a 128×64
monochrome LCD, five hardware buttons + a directional pad, a battery,
microSD storage, USB-C, and an 18-pin GPIO expansion header. It ships in a
distinctive orange enclosure and boots into a shell called `flipper` on
top of a real-time OS (FURI, built on FreeRTOS).

The board revision most callers care about is the mass-production **Q4**
run and later (mid-2022 onward). Earlier engineering-sample revs are out
of scope here.

## How it works

### MCU — STM32WB55RG

The main SoC is an **STM32WB55RG** (WLCSP or LQFN package depending on
run):

- **Application core:** Cortex-M4F @ 64 MHz, 1 MB flash, 256 KB SRAM.
  Runs FURI, the GUI, all app logic, filesystem, USB, etc.
- **Network / radio co-processor:** Cortex-M0+ dedicated to the on-die
  2.4 GHz radio. Executes ST's proprietary "STM32WB Wireless Coprocessor"
  binary — **BLE 5.x only** on Flipper Zero. There is *no* Wi-Fi radio on
  the STM32WB55; the Cortex-M0+ can also run 802.15.4 / Zigbee firmware
  on the chip in principle, but Flipper Zero ships BLE.
- Inter-core comms via IPCC + shared SRAM2.

### On-board radios and I/O

| Subsystem   | Chip                                       | Notes                                                        |
|-------------|--------------------------------------------|--------------------------------------------------------------|
| SubGHz      | **TI CC1101**                              | 300–348, 387–464, 779–928 MHz windows; OOK/ASK/FSK/MSK; RX+TX. |
| NFC (HF)    | **ST25R3916** (some late runs: successor)  | 13.56 MHz reader / card / peer.                              |
| IR TX/RX    | Discrete LED + two receivers (38–56 kHz)   | Wide-angle TX; two RX diodes for angular coverage.           |
| 1-Wire      | Direct GPIO with level control             | Dallas iButton read/write/emulate.                           |
| 125 kHz RFID| On-board LF antenna + discrete analog frontend | EM4100/HID/T5577 read + write + emulate.                 |
| BLE         | STM32WB55 on-die radio                     | Peripheral + Central; used by Vesper's transport.            |
| USB         | USB 2.0 Full-Speed, USB-C                  | Serves CLI, mass-storage-like flows via qFlipper, DFU.       |

The two IR receivers give ~180° coverage; the single wide-lens TX LED is
angled forward. The LF antenna is a coil laid into the enclosure; the HF
antenna is a printed loop on the PCB.

### Storage

- **Internal flash (1 MB on the M4 core):** firmware + tightly-coupled
  data.
- **microSD:** the "external" filesystem — everything user-visible
  (`/ext/subghz`, `/ext/nfc`, `/ext/apps`, etc.). Formatted FAT32.
  Recommended size ≥ 8 GB; card class doesn't matter much.
- **SPI Flash (external NOR, small):** used by ST's stack for the
  BLE binary and radio configuration; not user-writable.

See `flipper-storage.md` for the SD layout.

### Buttons, LCD, LED, vibro

- Five physical buttons: up/down/left/right/OK; a dedicated **BACK** on
  the right edge.
- 128×64 monochrome reflective LCD (Sharp Memory-LCD family). Extremely
  low idle power; readable in direct sunlight.
- RGB LED under the "dolphin" area, driven from the M4 core.
- Small vibration motor for haptic feedback.

### Power

- **2 000 mAh Li-Po** internal battery.
- Charging over USB-C @ 5 V, ~500 mA typical.
- The M4 aggressively deep-sleeps; a fully charged Flipper idles for
  a week or more of on-standby time. Actively transmitting on SubGHz +
  keeping BLE up + backlight on drops runtime to a few hours.
- The GPIO header can source 3V3 (~500 mA) or 5V from a USB OTG path — see
  `flipper-gpio-pinout.md`.

## Capabilities and limits

Capabilities: SubGHz RX+TX with common protocol decoders, NFC/RFID read
and emulate, IR record+replay+universal remote, 1-Wire (iButton) capture
and emulate, BadUSB over USB HID when tethered to a target computer,
in-app tools (RNG, U2F, remote-key learning), GPIO for external boards.

Limits worth naming up front:

- **No Wi-Fi radio on-board.** 802.11 needs an ESP32 companion on the
  GPIO header — see `flipper-gpio-extensions.md` and the Marauder docs.
- **No cellular, no LoRa on-board.** Some third-party GPIO boards add
  these; the base Flipper cannot.
- **Region-locked SubGHz TX** on official firmware. Custom firmware
  (Momentum / Unleashed / RogueMaster) unlocks additional windows; see
  `firmware-families.md`.
- **RX-only for some HF standards.** 13.56 MHz FeliCa and ISO15693 have
  partial support depending on firmware.

## Common tasks

- **Update firmware:** put a `.tgz` on the SD under `/ext/update/`, boot
  into the on-device Updater; or use qFlipper over USB.
- **Add an SD card:** eject the microSD from the slot on the top edge,
  format FAT32, drop it back in. First boot recreates the standard tree.
- **Read basic info:** `device_info` over the CLI (`flipper-cli.md`)
  gives hardware revision, firmware family, unique ID, battery %.
- **Hard reset:** hold BACK for ~10 s to force a reboot without needing
  to disassemble anything.
- **Enter DFU:** hold LEFT + BACK during power-on; the M4 comes up in
  ST's system bootloader for USB DFU recovery.

## Gotchas

- The **CC1101 has a hard limit** at 928 MHz and can *tune* down to
  ~280 MHz, but TX below 300 MHz or above 348/464/928 MHz is
  extra-legal in most jurisdictions and disabled on stock firmware.
- The **NFC antenna is small**; expect a few centimeters of range at
  best. Larger targets (transit gates, industrial readers) may not
  couple at all — this is not a Flipper defect.
- The **LF RFID antenna is directional**; hold the back of the Flipper
  flat against the reader/card, not the screen side.
- The **plastic case** is not RF-transparent everywhere; the SubGHz
  antenna trace is deliberately positioned. Removing shielding or the
  case is unsupported.
- **microSD wear** is a real thing under heavy `subghz` capture
  workloads. Rotate cards annually if you're capturing continuously.
- STM32WB55 flash has ~10 000-cycle endurance; the firmware wear-levels,
  but flashing 100+ times a day (uncommon) will eventually reach it.

## Legal & safety notes

Owning a Flipper Zero is legal in most countries. Transmitting on
SubGHz, cloning access-control credentials, and running BadUSB payloads
against systems you don't own is not. See `legal-and-safety.md`.

## See also

- `flipper-gpio-pinout.md` — the 18-pin expansion header, pin by pin.
- `flipper-storage.md` — SD card layout.
- `flipper-cli.md` — the on-device serial CLI.
- `firmware-families.md` — Official vs Momentum vs Unleashed vs RogueMaster.
- `subghz-overview.md` — deeper on the CC1101 + tuning.

---
*Attribution:* Flipper-Devices hardware documentation
(<https://docs.flipper.net/>), ST STM32WB55 datasheet (RM0434), TI
CC1101 datasheet (SWRS061I), ST25R3916 product page. Retrieved
2025-Q3.
