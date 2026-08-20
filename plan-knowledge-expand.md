# plan-knowledge-expand.md — Expand the MCP knowledge corpus

Goal: turn the MCP corpus (`docs/` + `README.md`, exposed by
`mcp-server/src/vesper_mcp/tools/knowledge.py`) into a genuine
Flipper-Zero-plus-Momentum-plus-Marauder subject-matter expert, not just
a description of the Vesper Android app.

The MCP is a **knowledge-focused corpus server** (see `plan-mcp.md` §9).
That framing is the load-bearing constraint here: every doc added must be
useful to someone *reading and reasoning* about a Flipper, not just
someone *driving one via the app*. If a doc only makes sense in the
context of `execute_command`, it belongs in `architecture.md`, not in
the expansion.

## 1. Current-state audit — what the corpus is and isn't

`vesper_mcp.tools.knowledge` picks up every `*.md` under `docs/` plus
the top-level `README.md`. Today that is:

| Topic id             | Actual content                                        | Flipper-domain content? |
|----------------------|-------------------------------------------------------|-------------------------|
| `readme`             | Project overview, app features, safety model         | Vesper app, not Flipper |
| `architecture`       | Cloud/phone/Flipper data-flow boundary, risk tiers   | Vesper app enforcement  |
| `app-build-process`  | Gradle + emulator instructions for the Android app   | None — build only       |
| `campaigns`          | Ralph autonomous campaign UX                          | Vesper feature          |
| `labs`               | Alchemy / Payload Lab UX                              | Vesper feature          |
| `mcp`                | How to run *this* MCP server                          | Meta / self-referential |
| `execute-command-schema` (JSON, not a topic) | ~60 action definitions                      | Interface, not concepts |

**Critical gap.** An MCP client asking "how does the Flipper's SubGHz
receiver actually work?", "what's the difference between Unleashed and
Momentum?", "what pins does the Marauder wire to?", or "why did my
KeeLoq capture roll?" gets nothing back. `search_docs` will hit the
word "SubGHz" inside `README.md`, but the substance is one bullet:
"SubGHz — transmit from file, passive receive with duration, decode
RAW_Data."

Additionally, the seven `SKILL.md` playbooks (bundled under
`app/src/main/assets/skills/`) are Flipper-domain methodology and never
reach the MCP corpus. They are the closest existing artefact to what
this plan is asking for, but they are packaged with the app and
addressed by name from `SkillRegistry`, not exposed by `list_topics()`.
Fixing that discoverability is part of the plan (§5).

## 2. Guiding principles for the expansion

1. **One-doc-per-concept.** Every new file answers one well-scoped
   question. Docs under `docs/` are addressable by topic id and read
   whole; long omnibus files are hostile to `read_doc`.
2. **Reader-first, not app-first.** Explain how the Flipper works, then
   note where the Vesper app surfaces it. If the Vesper app doesn't
   surface it at all (e.g. GPIO expansion boards), that's still worth
   knowing — the assistant should be able to answer "can I…" not just
   "does the app let me…".
3. **Fact-checkable, not marketing copy.** Frequencies, chip part numbers,
   pin names, protocol names, register widths, byte layouts. When a
   value comes from Flipper-Devices / Momentum / Marauder docs, cite the
   source in an "Attribution" footer so it can be re-verified later.
4. **Capability ≠ legality.** Every capability doc that touches emit /
   transmit / spoof / clone ends with a short *Legal & safety notes*
   block. This is not filler; the assistant will paraphrase it.
5. **Cross-link liberally.** Every new doc lists related topics at the
   bottom (`See also:`). `search_docs` is substring-only, so
   discoverability rides on explicit references.
6. **Kebab-case, `.md`, no numeric prefixes.** `list_topics` derives ids
   from filenames; keep them stable and searchable.
7. **Momentum and Marauder get first-class visibility, not footnotes.**
   The library name is M0MA-V3SP3R for a reason — those two pieces need
   dedicated top-level docs, not paragraphs buried inside larger files.
8. **The corpus is bundled into the wheel.** Per `plan-mcp.md` §2,
   `docs/` and `README.md` are force-included into `_knowledge/`. Every
   file added here ships with `pip install vesper-mcp`. Don't add
   anything that can't survive being read out of context.
9. **No dead links or code fences that look executable but aren't.** If
   a snippet is illustrative, mark it `# example — not runnable here`.
   MCP clients will paraphrase these to end users.
10. **Absolute dates.** Any doc that dates a firmware feature or protocol
    change writes the date out (`as of 2025-Q3`), not "recently".

## 3. Topic tree — the target corpus

New topics land under `docs/` unless noted. Existing topics get **update**
notes where they overlap. Every new doc gets a 1-line description here so
the maintainer can weigh what to write first.

### Group A — Flipper Zero hardware & platform

Foundational. Everything else references these. Roughly ~4 docs.

- **`flipper-hardware.md`** — MCU (STM32WB55RG, dual-core Cortex-M4 +
  Cortex-M0+ radio core), on-board peripherals (CC1101 SubGHz
  transceiver, ST25R3916 NFC, TI TRF7970A for HF fallback if applicable,
  IR TX/RX, iButton 1-Wire, RGB LED, LCD, vibration motor), memory
  layout (Internal flash / SD card / SPI Flash regions), buttons, power
  (battery + USB). Include a labelled diagram (ASCII fine) of the case
  layout. Cite Flipper-Devices HW docs.
- **`flipper-gpio-pinout.md`** — the 18-pin header, per-pin function
  (3V3, 5V/OTG, GND, PA7, PB2, PB3, PC0, PC1, PC3, PA6, PA4, PB2, PA15,
  etc.), which pins Marauder / VGM / NRF24 / other addons consume, GPIO
  voltage tolerance, OTG behavior, SWD debug lines. This doc is the
  authoritative pinout reference — Marauder / expansion docs point here.
- **`flipper-storage.md`** — SD card layout (`/int` vs `/ext`), what
  each subdir stores (`/ext/subghz`, `/ext/nfc`, `/ext/badusb`,
  `/ext/apps`, `/ext/apps_data`, `/ext/dolphin`, `/ext/update`), file
  formats you'll encounter (`.sub`, `.nfc`, `.ir`, `.rfid`, `.ibtn`,
  `.txt` for BadUSB, `.mus`/`.fmf`, `.fap`). Cross-link to
  `signal-formats-*`.
- **`flipper-cli.md`** — the on-device CLI (accessed over USB serial via
  qFlipper / picocom, or over BLE via Vesper). Full verb list with
  categories (help, device_info, storage.*, subghz.*, ir.*, nfc.*,
  rfid.*, ibutton.*, gpio.*, loader.*, badusb.*, led/vibro, log). What
  is *safe* to run (LOW-risk equivalents) vs *destructive*. Every
  CLI verb the app allowlists appears here.

### Group B — Firmware ecosystem, with heavy Momentum coverage

The current README lists four firmware families and never explains what
that means. This is the biggest single knowledge gap and where the
"M0MA" naming pays off. ~4 docs.

- **`firmware-families.md`** — overview + comparison. Official
  (Flipper-Devices), Unleashed, RogueMaster, **Momentum** (the primary
  target for this repo), and where they differ: SubGHz region unlock,
  hidden animations, extra apps preinstalled, iButton/NFC extensions,
  ESP32 companion integration. Include a compat matrix (feature × firmware).
  Cross-link `firmware-momentum.md`, `flipper-cli.md`, and
  `firmware-compatibility-profile.md`.
- **`firmware-momentum.md`** (⭐ M0MA priority) — dedicated deep dive.
  What Momentum is (community fork actively maintained; successor to
  Xtreme lineage), how it's structured, install flow (web installer /
  qFlipper / uFBT), unlocked features (worldwide SubGHz, extra NFC
  parsers, extended BadUSB, extra graphics packs, JS runner, etc.), the
  Momentum-specific `assets/` structure (packs / manifests), and — most
  importantly — what the *Vesper* `FirmwareCompatibilityProfile` selects
  when Momentum is detected on connect (which CLI verbs are wired, which
  quirks the `.sub` / `.nfc` parsers tolerate). Cite Momentum's GitHub +
  release notes.
- **`firmware-compatibility-profile.md`** — mechanics of the on-device
  probing Vesper does: how it reads `device_info`, what strings it looks
  for, which profile matches which firmware. Explain how a new fork can
  be added (edit `FirmwareCompatibilityProfile.kt`, extend the enum,
  ship a profile). This is the bridge from "how the firmware works" to
  "how the app handles it".
- **`firmware-updating.md`** — how firmware updates actually happen:
  the `.tgz` bundle, `/ext/update/` staging, `update` subcommand, DFU
  fallback via USB, brick-recovery. When you'd want each firmware
  family, and the tradeoffs (support burden, community risk, region
  policy). Legal note on region-lock removal.

### Group C — RF subsystems (SubGHz, IR, NFC, RFID, iButton, GPIO)

The single biggest expansion — this is what a Flipper user wants an
assistant for. Split by radio because the physics and protocols don't
generalize.

**SubGHz — ~3 docs.**

- **`subghz-overview.md`** — the CC1101 module, tunable range
  (300-348, 387-464, 779-928 MHz — with regional restrictions; note
  that Flipper Zero can *technically* tune 280–930 MHz but only certain
  windows are legal to transmit on and only certain firmware unlocks
  them), presets (AM270, AM650, FM238, FM476, custom), TX vs RX
  sensitivity, antenna considerations. Reference `signal-formats-sub.md`
  for the `.sub` file format. Section on the "why does my capture only
  RX and not decode?" question.
- **`subghz-protocols.md`** — walkthrough of the well-known families
  the Flipper's decoder recognises: Princeton, CAME, NICE FLO / Nice
  FloR-S, KeeLoq (Microchip HCS200/300/301 — HCS300 in particular),
  Faac SLH, Security+ 1.0 / 2.0 (Chamberlain), Somfy Telis, Hörmann,
  Doitrand, GT-WT-01 weather, LiftMaster, Chamberlain, generic OOK-ASK
  learners. For each: modulation, bit rate, packet structure, rolling
  vs fixed, replay-attack posture. Cross-link `signal-analysis` skill.
- **`signal-formats-sub.md`** — the `.sub` file format itself: header
  fields (`Filetype`, `Version`, `Frequency`, `Preset`, `Protocol`),
  data section (`Key` for protocol-decoded, `RAW_Data` for raw
  captures), how RAW_Data samples map to microseconds and mark/space
  transitions, gotchas (buffer size limits, TX vs RX preset asymmetry).
  Include one worked example for each of a decoded packet and a raw
  capture.

**Infrared — ~2 docs.**

- **`ir-overview.md`** — IR TX (38–56 kHz carriers), the two RX diodes,
  standard consumer protocols (NEC, NECext, Samsung32, RC5, RC6, Sony
  SIRC 12/15/20, Kaseikyo), universal remote database, custom raw
  waveforms.
- **`signal-formats-ir.md`** — the `.ir` file format, signal blocks,
  `type: parsed` vs `type: raw`, frequency + duty cycle + data arrays.

**NFC — ~2 docs.**

- **`nfc-overview.md`** — HF (13.56 MHz) via ST25R3916 (or successor
  chip on later Flipper hardware — call this out honestly, cite the
  current schematic), tag types the Flipper reads (ISO14443A —
  MIFARE Classic 1K/4K / Ultralight / DESFire, ISO14443B less common,
  ISO15693 vicinity, FeliCa partial), reading / detecting / emulating,
  Nested / Hardnested / Static Encrypted / MFKey32 attack notes at a
  methodology level (link to `signal-analysis` skill for concrete
  steps). MIFARE Classic key derivation is a big topic — give it its
  own subsection.
- **`signal-formats-nfc.md`** — the `.nfc` file format across
  MIFARE / ISO14443A / ISO15693 layouts, ATQA / SAK / UID / Blocks
  fields, sector-key encoding.

**LF RFID — ~1 doc.**

- **`rfid-lf-overview.md`** — 125 kHz, the on-board LF antenna,
  supported cards (EM4100, HIDProx H10301 / H10302 / Corp1000, T5577
  writable blanks, Indala, IoProx, AWID, Paradox, PAC/Stanley,
  Nedap). Read vs write vs emulate. T5577 configuration and password
  protection. Cross-link to `signal-formats-rfid.md`.

**iButton — ~1 doc.**

- **`ibutton-overview.md`** — 1-Wire protocol, Dallas DS1990A family,
  CYFRAL, Metakom. What "reading" and "emulating" actually do at the
  physical level. Common intercom / access-control contexts.

**GPIO / extensions — ~1 doc.**

- **`flipper-gpio-extensions.md`** — what boards / addons commonly
  plug into the 18-pin header: the WiFi devboard (Marauder / Blackmagic
  / other), NRF24 module, sub-GHz VGM external antenna, DAP-Link JTAG
  adapter, Video Game Module (STM32H7), GPIO breakout cables. Includes
  a compatibility note that only one board can occupy the header at a
  time and that the WiFi devboard is the one the app has first-class
  support for. Cross-link `marauder-*` docs.

### Group D — WiFi Marauder header (⭐ M0MA priority)

Dedicated top-level treatment. Not a "note on GPIO." ~4 docs.

- **`marauder-overview.md`** — what the WiFi devboard actually is
  (ESP32-WROOM-32 or ESP32-S2 depending on rev — cite the FZ store
  page + the JustCallMeKoko / Marauder wiki), what it does (802.11
  scan, deauth, beacon spam, evil twin, KARMA, PMKID harvest, Wall of
  Flippers detection), which chips speak which modes, why 802.11 requires
  a companion at all (STM32WB55's WiFi core doesn't exist — the
  co-processor is BLE-only).
- **`marauder-firmware.md`** — flashing (esptool / esp-idf / the
  Marauder web installer), current release cadence, mainline vs
  Momentum-shipped, how the ESP32 firmware exposes its CLI over UART
  (baud, framing, command list). Version notes: what commands landed
  in which Marauder release (as of 2025-Q3 / Q4 — timestamp all such
  claims). Cross-link `marauder-wiring.md`.
- **`marauder-wiring.md`** — the physical wiring: TX/RX/GND/3V3
  routing on the Flipper GPIO header to the ESP32 devboard, standard
  header vs alternate headers, the "screaming pink" official variant
  vs third-party carriers, common wiring failures (swapped TX/RX, no
  ground, undervoltage on 3V3 rail). Photos or ASCII diagrams. Ties
  directly to `flipper-gpio-pinout.md`.
- **`marauder-commands.md`** — the full command set exposed by the
  Marauder firmware, grouped by purpose (Scan / Attack / Utility),
  each with argument shape and example line. Explicitly bridges to
  the `wifi-attack` methodology skill (see §5) and to the currently
  deferred `wifi_marauder` typed actions mentioned in
  `plan-deferred-wifi-marauder.md`.

Legal & safety note is *loud* on every Marauder doc — 802.11 deauth
is illegal to transmit in most jurisdictions without authorization.

### Group E — Extension & development

Answers "how do I build on the Flipper", not just "how do I use it".
~3 docs.

- **`flipper-fap-apps.md`** — `.fap` app format, FURI (Flipper's
  RTOS), how to build a `.fap` with `uFBT`, install paths
  (`/ext/apps/…`), FapHub / catalog structure, sandboxing (or lack
  thereof), how the app calls into GUI / storage / SubGHz / NFC APIs.
  Reference to the community fap ecosystem.
- **`flipper-firmware-build.md`** — building firmware from source
  (`fbt`, `uFBT`, submodule structure, target names — usually just
  `f7` for Flipper Zero), how patches are usually organised in the
  community forks (Momentum's tree layout in particular), how to
  contribute upstream vs how to publish a fork.
- **`flipper-js-runner.md`** — the Momentum JavaScript runner
  (present in Momentum + Xtreme lineage, not in Official), what
  APIs it exposes (`storage`, `subghz`, `notification`, `gpio`,
  `math`, `keyboard`), and how it differs from writing a `.fap` in C.
  Cite the Momentum JS API reference.

### Group F — Signal analysis methodology (from bundled skills)

The seven `SKILL.md` playbooks are already good methodology docs — they
just aren't reachable via MCP. Bring copies into `docs/skills/` and let
`knowledge.list_topics()` see them. **Do not** move the originals; the
Android app still needs them at their existing paths for
`SkillRegistry`. The MCP-facing copies live under a nested folder so
they're identifiable as reused methodology rather than a doc written
for the corpus.

- **Update `mcp-server/src/vesper_mcp/tools/knowledge.py`** to also
  glob `docs/skills/*.md` (see §5 for the code change), and
- **Create `docs/skills/`** with symbolic-or-copied `SKILL.md`s renamed
  by topic (see §5 for the choice between copy-at-build-time vs
  in-place copy). Topic ids: `skill-ble-exploitation`,
  `skill-campaign`, `skill-payload-authoring`, `skill-pentest-report`,
  `skill-protocol-analysis`, `skill-signal-analysis`,
  `skill-wifi-attack`.
- **`docs/skills/README.md`** — one-page index explaining that these
  are the same playbooks the app's `SkillRegistry` loads on demand,
  what each is for, and when a caller should request one.

### Group G — Safety, legal, RF regulations

One doc, opinionated, short.

- **`legal-and-safety.md`** — a *practical* summary of the legal
  posture around SubGHz TX (FCC Part 15 in US, ETSI SRD in EU, region
  unlocks under Momentum/Unleashed, ISM bands), IR (unregulated),
  NFC (short range makes read benign but emulate/spoof is not), RFID
  cloning (access-control implications), BadUSB (CFAA / equivalent
  around unauthorized computer access), WiFi (deauth = intentional
  interference under FCC 47 CFR 15.5). Not legal advice; a directional
  map of where the tripwires are. Every capability doc's "Legal &
  safety notes" footer links here.

### Group H — Vesper positioning (existing docs, minor updates)

Corpus already covers this well — just tighten cross-links, not new
authorship.

- **Update `docs/architecture.md`** — add cross-links to the new
  Flipper-hardware and firmware-family docs at the top ("If you don't
  yet know what a Flipper Zero is, start with `flipper-hardware`.").
  Add one sentence to the FirmwareCompatibilityProfile paragraph
  linking `firmware-compatibility-profile.md`.
- **Update `docs/labs.md`** — the "Payload Types" table already lists
  extensions; add a cross-link to each Group C doc.
- **Update `README.md`** — replace the current one-line SubGHz / IR /
  NFC bullets with links to the new topic docs. Trim, don't grow.

## 4. Doc template

Every new topic doc uses this shape so `read_doc` returns consistent,
paraphraseable content:

```markdown
# {Title}

> One-sentence purpose of this doc. Optional context, one line.

## What it is

Brief factual definition. Chip part numbers, frequencies, protocol
names, RFC references. Roughly 3-8 sentences.

## How it works

Deeper mechanism. Physical layer, framing, timing, encoding. Where
the Vesper app surfaces it (one paragraph, not a section).

## Capabilities and limits

What you can do; what you can't. Firmware-family-dependent behavior
listed explicitly.

## Common tasks

3-6 short worked mini-scenarios ("capture then decode a Princeton
remote", "write an EM4100 to a T5577"). Each ~4-8 lines.

## Gotchas

Edge cases and failure modes we've seen. Buffer limits, region
locks, timing sensitivities.

## Legal & safety notes

1-3 lines. Links to `legal-and-safety.md`.

## See also

- `related-topic-a.md`
- `related-topic-b.md`

---
*Attribution:* Flipper-Devices docs, Momentum wiki, Marauder wiki,
or specific hardware datasheet — whichever applies. Include commit
hash or retrieval date when relevant.
```

Files that don't fit this shape (index READMEs, comparison matrices)
just say so at the top and use whatever structure serves them.

## 5. Discoverability & retrieval

Three concrete corpus-hygiene changes so the assistant can actually
find and rank this new material.

### 5.1 Extend `knowledge.py` to glob nested folders

Right now `_md_files()` only globs `docs/*.md` and the top-level
`README.md`. It needs to reach `docs/**/*.md` so `docs/skills/*.md`
is picked up.

Change (in `mcp-server/src/vesper_mcp/tools/knowledge.py`):

```python
def _md_files(root: Path) -> list[Path]:
    files: list[Path] = []
    docs = root / "docs"
    if docs.is_dir():
        files.extend(sorted(p for p in docs.rglob("*.md") if p.is_file()))
    readme = root / "README.md"
    if readme.is_file():
        files.append(readme)
    return files
```

Topic id derivation from a nested file becomes `parent-stem`, e.g.
`docs/skills/wifi-attack.md` → `skills-wifi-attack`. Update
`_topic_id`:

```python
def _topic_id(path: Path) -> str:
    # Path relative to root/docs (or root for README) with slashes → hyphens.
    rel = path.stem if path.parent.name in ("", ) else "-".join([*path.relative_to(path.parents[1]).parent.parts, path.stem])
    return rel.lower().replace("_", "-")
```

Tests in `mcp-server/tests/test_knowledge_tools.py` need one new case
that lays a `docs/skills/foo.md` in the fake corpus and asserts
`list_topics` returns `skills-foo`.

### 5.2 Symlink vs copy for the SKILL.md playbooks

Two options:

- **Option A (copy):** `docs/skills/wifi-attack.md` is a physical
  copy of `app/src/main/assets/skills/wifi-attack/SKILL.md`. Adds a
  drift risk, needs a CI check.
- **Option B (build-time gather):** a Gradle task (or a small Python
  script under `mcp-server/scripts/`) that reads
  `app/src/main/assets/skills/*/SKILL.md` and writes them into
  `docs/skills/*.md` before packaging the wheel. Ships one canonical
  source.

**Recommendation: Option B.** Add a `sync-skills` script invoked from
the existing `verifyExecuteCommandSchema`-adjacent Gradle plumbing and
from `.github/workflows/mcp-python.yml` (fail if `docs/skills/*.md`
is stale relative to `app/src/main/assets/skills/*/SKILL.md`). The
schema already has the same drift-prevention pattern so this is
familiar. Owner also cheaper on merges — no branch conflicts on
copy-pasted playbook edits.

Fallback: if the Gradle plumbing is too heavy for a first pass, ship
Option A with a hand-written README note that the app-side skill is
canonical, and add the sync script as a follow-up.

### 5.3 Corpus-wide index doc

Add `docs/index.md` — a hand-curated table of contents (topic id →
one-line purpose, grouped by A-H above). This is a *reading* aid; it
also gives `search_docs` a single place where every topic id appears
as a substring hit. Consuming clients can `read_doc("index")` before
they know what to look for.

## 6. Rollout — landing order

Follow the branch-per-slice / `--no-ff merge` pattern from `plan-mcp.md`
§10. Each slice below is one branch; merge in the order given so later
docs can cross-link earlier ones without dangling references.

### 6.1 `feature/knowledge-hardware-foundations`

Files added:
- `docs/flipper-hardware.md`
- `docs/flipper-gpio-pinout.md`
- `docs/flipper-storage.md`
- `docs/flipper-cli.md`
- `docs/index.md` (stub — expanded across later slices)

Green criteria: `list_topics()` returns the new topic ids; `read_doc`
succeeds for each; a substring search for "STM32WB55" hits
`flipper-hardware`; `ruff check .` and `pytest -q` still clean in
`mcp-server/`.

### 6.2 `feature/knowledge-firmware-families`

Files added:
- `docs/firmware-families.md`
- `docs/firmware-momentum.md` ⭐
- `docs/firmware-compatibility-profile.md`
- `docs/firmware-updating.md`

Update: append cross-links in `docs/architecture.md`
(FirmwareCompatibilityProfile section).

Green criteria: as above; substring search for "Momentum" now returns
the deep-dive doc first (by insertion order).

### 6.3 `feature/knowledge-rf-subsystems`

Files added:
- `docs/subghz-overview.md`
- `docs/subghz-protocols.md`
- `docs/signal-formats-sub.md`
- `docs/ir-overview.md`
- `docs/signal-formats-ir.md`
- `docs/nfc-overview.md`
- `docs/signal-formats-nfc.md`
- `docs/rfid-lf-overview.md`
- `docs/ibutton-overview.md`
- `docs/flipper-gpio-extensions.md`

Green criteria: same. Update `docs/index.md` with Group C entries.
Manual smoke: `read_doc("subghz-protocols")` returns something a
non-expert can paraphrase into "KeeLoq is a rolling code…".

### 6.4 `feature/knowledge-marauder` ⭐

Files added:
- `docs/marauder-overview.md`
- `docs/marauder-firmware.md`
- `docs/marauder-wiring.md`
- `docs/marauder-commands.md`

Update: `docs/labs.md` cross-links the new Marauder docs where WiFi is
mentioned; `docs/architecture.md` MarauderBridge paragraph gets a link.

Green criteria: substring search for "deauth" returns the wifi-attack
skill (once §6.6 lands) AND `marauder-commands.md`; search for "ESP32"
returns `marauder-overview.md` first.

### 6.5 `feature/knowledge-extension-and-dev`

Files added:
- `docs/flipper-fap-apps.md`
- `docs/flipper-firmware-build.md`
- `docs/flipper-js-runner.md`
- `docs/legal-and-safety.md`

Green criteria: every doc in Groups A-E now has a "Legal & safety notes"
block pointing to `legal-and-safety.md`.

### 6.6 `feature/knowledge-skills-gather`

Files added:
- `mcp-server/scripts/sync_skills.py` (or `buildSrc/` Gradle task, per §5.2)
- `docs/skills/README.md`
- `docs/skills/*.md` (seven files, generated by the sync)
- Update `mcp-server/src/vesper_mcp/tools/knowledge.py` to `rglob`
- Update `mcp-server/tests/test_knowledge_tools.py` — nested-topic test
- Update `.github/workflows/mcp-python.yml` — invoke the sync + fail on drift

Green criteria: `list_topics()` returns seven `skills-*` topics
alongside the top-level docs; the sync fails CI if run against a stale
`docs/skills/`.

### 6.7 `feature/knowledge-index-and-polish`

Final pass. Everything already lands; this slice tightens.

- Fully expand `docs/index.md` with every topic id + one-liner.
- Add `See also:` blocks to every existing top-level doc so cross-links
  are dense and searchable.
- Update `README.md` to point at `docs/index.md`.
- Trim `architecture.md` / `labs.md` where the new docs subsume detail.

Green criteria: `search_docs("marauder", 20)` returns hits across at
least three different topics; every top-level doc has at least two
`See also` links.

## 7. Volume estimate

Roughly 30 new markdown files, plus ~5 doc updates and one code change
in `tools/knowledge.py`. Target size per doc: 400-900 lines is too much
(chokes `read_doc` context); aim for **150-400 lines per top-level doc,
including code fences and tables.** Skills that land under
`docs/skills/` come in at their existing size (400-700 lines) — they
were written to be read whole.

Total corpus size after expansion: ~10-15k lines vs today's ~1.5k. That
fits well inside the wheel's `force-include` scope; no need to change
`plan-mcp.md` §2.

## 8. Explicit non-goals

Keep the scope honest.

- **Not a fap app catalog mirror.** `search_faphub` / `install_faphub_app`
  are execution actions on the Android side. The corpus documents the
  fap format and ecosystem, not individual apps.
- **Not a live spectrum tutorial.** Signal capture is a hardware
  activity; the corpus explains how to *reason about* a capture, not
  how to hold your antenna.
- **Not a legal opinion.** `legal-and-safety.md` is directional and
  cites jurisdictions; it does not give legal advice and should say so
  in its first sentence.
- **Not a MITM primer.** BLE recon methodology stays in
  `skill-ble-exploitation`; the corpus doesn't try to teach offense from
  first principles for platforms unrelated to Flipper.
- **Not a re-implementation of the Momentum wiki.** Cite it, don't
  clone it. Duplication rots; links + attribution let a reader chase
  the current source of truth.
- **Not a schema mirror.** `describe_action` already returns the arg
  block for every action. Do not narrate the JSON schema in Markdown.

## 9. Success criteria

The expansion succeeds when — with a fresh MCP client and no priors —
each of the following queries returns a paraphraseable, correct answer
purely from tool calls (no web fallback):

1. "What chip does the Flipper's SubGHz radio use, and what's its
   frequency range?"
2. "What's Momentum firmware and how does it differ from stock?"
3. "Which pins on the Flipper's GPIO header does the Marauder devboard
   use?"
4. "How do I forge a KeeLoq packet if I've captured two consecutive
   button presses?"
5. "Can the Flipper transmit on 868 MHz in the US, and what changes
   with Momentum?"
6. "What's the difference between a `.sub` file with `Protocol=Princeton`
   and one with `RAW_Data`?"
7. "How do I write a new `.fap` app and get it onto the Flipper?"
8. "What Marauder commands do I need to run a deauth against a specific
   BSSID on channel 6, and what's illegal about it?"

Every one of those questions is unanswerable from the current corpus.
Every one becomes answerable after slice 6.6 lands.
