# NFC — overview

> The Flipper Zero's 13.56 MHz NFC/HF subsystem: which tags it reads,
> emulates, and what attacks the app catalog automates.

## What it is

The Flipper Zero has a **13.56 MHz** NFC front-end driven by the
**ST25R3916** (early revs; some later revs use a successor chip in the
same ST family — cite the current Flipper-Devices schematic before
making hardware claims). It supports ISO 14443 Type A, Type B (partial),
ISO 15693 (vicinity, partial), FeliCa (partial), and the Flipper's
peer-to-peer emulation and reader modes.

## How it works

### Standards supported

| Standard              | Tag examples                                      | Read | Emulate | Extra                                                  |
|-----------------------|---------------------------------------------------|------|---------|--------------------------------------------------------|
| **ISO 14443A**        | MIFARE Classic 1K/4K, Ultralight, DESFire, NTAG21x | Yes  | MIFARE + UL yes; DESFire partial | Full attack suite for MIFARE Classic (see below). |
| **ISO 14443B**        | Some banking/credit interfaces                    | Partial | No     | Reader-only, limited protocol coverage.                |
| **ISO 15693**         | Vicinity tags (long-range HF)                     | Yes  | Some     | Growing over 2024-2025 releases.                       |
| **FeliCa (JIS X 6319-4)** | Suica, Octopus, older Japanese payment          | Partial | No     | Read-only, framing quirks.                             |

The 90% case: **MIFARE Classic 1K**. It's the most common access
control card in the world. Everything else is a smaller subset of the
workflow.

### Reader vs emulator vs peer modes

- **Reader mode** — Flipper powers the field, tags respond. This is
  how `nfc detect` and the Read function work.
- **Emulator mode** — Flipper responds as if it were a tag. Reader
  hardware (a wall reader, a phone) sees the emulated card.
- **Peer / dev mode** — Flipper-to-Flipper, or Flipper as an NFC
  "phone" in some experimental app modes.

Antenna limits: **a few centimeters** in reader mode, similar for
emulation. Wall readers may not couple; the Flipper's antenna is small.

### MIFARE Classic — key derivation

MIFARE Classic (1K, 4K, Mini) is a proprietary card from NXP using the
Crypto-1 cipher, broken since 2008 (Nohl et al.). It has:

- 16 sectors × 4 blocks (1K) or 40 sectors (4K).
- Each sector has a **Key A** and **Key B** stored in the sector
  trailer, plus access-condition bits.
- Every block-read requires a per-sector authentication with a valid
  key.

The Flipper's attack chain:

1. **Dictionary attack** — try common keys from `mf_classic_dict.nfc`
   and `mf_classic_dict_user.nfc` (both under `/ext/nfc/assets/`).
   Sector trailers often use factory-default keys (`FFFFFFFFFFFF`,
   `A0A1A2A3A4A5`, transit-system-specific keys). Solves ~70% of
   cards in the wild.
2. **Nested attack** — once *any* key is known, exploit Crypto-1's
   PRNG weakness to derive keys for other sectors. Requires ~2 000
   authentications; ~30 s on-device.
3. **Hardnested attack** — variant against newer MIFARE Classic EV1
   cards with hardened PRNG. Slower, ~2 min.
4. **Static Encrypted Nonce / MFKey32** — recovers keys from a
   captured reader-tag exchange. Flipper's `mfkey32` app processes
   `.nfc_log` captures.

Not all of these are enabled by default:

- **Official firmware:** dictionary attack + partial nested.
- **Momentum:** all four, integrated into the NFC app UI.
- **Unleashed / RogueMaster:** all four.

### DESFire, NTAG21x, and others

- **MIFARE DESFire (EV1/EV2/EV3)** — Real crypto (AES-128 in modern
  variants). Not broken; Flipper reads UID + partial ATQA/SAK but can't
  auth without keys. Emulation is impractical.
- **MIFARE Ultralight** — 512-bit tag, some models writable. Flipper
  reads and can emulate fully.
- **NTAG213/215/216** — NFC-Forum-compliant, URI/URL storage. Flipper
  reads NDEF messages; emulation supported.

### File format

`.nfc` savefiles hold the tag dump. See `signal-formats-nfc.md`.

## Capabilities and limits

- **Range: ~2–4 cm.** Frustratingly short. Wall readers with large
  antennas can extend the effective reader-to-tag distance to 10+
  cm, but the Flipper *itself* is short-range.
- **DESFire and modern EMV cards are out of reach.** Reading UIDs
  is not equivalent to cloning.
- **Read speed** — sector reads on MIFARE Classic are ~50 ms each
  once authenticated. A full 1K dump with known keys: ~5–10 s.
- **Emulation is per-frame** — the ST25R3916 handles anti-collision;
  higher-level command handling is firmware. Some readers time out on
  the extra latency Flipper adds. Momentum reduces this on newer
  builds.
- **Magic cards (UID-writable "Gen 1a/1b/2" MIFARE clones)** — the
  Flipper writes MIFARE Classic dumps to these, giving you a physical
  clone. Legitimate blank + tools required.

## Common tasks

- **Read a MIFARE Classic 1K with known/default keys:**
  1. NFC app → Read.
  2. Present card. Flipper auto-detects MFC-1K.
  3. Runs dictionary attack, then nested if partial.
  4. Save `.nfc` — includes UID, ATQA, SAK, per-sector keys, and
     block dumps.
- **Emulate a saved MIFARE card:** NFC → Saved → pick file → Emulate.
- **Write to a magic card:** load `.nfc` → Write. Requires a
  Gen1/Gen2 blank; the Flipper detects magic-card capability.
- **Log a reader-tag exchange for MFKey32:** NFC → Detect Reader →
  present Flipper to reader → captures nonces to `.nfc_log`. Run
  MFKey32 app on-device to recover keys.
- **Read an NTAG NDEF URL:** NFC → Read → tag with a URL comes back
  parsed.
- **Read UID only:** `nfc detect` from CLI. Fast; doesn't try key
  derivation.

## Gotchas

- **UID ≠ full card.** Cloning a card that identifies solely by UID
  (older access control) is one thing; cloning MIFARE Classic
  (which uses per-sector auth) requires keys.
- **"Magic UID" cards vs original cards.** Some readers detect
  cloned magic cards (they emit slightly different ATQAs or timing
  fingerprints). Cloning ≠ undetectable substitution.
- **The `mf_classic_dict.nfc` on `/ext/nfc/assets/` is firmware-managed.**
  Firmware updates overwrite it. Put your custom keys in
  `mf_classic_dict_user.nfc` — that one is preserved.
- **Some transit / access systems mix MIFARE Classic with additional
  encrypted layers.** A successful key extract doesn't automatically
  clone functionality.
- **DESFire read output looks like success but isn't.** The Flipper
  will happily report UID + ATQA + SAK for a DESFire card, giving
  the impression it "read" the tag. It only read the meta.
- **FeliCa framing errors are common.** Not all Japanese cards are
  identically parseable; expect quirks.
- **Hardnested is slow.** Progress bar can look stuck for a minute.
  Don't power-cycle.

## Legal & safety notes

Reading MIFARE UIDs in a public setting is a grey area. Cloning access
credentials (building access, transit cards) without authorization
is a criminal offense in most jurisdictions (access-control law +
computer-fraud law). Reading/copying stored value on transit or payment
cards is theft. See `legal-and-safety.md`.

## See also

- `signal-formats-nfc.md` — `.nfc` file format.
- `flipper-storage.md` — `/ext/nfc/`, `/ext/nfc/assets/` layout.
- `flipper-cli.md` — `nfc` verbs.
- `flipper-hardware.md` — antenna specifics.
- `skill-signal-analysis` (slice 6.6) — capture-and-attack methodology.

---
*Attribution:* Flipper-Devices NFC docs
(<https://docs.flipper.net/nfc>), ST25R3916 product datasheet, Nohl
et al. "Cryptanalysis of Crypto-1" (2008), MIFARE Classic vendor
documentation (NXP). Retrieved 2025-Q3.
