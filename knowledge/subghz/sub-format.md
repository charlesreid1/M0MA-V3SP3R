# `.sub` file format

> How SubGHz captures are stored on the Flipper's SD. Header fields,
> `Key` vs `RAW_Data`, timing semantics, gotchas.

## What it is

A **`.sub` file** is the Flipper Zero's savefile for a SubGHz signal.
Plain text. Header + data section, both key/value. Lives under
`/ext/subghz/` (or wherever the SubGHz app puts subfolders).

Two flavors:

- **Decoded** — the Flipper recognised the protocol during capture
  and stored the *bits*, not the timing.
- **Raw** — the Flipper failed to decode (or the user chose Read
  RAW) and stored the timing directly.

## How it works

### Header fields

Common to both flavors:

```
Filetype: Flipper SubGhz Key File
Version: 1
Frequency: 433920000
Preset: FuriHalSubGhzPresetOok650Async
```

- **`Filetype`** — `Flipper SubGhz Key File` or `Flipper SubGhz RAW File`.
- **`Version`** — 1 for both flavors on all firmware families as of
  2025-Q3.
- **`Frequency`** — Hz.
- **`Preset`** — one of the CC1101 configuration names. See
  `subghz-overview.md`. Momentum-specific `FuriHalSubGhzPresetCustom`
  is followed by extra `Custom_preset_module` and
  `Custom_preset_data` lines.

### Decoded body

For a decoded capture, the body carries protocol metadata + bits:

```
Protocol: Princeton
Bit: 24
Key: 00 00 00 00 00 90 A5 C0
TE: 386
Repeat: 5
```

- **`Protocol`** — decoder name.
- **`Bit`** — number of meaningful bits in the key.
- **`Key`** — 64-bit right-aligned representation of the payload,
  hex-space-separated (some firmwares omit the spaces).
- **`TE`** — timing element in microseconds. The basic pulse width the
  protocol was decoded against; matters for TX.
- **`Repeat`** — number of times to retransmit on replay.

Protocol-specific extras appear here too:

- **KeeLoq:** `Manufacture: <name>`, `Serial: <hex>`, `Count: <int>`.
- **Nice FloR-S:** `Serial:`, `Count:`.
- **Somfy Telis:** `Serial:`, `Count:`.

### Raw body

For a raw capture:

```
Protocol: RAW
RAW_Data: 300 -300 400 -400 300 -300 ...
```

- **`Protocol: RAW`** — sentinel.
- **`RAW_Data`** — space-separated integers, microseconds. **Positive =
  mark (TX on), negative = space (TX off).** Concatenated as one long
  waveform. Line-wrapping is allowed; the parser joins across
  `RAW_Data:` lines.

Raw captures can be very long (thousands of samples). Some firmwares
chunk them into multiple `RAW_Data:` lines.

### Timing semantics

- All timings are **microseconds**.
- Consecutive same-sign entries are **not** merged; each represents
  one edge of the CC1101's output.
- The waveform is played back at whatever `Preset` the header
  declares. Play RAW captured under `AM650` back on `AM650` — a
  cross-preset replay silently mangles the output.

## Worked examples

### Example 1 — decoded Princeton

```
Filetype: Flipper SubGhz Key File
Version: 1
Frequency: 433920000
Preset: FuriHalSubGhzPresetOok650Async
Protocol: Princeton
Bit: 24
Key: 00 00 00 00 00 90 A5 C0
TE: 386
Repeat: 5
```

Meaning: 24-bit Princeton payload `0x0090A5C0`, transmit with 386 µs
basic pulse width on 433.92 MHz AM650, repeat 5 times.

### Example 2 — raw capture (garage remote, unknown protocol)

```
Filetype: Flipper SubGhz RAW File
Version: 1
Frequency: 315000000
Preset: FuriHalSubGhzPresetOok650Async
Protocol: RAW
RAW_Data: 393 -397 782 -796 391 -394 780 -797 391 -394 780 -797 391 -394 782 -793 -3900
```

Meaning: 315 MHz OOK, sequence of mark/space pairs approximating a
Manchester code, gap at the end. Replayable but not decodable.

## Capabilities and limits

- **RAW files can be very large.** Long captures should be split.
- **Cross-firmware compatibility:** Official and Momentum both parse
  each other's `.sub` files, *except* Momentum's `PresetCustom` (with
  its `Custom_preset_module`/`Custom_preset_data` lines) — Official
  refuses those with a preset-mismatch error.
- **Editing RAW_Data manually is legitimate** — you can shift, trim,
  concatenate. Text editors work.
- **Editing `Key:` manually** for a decoded capture usually breaks
  it — the field is protocol-specific and the encoder re-derives
  timing from `TE`.
- **`Repeat: 0`** — some decoders treat 0 as "once", others as
  "infinity, until interrupted". Test before relying.

## Common tasks

- **Inspect a saved capture:** `storage read /ext/subghz/mycap.sub`
  (see `flipper-cli.md`), or open in any text editor with the SD in a
  laptop.
- **Replay:** `subghz tx_from_file /ext/subghz/mycap.sub`. Frequency
  and preset come from the header.
- **Concatenate two RAW captures:** copy-paste both `RAW_Data:` line
  contents into one file, join with a space. Ensure a plausible
  inter-frame gap (e.g. `-10000` between them).
- **Convert decoded → raw:** open the file, delete `Protocol:` /
  `Bit:` / `Key:` / `TE:` / `Repeat:`, add `Protocol: RAW` and a
  `RAW_Data:` with the bit pattern rendered as pulses at your chosen
  `TE`. Rarely useful — tooling on Momentum can do it for you.

## Gotchas

- **`Preset: FuriHalSubGhzPresetCustom`** is Momentum-only. See
  `firmware-compatibility-profile.md`; Vesper's parser handles it
  differently per profile.
- **Editing `Frequency:` without changing the physical timing** — if
  you shift a capture from 315 → 433.92 MHz by editing the header,
  playback happens on 433.92 but the *symbol timing* is still what
  the 315 MHz remote used. Whether the target accepts that depends on
  the protocol.
- **Zero-length RAW_Data** is possible (empty capture). Playback is a
  no-op; some firmwares crash on it — check length before replay.
- **Line ending differences:** `.sub` files are LF-terminated by the
  Flipper. CRLF from Windows editors is tolerated on Momentum,
  rejected on some Official builds.
- **`Key: 00 00 00 00 00 90 A5 C0`** is right-aligned in a 64-bit
  field. `Bit: 24` says "the last 24 bits are meaningful." If you see
  `Bit: 24, Key: 00 00 00 00 00 90 A5 C0`, the actual payload is
  `0x90A5C0`, not `0x00000000000090A5C0`.

## Legal & safety notes

Replaying a captured `.sub` against a target you don't own is what the
legal notes on `subghz-overview.md` and `legal-and-safety.md` cover.
The file format itself is not regulated.

## See also

- `subghz-overview.md` — presets, frequencies, region policy.
- `subghz-protocols.md` — what `Protocol:` values mean.
- `flipper-storage.md` — where `.sub` files live on the SD.
- `flipper-cli.md` — `subghz tx_from_file`, `subghz decode_raw`.
- `firmware-compatibility-profile.md` — cross-family parser tolerance.

---
*Attribution:* Flipper-Devices `.sub` format
(<https://docs.flipper.net/file-formats/sub>), Momentum
`applications/main/subghz/` sources. Retrieved 2025-Q3.
