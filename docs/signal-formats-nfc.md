# `.nfc` file format

> How NFC captures are stored on the Flipper's SD. Tag-type layouts,
> ATQA/SAK/UID/block fields, key encoding.

## What it is

A **`.nfc` file** stores a captured or hand-authored NFC tag: metadata
(UID, ATQA, SAK, protocol), plus block data (for MIFARE), plus per-sector
keys. Plain text. Lives under `/ext/nfc/`.

Format specifics vary slightly by tag type. Three broad layouts matter:

- **ISO14443-3A** (UID-only, no MIFARE Classic).
- **MIFARE Classic** (sectors, keys, per-block data).
- **MIFARE Ultralight / NTAG21x** (pages).

## How it works

### Common header

```
Filetype: Flipper NFC device
Version: 4
```

- **`Version:`** varies by firmware. Version 3 is the historical stable
  format; version 4 adds richer metadata (Momentum uses 4 by default
  as of 2025-Q3). Both are read by all firmwares.

### Device / tag identification

```
Device type: Mifare Classic
UID: A1 B2 C3 D4
ATQA: 00 04
SAK: 08
```

- **`Device type`** — one of `NTAG213`, `NTAG215`, `NTAG216`,
  `Mifare Classic`, `Mifare Ultralight`, `EMV`, `Slix`, etc.
- **`UID`** — space-separated hex bytes.
- **`ATQA`** — 2-byte "Answer To Request" (little-endian on some
  parsers; check byte order if writing manually).
- **`SAK`** — 1-byte "Select Acknowledge".

### MIFARE Classic body

```
Mifare Classic type: 1K
Data format version: 2
# Mifare Classic blocks, byte 0-15 hex, per block, sector by sector.
Block 0: A1 B2 C3 D4 08 04 00 62 63 64 65 66 67 68 69 6A
Block 1: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 2: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 3: FF FF FF FF FF FF FF 07 80 69 FF FF FF FF FF FF
# ... continue through all sectors
Key A sector 0: FF FF FF FF FF FF
Key B sector 0: FF FF FF FF FF FF
Key A sector 1: A0 A1 A2 A3 A4 A5
Key B sector 1: B0 B1 B2 B3 B4 B5
# ...
```

- **`Mifare Classic type`** — `Mini` / `1K` / `4K`.
- **`Block N: ...`** — 16-byte block content, hex-space-separated.
  Blocks 0–3 are sector 0, 4–7 are sector 1, etc. Sector trailer
  blocks (3, 7, 11, ...) hold Key A + access bits + Key B.
- **`Key A sector N` / `Key B sector N`** — the sector keys, 6 bytes
  each. Redundant with the sector-trailer block, but the Flipper
  stores them separately for convenience.

### MIFARE Ultralight / NTAG body

```
UL version: EV1_MF0UL21
Signature: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Mifare version: 00 04 03 01 01 00 0F 03
Counter 0: 000000
Tearing 0: BD
Pages total: 41
Pages read: 41
Page 0: A1 B2 C3 D4
Page 1: E5 F6 07 18
# ... one page per line, 4 bytes each
```

- **`Pages total`** — declared page count for the tag variant.
- **`Pages read`** — how many were successfully read. Locked pages
  come back as `?? ?? ?? ??` on some firmwares.
- **`Page N`** — 4-byte page.
- Metadata (`Signature`, `Counter`, `Tearing`) are Ultralight-EV1
  extensions.

### ISO14443A UID-only body

For non-MIFARE ISO14443A tags where the Flipper only got UID/ATQA/SAK:

```
Filetype: Flipper NFC device
Version: 4
Device type: ISO14443-3A
UID: A1 B2 C3 D4
ATQA: 00 04
SAK: 08
```

No block data. Emulation replays UID + ATQA/SAK responses.

## Capabilities and limits

- **File size** — 1K MIFARE dump is ~5 KB; 4K is ~15 KB. Comfortable.
- **Hand-authoring** works — you can craft a `.nfc` file in a text
  editor to emulate a specific UID + protocol. Useful for testing.
- **UID length** — 4-byte UIDs (single-size), 7-byte UIDs (double-size),
  10-byte UIDs (triple-size) all supported. Length is inferred from
  the `UID:` byte count.
- **Version drift** — Version 3 files parse on all firmwares. Version 4
  requires 2024+ firmware. Don't emit Version 4 if targeting old
  Official.
- **DESFire dumps** capture UID, ATQA, SAK, ATS. No file-selection
  content beyond that (auth required).
- **`Data format version:`** on MIFARE Classic bodies — bumped when
  the block layout changes. Version 2 is current.

## Worked example — MIFARE Classic 1K dump

```
Filetype: Flipper NFC device
Version: 4
Device type: Mifare Classic
UID: A1 B2 C3 D4
ATQA: 00 04
SAK: 08
Mifare Classic type: 1K
Data format version: 2
Block 0: A1 B2 C3 D4 08 04 00 62 63 64 65 66 67 68 69 6A
Block 1: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 2: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 3: FF FF FF FF FF FF FF 07 80 69 FF FF FF FF FF FF
Block 4: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 5: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 6: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Block 7: A0 A1 A2 A3 A4 A5 FF 07 80 69 B0 B1 B2 B3 B4 B5
# ... blocks 8-63 continue similarly
Key A sector 0: FF FF FF FF FF FF
Key B sector 0: FF FF FF FF FF FF
Key A sector 1: A0 A1 A2 A3 A4 A5
Key B sector 1: B0 B1 B2 B3 B4 B5
# ...
```

## Common tasks

- **Inspect a `.nfc` file:** `storage read /ext/nfc/mycard.nfc`
  (see `flipper-cli.md`), or open in any text editor.
- **Emulate:** `nfc emulate /ext/nfc/mycard.nfc` from CLI, or
  Emulate button in the app.
- **Hand-author a UID emulator:** create a minimal `.nfc` with only
  header + `UID:` / `ATQA:` / `SAK:`. Great for testing what a reader
  accepts.
- **Extract keys from a saved dump:** the `Key A sector N` /
  `Key B sector N` lines *are* the keys. Add them to
  `mf_classic_dict_user.nfc` for future dictionary attacks.
- **Convert between v3 and v4:** manually — v4-specific fields
  (extended metadata) can be dropped to downgrade; v3 can be trivially
  upgraded by bumping the header. Rarely necessary.

## Gotchas

- **`SAK: 08` = MIFARE Classic 1K.** `SAK: 18` = 4K. `SAK: 00` =
  MIFARE Ultralight. `SAK: 20` = ISO14443-4 (DESFire etc.). Getting
  this wrong makes emulation fail with confusing "reader rejected"
  behavior.
- **Sector-trailer keys are canonical.** If the sector trailer block
  and the separate `Key A/B sector N` lines disagree, most firmware
  trusts the separate keys — but this is undefined behavior. Keep
  them in sync.
- **UID uniqueness bit** — for 7-byte UIDs on MIFARE Ultralight,
  byte 0 must be `04` (NXP manufacturer prefix). Emulating with
  another prefix produces cards some readers accept but others
  reject.
- **`Data format version: 3` (older)** doesn't include separate `Key A
  sector N` lines — keys are in the trailer blocks only. Newer parsers
  handle both.
- **Magic-card writes require the `.nfc` file's byte 0 of Block 0** to
  equal the desired UID. Some Gen2 magic cards lock this after first
  write.

## Legal & safety notes

Cloning access-control credentials without authorization is illegal
in most jurisdictions. The file format itself is neutral; what you
capture into it is where the tripwires live. See
`legal-and-safety.md`.

## See also

- `nfc-overview.md` — protocols, hardware, attacks.
- `flipper-storage.md` — `/ext/nfc/`, `/ext/nfc/assets/` layout.
- `flipper-cli.md` — `nfc emulate`, `nfc detect`.

---
*Attribution:* Flipper-Devices NFC file format
(<https://docs.flipper.net/file-formats/nfc>), Momentum
`applications/main/nfc/` sources. Retrieved 2025-Q3.
