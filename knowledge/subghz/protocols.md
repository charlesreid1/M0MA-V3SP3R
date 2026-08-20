# SubGHz — protocols

> The protocol families the Flipper Zero's SubGHz decoder recognises,
> what each looks like on the wire, and which ones are trivially
> replayable versus rolling-code.

## What it is

The Flipper's SubGHz app ships a decoder library covering a few dozen
common protocols in the 300–928 MHz range. When you press *Read* and
capture a remote, the app tries every decoder against the incoming
waveform. If one matches, the resulting `.sub` file gets a `Protocol:`
field and a `Key:`; if none match, the file gets `RAW_Data`.

This doc walks the families the decoder covers. For the physics/radio
side, see `subghz-overview.md`. For the file format, see
`signal-formats-sub.md`.

## How it works — protocol reference

### Fixed-code protocols

*Every button press sends the same code every time.* Replay works
indefinitely. These are trivial to intercept + retransmit; they still
account for the majority of cheap remotes in the wild as of 2025.

#### Princeton (PT2262 / PT2264 / SC2262)

- **Modulation:** OOK/ASK.
- **Frequency:** typically 315 or 433.92 MHz.
- **Bit rate:** 1.5–4 kbps (depends on remote's timing resistor).
- **Frame:** 24 bits, tri-state (0/1/floating) → 8 tri-state trits ×
  3 bits each. Sync gap follows.
- **Rolling?** No. Same code every time.
- **Replay-attack posture:** trivial. Capture once, replay forever.
- **Typical use:** cheap wireless doorbells, generic remote outlets,
  toy remotes.

#### CAME (12/24-bit)

- **Modulation:** OOK/ASK.
- **Frequency:** 433.92 MHz (EU), 315 MHz (US).
- **Bit rate:** ~200 bps.
- **Frame:** 12 or 24 bits, Manchester-adjacent encoding.
- **Rolling?** No.
- **Typical use:** older CAME gate openers.

#### Nice FLO / FLO-R (fixed-code Nice)

- **Modulation:** OOK/ASK.
- **Frequency:** 433.92 MHz.
- **Frame:** 12-bit or 24-bit.
- **Rolling?** FLO is fixed; **FLO-R is rolling** (see below).
- **Typical use:** Italian-brand gate remotes.

#### Faac SLH

- **Modulation:** OOK/ASK.
- **Frequency:** 868 or 433.92 MHz.
- **Rolling?** Fixed variants exist; some SLH2 versions are rolling.
- **Typical use:** commercial gate systems.

#### Doitrand, Hörmann (fixed variants), GT-WT-01

- Doitrand: gate remotes, 433.92 MHz, fixed.
- Hörmann HSM4: older garage remotes, 868/433 MHz, fixed. Newer
  Hörmann is rolling.
- GT-WT-01 / GT-WT-02: **weather stations**, 433.92 MHz, OOK Manchester.
  Fixed sensor ID, changing data payload. Great learning target — see
  the `skill-signal-analysis` playbook (§6.6).

#### Generic OOK-ASK learner

If nothing else decodes, the "Static" decoder tries to fit a
generic OOK pattern. Success is spotty; a raw capture is often more
useful.

### Rolling-code protocols

*Every button press sends a different code.* Replay of a single
capture works exactly *once* — after the target sees a code, it
increments its expected counter and refuses replays.

#### KeeLoq (Microchip HCS200 / HCS300 / HCS301)

- **Modulation:** OOK/ASK, some FSK.
- **Frequency:** 315 / 418 / 433.92 / 868 MHz depending on region.
- **Bit rate:** ~830 bps (HCS300 default).
- **Frame:** 66 bits = 32-bit encrypted rolling code +
  28-bit fixed serial + 4-bit button + 2-bit status.
- **Encryption:** proprietary NLFSR ("Classic KeeLoq"). Cipher is
  broken academically (Bogdanov 2007, Eisenbarth 2008); the practical
  attack requires two captures and a per-manufacturer key.
- **Replay posture:** single-shot; second-use rejected.
- **The Flipper's decoder** identifies the format and prints the
  serial number and button bit, but does **not** decrypt the rolling
  half. Momentum / RogueMaster include mfkey-style helpers for some
  known-manufacturer keys.
- **Typical use:** Microchip HCS-based gate/garage remotes (HCS300
  in particular is ubiquitous), some car aftermarket alarms.

#### Security+ 1.0 and Security+ 2.0 (Chamberlain / LiftMaster)

- **Modulation:** Complex FSK for 2.0; earlier 1.0 is FM.
- **Frequency:** 315/390 MHz (US, most common).
- **Rolling?** Yes; 40-bit rolling code on 1.0, extended on 2.0.
- **Replay posture:** single-shot; some 2.0 targets tolerate a small
  window.
- **The Flipper decoder** parses both.

#### Somfy Telis

- **Modulation:** OOK.
- **Frequency:** 433.42 MHz (note the offset — not 433.92).
- **Rolling?** Yes.
- **Typical use:** Somfy blinds/awnings.
- Common capture-failure cause: users tune to 433.92 and hear nothing.

#### Hörmann (rolling variants) / BFT

- Rolling successors to the fixed variants above. Same decoders,
  different keys.

#### Nice FLO-R / FLOR-S

- FLO-R adds rolling code atop FLO. FLOR-S is the newer version.
- 433.92 MHz.

### Weather / telemetry protocols

Not "attackable" in the credential sense — they're sensor telemetry.
Useful for learning and for legitimate weather-monitor projects.

- **GT-WT-01/02** — see above.
- **Nexus-TH** — 433.92 MHz, 8-bit temperature + humidity + battery.
- **Kedsum / Nexus / others** — similar OOK Manchester schemes.
- **Acurite** — 433.92 MHz, several variants.

The Flipper decodes many of these, exposing the sensor ID + temperature
value directly.

## Capabilities and limits

- **Decoder table is finite.** New protocols get added by community
  PRs to firmware; you can add your own on Momentum by writing a
  decoder plugin. Anything outside the table lands in RAW_Data.
- **Rolling-code capture is inherently one-shot.** No amount of clever
  replay changes that. Attacks require capture + cryptanalysis
  (Rolljam, keylog, etc.) which the Flipper's built-in tools don't
  automate.
- **KeeLoq keys are per-manufacturer.** Without the right manufacturer
  key, decryption of the rolling half is not feasible from a single
  captured pair.
- **Protocol families overlap in the wild.** A "433 MHz gate remote"
  might be Princeton, CAME, or KeeLoq depending on year and brand. The
  Flipper's Read function tries all decoders — trust it.
- **Region and frequency drift.** Older EU remotes often on 40 MHz
  (below CC1101 range); newer ones on 433/868. If you can't decode,
  first confirm the frequency with Frequency Analyzer.

## Common tasks

- **Capture then decode a Princeton remote:** SubGHz app → Read on
  433.92 MHz `AM650` → press remote → save `.sub` should have
  `Protocol: Princeton`.
- **Learn a rolling KeeLoq remote:** SubGHz → Read → press once →
  save. The `.sub` shows serial number and button. Retransmission
  works exactly once before the target increments.
- **Distinguish CAME from Princeton:** both are OOK/ASK 433.92 MHz;
  the decoder tells you. If both fit, packet length differs
  (CAME 12/24 vs Princeton 24 tri-state bits).
- **Get a weather-station reading:** SubGHz Read on 433.92 MHz →
  ambient temp devices broadcast every ~30 s; decoder prints value.
- **Brute-force a Princeton 24-bit code:** SubBrute app on Momentum,
  or a custom script. Only viable because Princeton is 12-bit
  addressable in tri-state (~half a million combinations, tractable).

## Gotchas

- **"Decoded then replayed" ≠ "compromised."** Fixed-code = yes,
  compromised. Rolling-code = single replay = *maybe* opens the target
  once, then never again.
- **KeeLoq HCS300 vs HCS301:** HCS300 uses a fixed serial + rolling
  counter; HCS301 supports a wider counter and different framing.
  Decoders sometimes label them interchangeably; check the raw bit
  count.
- **Somfy on 433.42, not 433.92.** Trip everyone hits once.
- **Weather stations transmit continuously.** Long captures fill
  RAW_Data buffers. Set a short duration.
- **The Flipper's SubBrute is not a magic gate-opener.** It only
  helps against protocols where brute force is combinatorially
  feasible — Princeton, some CAME. Not KeeLoq.
- **`.sub` files with `Protocol: Unknown`** got RX'd but not decoded.
  They may still be replayable via RAW_Data.

## Legal & safety notes

TXing on any of these protocols outside licensed use — and cloning /
replaying / brute-forcing credentials against systems you don't own — is
regulated by RF law and by access-control / computer-fraud law
respectively. See `legal-and-safety.md`.

## See also

- `subghz-overview.md` — the radio physics side.
- `signal-formats-sub.md` — how these show up in `.sub` files.
- `skill-signal-analysis` (slice 6.6) — capture-to-decode methodology.
- `firmware-families.md` — decoder additions per firmware.
- `flipper-cli.md` — `subghz decode_raw` etc.

---
*Attribution:* Flipper-Devices protocol source
(`lib/subghz/protocols/`), Microchip HCS300 datasheet, Bogdanov et al.
"A Practical Attack on KeeLoq" (2008), Somfy RTS reverse-engineering
notes (community). Retrieved 2025-Q3.
