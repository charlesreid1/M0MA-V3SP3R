# Legal & safety — practical map

> Not legal advice. A directional summary of where the tripwires are
> around the Flipper Zero, Momentum, and Marauder. Every capability
> doc in the corpus links here.

**This is not legal advice.** Laws vary by jurisdiction and change
over time. If you're doing something that might be regulated, talk to
a lawyer. The purpose of this doc is to help you *know when to worry* —
not to make the decision for you.

## What it is

The Flipper Zero + Momentum + Marauder stack can do things that
happen to be regulated by four broad legal regimes:

1. **RF spectrum law** — FCC in the US, ETSI/national telecoms in
   the EU, similar bodies worldwide. Governs *what you can transmit
   and how much*.
2. **Computer-fraud / unauthorized-access law** — US CFAA, UK CMA, EU
   NIS, similar globally. Governs *accessing systems you don't own*.
3. **Access-control token / counterfeiting law** — Governs *cloning
   or forging physical access credentials*.
4. **Interception / wiretap law** — Governs *capturing communications
   in transit*.

Capabilities in this corpus routinely touch two or more of these at
once.

## By subsystem

### SubGHz TX

**Regulation:** RF spectrum law (regime 1).

- **US:** FCC Part 15 defines ISM bands (315 MHz, 433.92 MHz US,
  902-928 MHz US ISM) and duty-cycle / power caps. **Transmitting
  outside ISM allocations or above the power cap violates 47 CFR §
  15.5** — intentional interference. Fines can be substantial.
- **EU:** ETSI SRD rules (EN 300 220 for <1 GHz). 433.92 and 868 MHz
  are the common bands; strict duty cycle (~1%) on 868 MHz.
- **Region unlocks** (Momentum, Unleashed, RogueMaster) *let* the
  radio TX on prohibited bands. They do not legalize it. See
  `firmware-momentum.md`, `subghz-overview.md`.

**When to worry:**

- TXing anywhere the CC1101 can physically tune but the current region
  disallows.
- Repeatedly retransmitting on ISM within duty-cycle caps for extended
  periods.
- TXing on non-ISM frequencies (e.g. 27 MHz CB, 40 MHz RC toys, 869.5
  MHz medical) at all.

### NFC / RFID / iButton cloning

**Regulation:** Access-control / counterfeiting (regime 3) + CFAA
(regime 2) if used to gain access.

- **US:** 18 U.S.C. § 1029 (access device fraud) covers cloning of
  most physical credentials tied to a computer system. CFAA (18 U.S.C.
  § 1030) covers subsequent unauthorized access.
- **UK:** Computer Misuse Act 1990, Fraud Act 2006.
- **EU:** Directive 2013/40/EU on attacks against information systems.
- **Payment / EMV cards:** Cloning is theft/fraud in every
  jurisdiction. Full-stop illegal, not merely regulated. See
  `nfc-overview.md`.

**When to worry:**

- Cloning a physical access card you don't have written authorization
  to duplicate.
- Reading + storing NFC/RFID/iButton data from cards you don't own,
  in bulk or in a way that could be construed as preparing to clone.
- Using an emulated credential to enter a space.

### Reading NFC / RFID / iButton passively

**Regulation:** Depends on jurisdiction; grey area.

- Reading in your own hand of a card you own: uncontroversial.
- Reading in a public space of cards belonging to others: legally
  murky. Some jurisdictions treat NFC skimming as attempted theft;
  others don't cover it.
- MIFARE Classic dictionary attacks against random cards: not
  criminal in itself (you're not accessing a system), but the
  possession + use of resulting credentials often is.

### BadUSB

**Regulation:** CFAA (regime 2).

- **US CFAA § 1030(a)(5)(A):** intentionally causing damage to a
  computer without authorization is a federal crime.
- Running BadUSB against a computer you own: fine.
- Running BadUSB against any computer you don't have written
  authorization to test: illegal under CFAA and near-universally
  under equivalent laws.
- Even "recon" payloads (dir listings, hostname enumeration) violate
  CFAA when unauthorized, per multiple prosecutions.

### WiFi / Marauder attacks

**Regulation:** RF spectrum law (regime 1) + CFAA (regime 2) +
telecom law (regime 4).

- **Deauth (802.11 deauth injection):**
  - **US:** FCC 47 CFR § 15.5 — intentional interference. FCC has
    issued citations for "jammers" including software-based deauth.
  - **UK:** Wireless Telegraphy Act 2006.
  - **EU:** Similar national laws.
  - Also violates CFAA if it disrupts a system you don't own.
- **Evil Twin / rogue AP / KARMA:**
  - Impersonating a network to lure clients: CFAA (system access) +
    wire-fraud statutes in most cases.
  - Even capturing credentials at the AP-level is federal wiretap in
    the US (18 U.S.C. § 2511).
- **PMKID / WPA handshake capture:**
  - Passive RX-only capture is legal in some jurisdictions, illegal
    in others (e.g. Germany treats it as interception).
  - Cracking is legal only against your own network.
- **Beacon spam:**
  - Rickroll / joke SSID lists: grey area. Not usually prosecuted
    but has legal exposure under interference statutes.

See `marauder-overview.md`, `marauder-commands.md`.

### IR

**Regulation:** Effectively unregulated.

- IR is not a licensed spectrum.
- The nearest legal exposure is harassment / property damage law if
  you disrupt commercial displays or safety systems (e.g. pointing
  Universal Remote "Power Off" at kiosks in a business).

### BLE spam (Apple / Google / Swift / Samsung pairing spam)

**Regulation:** Grey.

- Trivial nuisance most of the time.
- May be reachable under interference law in aggressive
  jurisdictions.
- Physical-world harassment concerns if used to target specific
  individuals.

## By activity — decision heuristics

**"Can I do this legally?"**

- **On my own hardware / my own network:** almost always yes.
- **On hardware / networks I have written authorization for:**
  yes, and that authorization is your defense.
- **On hardware / networks I don't own:** almost always no. There
  are narrow research exceptions (DMCA § 1201 in the US for some
  reverse engineering) but they don't cover most Flipper workflows.

**"But it's just RX / passive / reading?"** Passive RX of some
information is legal (broadcast radio, unencrypted Wi-Fi in the US
under some conditions). Passive RX of *encrypted* communications is
frequently illegal (US wiretap law: 18 U.S.C. § 2511 covers even
"attempted" interception in many cases).

**"But my region unlock lets me TX on X MHz."** Firmware ≠ law. The
firmware removes a software check; the FCC/ETSI cares whether you
transmitted.

**"But nobody notices Wi-Fi attacks in a coffee shop."** WIDS is
increasingly ubiquitous. Public networks are often monitored. Corporate
networks are almost always monitored. Prosecutions for coffee-shop
deauth have happened.

## Practical safety patterns

- **Own your test targets.** Set up your own network, your own NFC
  cards, your own remotes. Attack those.
- **Document authorization.** For pentest engagements, get scope in
  writing. Include specific IP ranges / BSSIDs / physical locations /
  card ranges you're allowed to test.
- **Isolate test environments.** RF spills. Faraday bags, screen
  rooms, or at minimum "in a basement / rural location."
- **Log your activity.** Vesper's audit trail (see `architecture.md`)
  gives you an evidence trail if something goes sideways.
- **Assume you're on camera.** Physical access-control tests are
  frequently CCTV-recorded.

## What Vesper does about it

- **`RiskAssessor`** (see `architecture.md`) rates every
  `execute_command` invocation LOW / MED / HIGH and requires user
  confirmation for MED / HIGH.
- **`FirmwareCompatibilityProfile`** (see
  `firmware-compatibility-profile.md`) routes TX-heavy verbs through
  the app-bridged RPC path when possible, giving Vesper a second
  gate.
- **`AuditService`** logs every command with justification and
  outcome to Room DB.
- **The Vesper app cannot override the physical radios' limits.**
  Momentum's region unlocks come from firmware; Vesper can only
  refuse to *request* certain frequencies via the schema.

Vesper is a technical hard-liner — it can prevent accidental
transmissions and log intentional ones. It cannot make an illegal
transmission legal.

## See also

- `subghz-overview.md` — RF regulation details for SubGHz TX.
- `nfc-overview.md` — MIFARE / access-control specifics.
- `rfid-lf-overview.md` — LF RFID cloning specifics.
- `ibutton-overview.md` — iButton cloning specifics.
- `marauder-overview.md` — 802.11 attack specifics.
- `marauder-commands.md` — command-level legal callouts.
- `firmware-families.md` / `firmware-momentum.md` — region policy in
  firmware.
- `architecture.md` — Vesper's local enforcement mechanisms.

---
*Attribution:* Not legal advice. Cited statutes are for orientation.
FCC 47 CFR Part 15 (US), ETSI EN 300 220 (EU), 18 U.S.C. § 1029 &
§ 1030 (US), UK Computer Misuse Act 1990, EU Directive 2013/40/EU,
18 U.S.C. § 2511 (US Wiretap Act). Reviewed against 2025-Q3
regulatory posture; check current text before relying.
