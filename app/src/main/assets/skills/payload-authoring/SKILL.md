---
name: payload-authoring
description: "When to author BadUSB / Evil Portal payloads directly vs handing off to the Payload Lab UI on M0MA-V3SP3R"
---

# Payload Authoring on M0MA-V3SP3R

M0MA-V3SP3R has two paths for authoring BadUSB scripts and Evil Portal HTML: (a) the tools you can call directly from chat, and (b) the Payload Lab screen the user can drive from the bottom-nav. Both use the same underlying `PayloadEngine` — the choice is about who's driving.

Load this skill whenever the user says "make me a BadUSB script", "write an evil portal", "generate a payload", or you decide on your own that authoring a payload is the next step in an engagement.

## Available tool actions

| Action | Purpose | Risk | Notes |
|--------|---------|------|-------|
| `badusb_generate` | Author a DuckyScript from a natural-language description | MEDIUM | LLM-authored + validated in one pipeline; no device effect until a subsequent `badusb_write` |
| `badusb_validate` | Lint an existing DuckyScript blob | LOW | Returns cleaned script + errors/warnings/optimizations |
| `badusb_diff` | Show a unified diff between an existing script on the Flipper and a proposed replacement | LOW | Reads the target path via the Flipper file system, then diffs |
| `badusb_write` | Save a script to `/ext/badusb/<name>.txt`, re-validating first | MEDIUM | Refuses to write if validation returns errors |
| `badusb_execute` | Run a script already on the Flipper via `badusb run <path>` | HIGH | Actual keystroke injection — user confirmation required |
| `forge_payload` | The fork's original general-purpose payload generator (supports `payload_type` = `subghz` / `ir` / `badusb` / `nfc`) | MEDIUM | Useful for the non-BadUSB payload types (Sub-GHz, IR, NFC) |

## Decision: direct vs Payload Lab

Compute an `authoring_score` from the user's request. This removes the judgement call about whether a payload is "simple enough" for direct authoring.

```
authoring_score = 0
+30  if estimated script_length_lines < 20
+20  if platform is explicitly specified (windows / macos / linux)
+15  if user provided target application, URL, or keystroke sequence verbatim
+10  if this is a follow-up (diff, validate, or minor edit to an existing script)
+ 5  if the engagement is mid-campaign and pausing to open UI would break flow
-25  if user said "let me tweak", "let me see", "iterate", or "show me"
-20  if payload_type ∈ {evil_portal, multi_stage_badusb}
-15  if script requires ≥2 platform variants (win + mac, etc.)
-10  if payload has runtime parameters the user will vary (URLs, usernames, timing)
-10  if the user wants to compare multiple candidate payloads side by side
```

**Interpretation bands:**
- `score ≥ 40` — author directly in chat
- `score 10–39` — author directly, offer Payload Lab as follow-up for refinement
- `score < 10` — hand off to Payload Lab

### Direct authoring sequence (score ≥ 10)

1. `badusb_generate(description="…", platform="windows")` — returns the script content.
2. If the user wants it saved: `badusb_write(filename="…", content=<script>, platform="windows")`. This re-validates before writing.
3. If the user then wants it to run: `badusb_execute(path="/ext/badusb/…")` — HIGH-risk, pauses for approval.

### Payload Lab hand-off (score < 10)

Don't try to launch the UI yourself. Tell the user "open the Payload Lab (bottom nav) and I've included the requirements above" and stop. The Payload Lab is a Compose screen the user drives; you can't invoke it via `execute_command`.

## Payload-type dispatch

Match the user's request against these rules to pick the right tool and output path. Each match rule is a boolean check the AI runs before choosing a tool.

| Payload type | Trigger phrases | Tool | Output path | Risk | Match rule |
|--------------|-----------------|------|-------------|------|------------|
| BadUSB DuckyScript | "keystroke", "type", "run a command", "HID", "USB Rubber Ducky" | `badusb_generate` | `/ext/badusb/*.txt` | MEDIUM | user names a target app OR keystroke sequence AND platform ∈ {windows, macos, linux} |
| Sub-GHz replay | "replay", "capture", "garage", "remote", "433 MHz", "sub-ghz" | `forge_payload(subghz)` | `/ext/subghz/*.sub` | MEDIUM | prior Sub-GHz capture exists in session AND static code confirmed (two identical captures of the same button press) |
| IR replay | "TV remote", "IR blast", "38 kHz", "infrared" | `forge_payload(ir)` | `/ext/infrared/*.ir` | MEDIUM | protocol identified ∈ {NEC, RC5, RC6, SIRC, Samsung32} (see IR protocol reference below) |
| NFC clone | "clone card", "MIFARE", "NTAG", "NFC tag" | `forge_payload(nfc)` | `/ext/nfc/*.nfc` | MEDIUM | UID captured AND (sector reads captured OR tag is NTAG21x) |
| Evil Portal | "captive portal", "phishing page", "SSO clone", "fake login" | `forge_payload(html)` | `/ext/evilportal/*.html` | HIGH | WiFi Marauder companion available — **currently NO; deployment path not yet wired**, authoring works but the "deploy" step is pending |

**IR protocol quick reference** (for the IR match rule):

| Protocol | Carrier | Leader burst | Leader space | Bit count |
|----------|---------|--------------|--------------|-----------|
| NEC | 38 kHz | 9 ms | 4.5 ms | 32 |
| RC5 (Philips) | 36 kHz | — (Manchester) | — | 14 |
| RC6 (Philips) | 36 kHz | 2.666 ms | 889 μs | 16–32 |
| Samsung32 | 38 kHz | 4.5 ms | 4.5 ms | 32 |
| SIRC (Sony) | 40 kHz | 2.4 ms | 600 μs | 12 / 15 / 20 |

**Evil Portal note:** authoring works via `PayloadEngine` today; deployment via ESP32 Marauder is not yet implemented. When the user requests an Evil Portal:
- Generate HTML via `forge_payload(payload_type="html", prompt="Corporate SSO clone…")` or the Payload Lab's Evil Portal tab.
- Save with `write_file(path="/ext/evilportal/…", content=<html>)`.
- Explicitly tell the user the deployment step is pending.

For non-BadUSB payloads generally, `forge_payload` returns text; the user saves it via `write_file` or pushes as binary via `push_artifact`.

## DuckyScript validation rules

Every generated script must satisfy these thresholds before `badusb_write`. `badusb_validate` enforces most of them; the remainder are AI-side checks before calling `badusb_generate` again.

| Property | Threshold | Rationale |
|----------|-----------|-----------|
| Max total lines | 150 | Beyond this, split into stages the user can approve independently |
| Max total content size | 32 KB | Flipper filesystem practical limit for `/ext/badusb/*.txt` |
| Max `DELAY` value | 10000 ms | Longer delays suggest bad timing design; use `WAIT_FOR_BUTTON_PRESS` instead |
| Min `DELAY` at script start (cold enum) | 1000 ms | HID enumeration on host — shorter drops the opening keystrokes |
| Min `DELAY` after `GUI r` (Windows Run) | 500 ms | Run dialog open latency |
| Min `DELAY` after `GUI SPACE` (macOS Spotlight) | 800 ms | Spotlight index warm-up |
| Min `DELAY` after `CTRL ALT t` (Linux terminal) | 700 ms | GNOME Terminal / Konsole spawn |
| Min `DELAY` after `ENTER` that opens an app | 1500 ms | App cold-start budget before next keystroke lands |
| Min `DELAY` between `STRING` and following `ENTER` | 50 ms | HID buffer flush |
| Max consecutive characters in one `STRING` | 4096 | HID buffer risk on some targets; split into multiple `STRING` lines |
| Required Windows opener | `DELAY 1000` then `GUI r` | Cold-boot HID enumeration + Run dialog |
| Required macOS opener | `DELAY 2000` then `GUI SPACE` | HID + Spotlight index warm-up |
| Required Linux opener | `DELAY 1000` then `CTRL ALT t` | HID + terminal spawn (GNOME/KDE defaults) |

### Per-platform keystroke reference

| Action | Windows | macOS | Linux |
|--------|---------|-------|-------|
| Open command runner | `GUI r` | `GUI SPACE` | `CTRL ALT t` |
| Elevate to admin | `CTRL SHIFT ENTER` (after typing cmd) | prefix command with `sudo ` | prefix command with `sudo ` |
| Copy | `CTRL c` | `GUI c` | `CTRL c` |
| Paste | `CTRL v` | `GUI v` | `CTRL v` (or `CTRL SHIFT v` in terminal) |
| Switch app | `ALT TAB` | `GUI TAB` | `ALT TAB` |
| Close window | `ALT F4` | `GUI q` | `ALT F4` |
| Lock screen | `GUI l` | `CTRL GUI q` | `CTRL ALT l` (GNOME) |

## Abort and rework conditions

### After `badusb_generate`
- Validation errors > 0 after 2 regenerate attempts → hand off to Payload Lab
- Script length > 150 lines → split into stages; generate each stage separately
- Contains prohibited commands without explicit user intent → refuse and confirm:
  - Windows: `format`, `del /f /s /q C:`, `shutdown /s`, `rd /s /q C:\`
  - macOS/Linux: `rm -rf /`, `rm -rf ~`, `dd if=/dev/zero of=/dev/sd*`, `mkfs.*`, `shutdown -h now` (unprompted)
- References file paths or URLs not confirmed to exist on the target → downgrade to "template" and flag for user review

### After `badusb_write`
- Validation regressed between generate and write (>0 new errors) → do not write; re-run `badusb_generate`
- Filename collides with existing file at `/ext/badusb/` → require explicit overwrite confirmation
- Content length > 32 KB → refuse (Flipper filesystem limit)

### Before proposing `badusb_execute`
- User has not explicitly asked to run → do not propose
- Script was authored > 1 conversation turn ago → re-run `badusb_validate` first
- Target platform differs from last-known Flipper HID config → warn and stop
- Rate limit: **max 1 `badusb_execute` per 30 s** during an engagement, to allow the observation window for effect verification

## Rules of thumb

1. **Validate before write.** `badusb_write` does this automatically, but if you're chaining `badusb_generate` → user-review → save via `write_file`, run `badusb_validate` first.
2. **Never propose `badusb_execute` unprompted.** The user should always ask for execution explicitly. If they generated a script, offer to save it; don't offer to run it.
3. **Keep scripts small.** DuckyScript is easier to debug in short blocks; if a payload needs to do 10 things, split it into stages the user can approve independently.
4. **Platform matters.** `platform="windows"` vs `platform="macos"` vs `platform="linux"` changes the keyboard shortcut set (`GUI r` vs `GUI SPACE`, etc.). Ask if unclear.
5. **Record the finding.** After a successful execution during an engagement, `vuln_submit` the demonstrated capability (`vuln_type="hid_injection"` or similar) so it lands in the report.
