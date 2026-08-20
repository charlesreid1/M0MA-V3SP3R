# LF RFID — overview

> The Flipper Zero's 125 kHz LF RFID reader/emulator/writer: supported
> cards, T5577 blanks, workflow.

## What it is

The Flipper Zero has a **125 kHz** low-frequency RFID front-end using
the on-board LF antenna (a coil laid into the enclosure). LF RFID
predates NFC by decades and is still ubiquitous in physical access
control: hotel key cards, corporate ID badges, garage doors, small-office
door readers.

Unlike NFC (13.56 MHz, ISO 14443), LF RFID protocols are simpler,
proprietary, and typically don't use per-transaction cryptography. This
makes LF cards **substantially easier to read and clone** than modern
NFC cards.

## How it works

### Supported card families

| Card                 | Bits    | Structure                                      | Notes                                     |
|----------------------|---------|------------------------------------------------|-------------------------------------------|
| **EM4100 / EM4102**  | 64      | Vendor ID + serial, Manchester encoded         | The single most common cheap key fob.     |
| **HID Prox H10301**  | 26      | Format code + facility code + card number      | 26-bit HID — the "classic" corporate badge.|
| **HID Prox H10302**  | 37      | 37-bit HID variant                             | Larger orgs, some universities.           |
| **HID Prox Corp1000**| 35      | Custom HID format                              | Corporate variants.                       |
| **HID Prox H10304**  | 37      | Another HID variant                            |                                            |
| **Indala**           | 26/27/40+ | Vendor-specific PSK-encoded                  | Older but common.                         |
| **IoProx**           | 44      | Kantech format                                 | Canadian access systems.                  |
| **AWID**             | 26/34   | Similar to HID Prox                            |                                            |
| **Paradox**          | 44+     | Vendor-specific                                |                                            |
| **PAC / Stanley**    | 26      |                                                |                                            |
| **Nedap**            | 64      |                                                |                                            |
| **T5577**            | **writable blank** — reprogrammable to emulate any of the above | See below. |

Beyond this list, the Flipper's raw reader mode can capture Manchester
or biphase LF signals that don't fit a named format; you can save them
as `.rfid` files and replay.

### T5577 — the writable blank

The T5577 is a **rewritable 125 kHz LF tag** you can program to emulate
any of the fixed-format protocols above. Buy cheap on eBay/Amazon in
credit-card or fob form. Flipper writes T5577 config + data blocks;
after writing, the T5577 responds *as if it were* the target card
type.

Workflow: capture target card → save as `.rfid` → present T5577 blank →
Write → clone.

**Password protection:** T5577 supports a 32-bit write password. If
someone has set one, the Flipper needs it to rewrite. Default (blank)
T5577s ship with no password. Setting a password on a T5577 with no
recovery of the password bricks the tag (for rewrites; it still
functions as the last-programmed card).

### File format

`.rfid` files are plain text, similar to `.sub`/`.nfc`:

```
Filetype: Flipper RFID key
Version: 1
Key type: EM4100
Data: 12 34 56 78 9A
```

Or for HID Prox H10301:

```
Filetype: Flipper RFID key
Version: 1
Key type: H10301
Data: 00 12 34 56 78
```

`Data` is protocol-specific. Some formats have additional fields
(`Facility code`, `Card number`) that the app parses out of the raw
bytes.

## Capabilities and limits

- **Range: ~5 cm** in reader mode; comparable in emulate. LF antennas
  are directional — hold the back of the Flipper flat.
- **Read is easy.** LF cards emit their data continuously when
  energized; no auth, no challenge.
- **Emulate works reliably** for the supported formats. Most physical
  access readers accept the emulated response.
- **Write** only works to T5577 blanks (and a handful of other
  writable chips like Hitag2 on some firmwares). You cannot write to
  a factory-EM4100 tag — those are read-only.
- **T5577 password lock is destructive** if forgotten. Write down passwords.
- **No cryptography** on standard LF protocols. Cloning = full
  duplication of function. This is the most important difference from
  NFC.

## Common tasks

- **Read an unknown 125 kHz card:** RFID app → Read → present card →
  the Flipper cycles through formats and reports the first match.
- **Read raw (unknown format):** RFID → Read Raw → save `.rfid` with
  `Key type: RAW`. Rarely needed.
- **Save + emulate an EM4100 fob:** RFID → Read → Save → Emulate. Same
  workflow as NFC.
- **Write an EM4100 to a T5577 blank:** load a saved `.rfid` → Write.
  T5577 must be present; app confirms detection first.
- **Read an HID Prox badge (H10301):** same as EM4100 workflow; the
  Flipper decodes the format code + facility code + card number.
- **Detect T5577 vs original card:** RFID → Extra Actions → detect
  writable tag. Momentum has more comprehensive detection.

## Gotchas

- **Reading a "MIFARE" or "HID iCLASS" badge on 125 kHz will fail.**
  Those are 13.56 MHz cards; use the NFC app (see `nfc-overview.md`).
  Confusion between "prox" (LF, 125 kHz) and "iCLASS" (HF, 13.56 MHz)
  is the #1 support question.
- **HID Prox reads sometimes come back with wrong facility code.** LF
  reads are timing-sensitive; try again with the card in a different
  position.
- **T5577 write requires precise coupling.** A poor read of the target
  card, followed by a good write to T5577, produces a well-written
  wrong card. Verify by reading the T5577 back.
- **Some access readers detect T5577 by response timing** and reject
  clones. This is uncommon but worth noting.
- **Indala PSK-encoded cards** may need multiple read attempts to
  demodulate cleanly.
- **Firmware family matters slightly** — Official's format library is
  smaller than Momentum's. If a card decodes on Momentum but not
  Official, that's why.

## Legal & safety notes

Copying a physical access-control credential without authorization is
a criminal offense in most jurisdictions — often under "counterfeiting
of tokens" or similar statutes, and always under computer-fraud /
trespass law if used to enter a building. See `legal-and-safety.md`.

## See also

- `flipper-hardware.md` — LF antenna specifics.
- `flipper-storage.md` — `/ext/rfid/` layout.
- `flipper-cli.md` — `rfid` verbs (read/write/emulate).
- `nfc-overview.md` — the HF counterpart, for the "prox vs iCLASS"
  distinction.
- `ibutton-overview.md` — the 1-Wire access-control counterpart.

---
*Attribution:* Flipper-Devices RFID docs
(<https://docs.flipper.net/rfid>), HID Corporation format
documentation, T5577 datasheet (Atmel). Retrieved 2025-Q3.
