# M0MA-V3SP3R CORPUS — MANIFEST

This directory is the **reference half** of M0MA-V3SP3R — the reservoir
of Flipper Zero / Momentum / Marauder knowledge that the assistant
consults to advise. The **acting half** lives in the Android app
(`app/`): the `execute_command` tool schema, `CommandExecutor`, and
the BLE / CLI pipeline that reaches a real Flipper. The corpus tells
you *what a thing is* and *how it works*; the app *does it*.

The corpus is exposed over MCP by
[`mcp-server/`](../mcp-server/README.md). Every `.md` file below is
reachable via:

- the `list_topics()` / `read_doc(topic, name)` / `search_docs(query)`
  tools,
- the `vesper://<topic>/<name>` resource URI scheme, and
- the `vesper://index` resource — a human-readable TOC.

Add files freely. The server picks them up on next startup — no code
change needed. Topic id = directory name; name = filename stem.

## Topics

### Flipper Zero hardware & platform

- **`flipper-hardware/`** — MCU (STM32WB55), radios, sensors, storage,
  buttons, power. Start here if you're new to the device.
- **`flipper-gpio/`** — the 18-pin expansion header
  ([`pinout`](flipper-gpio/pinout.md)) and what plugs into it
  ([`extensions`](flipper-gpio/extensions.md): Wi-Fi devboard, NRF24,
  VGM, DAP-Link).
- **`flipper-storage/`** — `/int` vs `/ext`, standard tree, file
  formats.
- **`flipper-cli/`** — on-device serial CLI verbs, risk
  stratification.

### Firmware ecosystem

- **`firmware/`** — Official / Momentum / Unleashed / RogueMaster.
  - [`families`](firmware/families.md) — comparison matrix.
  - [`momentum`](firmware/momentum.md) — ⭐ the M0MA priority target.
  - [`compatibility-profile`](firmware/compatibility-profile.md) —
    how Vesper picks a routing profile on connect.
  - [`updating`](firmware/updating.md) — flashing, DFU, brick
    recovery.

### RF subsystems

- **`subghz/`** — CC1101 transceiver.
  - [`README`](subghz/README.md) — hardware, presets, tuning, region.
  - [`protocols`](subghz/protocols.md) — Princeton, CAME, KeeLoq,
    Security+, Somfy, more.
  - [`sub-format`](subghz/sub-format.md) — the `.sub` file format.
- **`ir/`** — infrared TX/RX.
  - [`README`](ir/README.md) — carriers, standard protocols,
    universal remote.
  - [`ir-format`](ir/ir-format.md) — the `.ir` file format.
- **`nfc/`** — 13.56 MHz.
  - [`README`](nfc/README.md) — MIFARE Classic attacks, DESFire
    limits.
  - [`nfc-format`](nfc/nfc-format.md) — the `.nfc` file format.
- **`rfid/`** — 125 kHz LF RFID: EM4100, HID Prox, T5577 blanks.
- **`ibutton/`** — 1-Wire: DS1990A, CYFRAL, Metakom.

### Wi-Fi Marauder devboard (⭐ M0MA priority)

- **`marauder/`**
  - [`README`](marauder/README.md) — what Marauder does; why 802.11
    needs a companion.
  - [`firmware`](marauder/firmware.md) — flashing, releases, UART CLI
    protocol.
  - [`wiring`](marauder/wiring.md) — physical wiring, pink board vs
    third-party.
  - [`commands`](marauder/commands.md) — Scan / Attack / Utility
    catalog.

### Extension & development

- **`development/`**
  - [`fap-apps`](development/fap-apps.md) — `.fap` format, FURI,
    uFBT, install paths.
  - [`firmware-build`](development/firmware-build.md) — `fbt`,
    submodules, Momentum tree layout.
  - [`js-runner`](development/js-runner.md) — Momentum JavaScript
    runtime.

### Methodology playbooks (bundled skills)

- **`skills/`** — the seven skill playbooks the Android
  `SkillRegistry` loads on demand, mirrored here so MCP clients can
  read them. Sync source: `app/src/main/assets/skills/<name>/SKILL.md`.
  Run `python mcp-server/scripts/sync_skills.py` to refresh.

### Legal & safety

- **`legal/`** — practical map of the regulatory posture (FCC, ETSI,
  CFAA-adjacent) per subsystem. Every capability page links here.

### Vesper positioning

- **`vesper/`** — the Android app itself, not the Flipper.
  - [`architecture`](vesper/architecture.md) — data-flow boundary,
    risk tiers.
  - [`campaigns`](vesper/campaigns.md) — Ralph autonomous campaign
    UX.
  - [`labs`](vesper/labs.md) — Alchemy / Payload Lab.
  - [`app-build-process`](vesper/app-build-process.md) — Gradle +
    emulator instructions.
  - [`mcp`](vesper/mcp.md) — how to run this server.

## Conventions

- **One idea per file.** Keep files short and cite sources at the
  bottom.
- **Directory = topic; filename stem = name.** `subghz/protocols.md`
  is topic `subghz`, name `protocols`.
- **Kebab-case, `.md`, no numeric prefixes.** Filenames are stable
  because they're addressable.
- **Cross-link liberally.** Use markdown links across topics —
  `search_docs` is substring-only, so discoverability rides on
  explicit references.
- **Legal & safety notes** on every capability doc. Link to
  [`legal/README`](legal/README.md).
- **Absolute dates.** Any doc that dates a firmware feature or
  protocol change writes the date (`as of 2025-Q3`), not "recently".
- **Cite Flipper-Devices / Momentum / Marauder docs** in an
  "Attribution" footer when a value comes from an external source.

## What this corpus is not

- **Not a `.fap` catalog mirror.** `search_faphub` /
  `install_faphub_app` are execution actions on the Android side.
  The corpus documents the fap *format* and *ecosystem*, not
  individual apps.
- **Not a schema mirror.** `describe_action` (an MCP tool) already
  returns the arg block for every action from
  [`../docs/execute_command_schema.json`](../docs/execute_command_schema.json).
  Do not narrate the JSON schema in Markdown.
- **Not a re-implementation of the Momentum / Marauder wikis.** Cite
  them; don't clone them.
- **Not legal advice.** [`legal/README`](legal/README.md) is
  directional and cites jurisdictions; it does not give legal advice.
