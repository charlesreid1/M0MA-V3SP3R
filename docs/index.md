# Corpus index

> Table of contents for the M0MA-V3SP3R MCP knowledge corpus. Every
> topic id here is a valid argument to `read_doc`. Groups follow the
> layout in `plan-knowledge-expand.md`.

## Group A — Flipper Zero hardware & platform

- `flipper-hardware` — MCU, radios, sensors, storage, buttons, power.
- `flipper-gpio-pinout` — the 18-pin expansion header, pin by pin.
- `flipper-storage` — `/int` vs `/ext`, standard tree, file formats.
- `flipper-cli` — on-device serial CLI verbs; risk stratification.

## Group B — Firmware ecosystem

- `firmware-families` — Official / Momentum / Unleashed / RogueMaster.
- `firmware-momentum` — ⭐ M0MA priority; the primary firmware target.
- `firmware-compatibility-profile` — how Vesper picks a routing profile on connect.
- `firmware-updating` — flashing, DFU, brick recovery.

## Group C — RF subsystems

**SubGHz:**

- `subghz-overview` — CC1101, presets, tuning, region policy.
- `subghz-protocols` — Princeton, CAME, KeeLoq, Security+, Somfy, more.
- `signal-formats-sub` — the `.sub` file format.

**Infrared:**

- `ir-overview` — TX/RX hardware, standard protocols, universal remote.
- `signal-formats-ir` — the `.ir` file format.

**NFC:**

- `nfc-overview` — 13.56 MHz, MIFARE Classic attacks, DESFire limits.
- `signal-formats-nfc` — the `.nfc` file format.

**LF RFID / iButton / GPIO extensions:**

- `rfid-lf-overview` — 125 kHz, T5577 blanks, HID Prox, EM4100.
- `ibutton-overview` — 1-Wire, DS1990A, CYFRAL, Metakom.
- `flipper-gpio-extensions` — Wi-Fi devboard, NRF24, VGM, DAP-Link.

## Group D — WiFi Marauder header

- `marauder-overview` — ⭐ what Marauder does; why 802.11 needs a companion.
- `marauder-firmware` — flashing, releases, UART CLI protocol.
- `marauder-wiring` — physical wiring, official pink board vs third-party.
- `marauder-commands` — Scan / Attack / Utility catalog.

## Group E — Extension & development

*(populated by slice 6.5)*

## Group F — Signal analysis methodology (bundled skills)

*(populated by slice 6.6)*

## Group G — Safety, legal, RF regulations

*(populated by slice 6.5)*

## Group H — Vesper positioning

- `architecture` — Vesper's cloud/phone/Flipper data-flow boundary.
- `campaigns` — Ralph autonomous campaign UX.
- `labs` — Alchemy / Payload Lab UX.
- `app-build-process` — Gradle build for the Android app.
- `mcp` — how to run this MCP server.
- `readme` — top-level project overview.

---
Cross-linking is dense on purpose. `search_docs` is substring-only, so
if you're not sure what to read next, follow the `See also:` block at
the bottom of every doc.
