# iButton (1-Wire) — overview

> The Flipper Zero's 1-Wire subsystem: Dallas DS1990A keys, CYFRAL,
> Metakom, intercom/access-control contexts.

## What it is

**iButton** is a family of contact-based access tokens built around
Dallas Semiconductor's **1-Wire** protocol. Physically, an iButton is a
small metal disc pressed against a matching reader. The reader powers
the token via a single data pin (plus ground); the token responds with
its serial number.

iButtons and their variants dominate residential intercom systems and
some industrial access control. They predate proximity RFID, are cheap,
and still ubiquitous in Eastern European apartment building intercoms.

The Flipper reads them via the **1-Wire pin (pin 7 on the GPIO header,
also mirrored to the on-board iButton contact)** — see
`flipper-gpio-pinout.md`. It can read, save, emulate, and write
compatible blanks.

## How it works

### Protocol variants

Three families the Flipper handles:

| Family              | ROM     | Encoding                          | Typical use                                       |
|---------------------|---------|-----------------------------------|---------------------------------------------------|
| **Dallas DS1990A**  | 64-bit  | Standard 1-Wire ROM (family + serial + CRC) | The canonical iButton. Serial number only.        |
| **CYFRAL**          | proprietary | Custom framing, not true 1-Wire | Eastern European intercoms; short serials.        |
| **Metakom**         | proprietary | Custom framing                 | Eastern European intercoms; different framing.    |

DS1990A is a **read-only ROM chip**: 8-bit family code (usually `0x01`),
48-bit serial, 8-bit CRC. Reading is trivial; every DS1990A shows the
same 64-bit code every time it's touched to a reader.

CYFRAL and Metakom are not strict 1-Wire — they use similar
single-wire signaling but incompatible framing. Flipper handles both;
their savefiles look similar to DS1990A savefiles with a different
`Key type` header.

### Reading

Physically touching a DS1990A to the reader (Flipper's contact) energizes
the chip via the data pin; it clocks out its 64 bits. Total exchange
takes ~100 ms.

### Emulating

The Flipper drives the data pin to mimic a target token's response.
Any 1-Wire-compatible reader sees the emulated key as if it were the
original disc.

### Writing

Some blanks (RW1990, TM2004) are writable DS1990A clones. The Flipper's
`ibutton write` verb (see `flipper-cli.md`) writes a saved key onto a
blank. Once written, the blank behaves like the original.

## File format

Similar to `.rfid`:

```
Filetype: Flipper iButton key
Version: 2
Key type: DS1990
Data: 01 A1 B2 C3 D4 E5 F6 07
```

- **`Key type:`** — `DS1990`, `Cyfral`, `Metakom`.
- **`Data:`** — 8 hex bytes for DS1990 (family + serial + CRC), fewer
  for CYFRAL/Metakom.

## Capabilities and limits

- **Read is a one-shot exchange** — touch and go. No auth.
- **Emulate is reliable** — most 1-Wire readers accept the Flipper's
  emulation.
- **Write requires a compatible blank.** DS1990A itself is read-only;
  you need RW1990, TM2004, or similar.
- **CYFRAL/Metakom emulation** can be finicky against particular
  intercoms with tight timing tolerances. Try a few times before
  concluding failure.
- **Range: contact.** By protocol design; there is no "read at a
  distance" for iButton.
- **The GPIO 1-Wire pin (pin 7)** is shared with the on-board contact —
  you can attach an external 1-Wire reader on the GPIO if the built-in
  contact is worn or damaged.

## Common tasks

- **Read a DS1990A key:** iButton app → Read → touch to Flipper's
  contact.
- **Emulate:** iButton → Saved → Emulate. Touch the Flipper's contact
  to the intercom reader.
- **Write to an RW1990 blank:** iButton → Saved → Write. Requires
  physical blank + brief contact.
- **Identify unknown intercom protocol:** iButton → Read → the app
  reports DS1990/CYFRAL/Metakom automatically. If it fails all three,
  the intercom probably isn't 1-Wire (could be a 125 kHz LF RFID
  reader in disguise).
- **Verify a write:** re-read the newly written blank; the `Data:`
  should match.

## Gotchas

- **"Just serial number" ≠ "no security."** A DS1990A read is trivial;
  substituting one for the target is exactly the point of the physical
  design's use as an access token. But some newer intercom systems layer
  a second factor (keypad code) on top.
- **The Flipper's contact wears.** Constant use degrades the contact
  point. Route through the GPIO 1-Wire pin (pin 7) for long-term rigs.
- **CYFRAL and Metakom emulation timing** varies by build. If a
  specific intercom rejects your emulation but accepts the original,
  update firmware.
- **Some "iButton keys" in the wild are actually 125 kHz RFID fobs**
  disguised as pucks. If the Flipper can't read on 1-Wire, try RFID
  (`rfid-lf-overview.md`).
- **CRC mismatch on DS1990A reads** — the app rejects reads with wrong
  CRC. Usually indicates bad contact rather than a bad key; try again.

## Legal & safety notes

Duplicating access tokens for buildings you don't have authorization
for is a criminal offense in most jurisdictions. See
`legal-and-safety.md`.

## See also

- `flipper-hardware.md` — the iButton contact hardware.
- `flipper-gpio-pinout.md` — pin 7 (1-Wire) details.
- `flipper-cli.md` — `ibutton` verbs.
- `flipper-storage.md` — `/ext/ibutton/`.
- `rfid-lf-overview.md` — the 125 kHz counterpart.

---
*Attribution:* Dallas Semiconductor / Maxim Integrated DS1990A
datasheet, Flipper-Devices iButton docs, community documentation of
CYFRAL and Metakom framing (RadioLibrary, various). Retrieved 2025-Q3.
