# `.ir` file format

> How IR signal sets are stored on the Flipper's SD. Signal blocks,
> parsed vs raw, per-button structure.

## What it is

An **`.ir` file** stores one or more IR signals — usually all the
buttons of a single remote in one file. Plain text. Lives under
`/ext/infrared/`.

Unlike `.sub` (one signal per file), `.ir` is a *collection*: one file
per remote, one *block* per button.

## How it works

### Header

```
Filetype: IR signals file
Version: 1
```

Simple. `Version: 1` on all firmware families as of 2025-Q3.

### Signal block — parsed

```
#
name: Power
type: parsed
protocol: NECext
address: 04 00 00 00
command: 08 00 00 00
```

- **`#`** — block separator.
- **`name`** — button label, shown in the app.
- **`type: parsed`** — protocol-decoded.
- **`protocol`** — one of the standard names (`NEC`, `NECext`,
  `Samsung32`, `RC5`, `RC6`, `SIRC`, `SIRC15`, `SIRC20`, `Kaseikyo`).
- **`address`** — protocol-specific address bytes, little-endian hex.
- **`command`** — protocol-specific command bytes.

### Signal block — raw

```
#
name: A/C Cool 22C
type: raw
frequency: 38000
duty_cycle: 0.33
data: 3400 1700 400 550 400 1550 400 550 ...
```

- **`type: raw`** — raw waveform.
- **`frequency`** — carrier frequency, Hz.
- **`duty_cycle`** — carrier duty cycle (typically 0.33 for standard
  IR, sometimes 0.50 or 0.25).
- **`data`** — mark/space durations in microseconds. Positive numbers
  are marks (carrier on), negative are spaces. **Note:** `.ir`
  historically uses positive-only durations alternating mark then
  space, unlike `.sub`'s signed convention. Some Momentum builds
  accept the signed variant; assume the alternating positive-only
  convention for maximum compatibility.

### Multiple blocks per file

```
Filetype: IR signals file
Version: 1
#
name: Power
type: parsed
protocol: NEC
address: 04 00 00 00
command: 08 00 00 00
#
name: Vol_Up
type: parsed
protocol: NEC
address: 04 00 00 00
command: 0A 00 00 00
#
name: Custom_AC
type: raw
frequency: 38000
duty_cycle: 0.33
data: 3400 1700 400 550 ...
```

The IR app renders each `name:` as a button in the remote UI.

## Capabilities and limits

- **Mixing parsed and raw** in one file is fine.
- **File size** — the app truncates at some (large) size; A/C
  remotes with long raw blocks are the practical limit.
- **Naming rules** — the `name` field can contain spaces (not
  quoted) but the app UI works best with short (~15-char) names.
- **Protocol coverage** — Flipper's parser knows the standard set
  listed in `ir-overview.md`. Anything else needs `type: raw`.

## Worked examples

### Example — a small TV remote (parsed)

```
Filetype: IR signals file
Version: 1
#
name: Power
type: parsed
protocol: NEC
address: 04 00 00 00
command: 08 00 00 00
#
name: Vol_Up
type: parsed
protocol: NEC
address: 04 00 00 00
command: 0A 00 00 00
#
name: Vol_Down
type: parsed
protocol: NEC
address: 04 00 00 00
command: 0B 00 00 00
```

### Example — an A/C button (raw, 38 kHz)

```
#
name: Cool_22
type: raw
frequency: 38000
duty_cycle: 0.33
data: 3400 1700 400 550 400 1550 400 550 400 1550 400 550 400 1550 400 550 400 1550 400 550 400 1550 400 550 400 1550 400 550 400 550 400 550
```

Shortened for illustration; a real A/C block runs 100–300 tokens long.

## Common tasks

- **Add a button to an existing remote:** open the `.ir` in a text
  editor, add a new `#` block with `name`/`type`/`protocol` or
  `frequency`/`data`, save. The IR app picks it up.
- **Rename a button:** edit `name:`. No app-side rescan needed
  beyond re-entering the remote.
- **Convert parsed → raw:** IR app supports this via UI ("Convert").
  Rarely needed; the reverse (raw → parsed) is only possible if the
  raw matches a known protocol.
- **Merge two remotes' buttons:** concatenate the block sections
  (everything after the header) into one file, ensuring `#` separators
  are correct.

## Gotchas

- **Positive-only `data:` values in raw blocks** — do not use
  negative numbers to mean "space"; `.ir` alternates mark/space
  strictly by position.
- **`duty_cycle` must be a float** — `0.33`, not `0.333`. Some
  parsers reject `0.333333`.
- **Missing frequency in a raw block** — some firmwares default to
  38 kHz; others reject. Always include.
- **Universal remote files** ship with `.ir` extensions but live under
  firmware-managed asset paths (see `flipper-storage.md`). Do not edit
  those directly — they get overwritten on firmware update. Copy to
  `/ext/infrared/` first.
- **CRLF line endings from Windows editors** are tolerated on
  Momentum but rejected on some Official builds.
- **Very long raw blocks** may hang the UI briefly during load. This
  is not a corruption.

## Legal & safety notes

None specific to the file format. Actually pointing an IR blaster at
a target you don't own may still be problematic — see
`ir-overview.md` gotchas and `legal-and-safety.md`.

## See also

- `ir-overview.md` — protocols, hardware, workflow.
- `flipper-storage.md` — `/ext/infrared/` layout.
- `flipper-cli.md` — `ir tx <path>`, `ir rx`.

---
*Attribution:* Flipper-Devices `.ir` format
(<https://docs.flipper.net/file-formats/infrared>). Retrieved
2025-Q3.
