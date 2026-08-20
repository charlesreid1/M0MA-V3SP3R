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

*(populated by slice 6.2)*

## Group C — RF subsystems

*(populated by slice 6.3)*

## Group D — WiFi Marauder header

*(populated by slice 6.4)*

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
