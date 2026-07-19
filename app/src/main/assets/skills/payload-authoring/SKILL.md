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

### Author directly in chat when:

- The user asks for a one-off script during a chat session and doesn't need to iterate on it visually.
- The script is small (<50 lines of DuckyScript) and the requirements are clear.
- The engagement is in the middle of a campaign phase and stopping to open a UI would break flow.
- The user asks for a diff or validation, not authoring from scratch.

For direct authoring, the standard sequence is:

1. `badusb_generate(description="…", platform="windows")` — returns the script content.
2. If the user wants it saved: `badusb_write(filename="…", content=<script>, platform="windows")`. This re-validates before writing.
3. If the user then wants it to run: `badusb_execute(path="/ext/badusb/…")` — this is HIGH-risk and will pause for approval.

### Hand off to Payload Lab when:

- The user says "let me tweak this" or "let me see it" — Payload Lab is a visual editor with live validation.
- The payload is complex (multi-step attack, requires iterative refinement, has parameters the user wants to adjust).
- The user wants to compare multiple candidate payloads side by side.
- The engagement produced a Sub-GHz or IR file the user wants to modify visually (Payload Lab supports those types too, via `forge_payload`).

For hand-off, don't try to launch the UI yourself — just tell the user "open the Payload Lab (bottom nav) and I've included the requirements above" and stop. The Payload Lab is a Compose screen the user drives; you can't invoke it via `execute_command`.

### Author subghz/ir/nfc payloads

For non-BadUSB payloads, use `forge_payload` with `payload_type` set appropriately:

```
forge_payload(payload_type="subghz", prompt="Replay this garage remote…")
forge_payload(payload_type="ir", prompt="Samsung TV volume down button…")
forge_payload(payload_type="nfc", prompt="…")
```

The result comes back as text; the user can save it via `write_file` or push it via `push_artifact` if it needs to become a binary.

## The Evil Portal case

Evil Portal HTML has one gotcha: the ESP32 Marauder-side deployment path isn't yet wired (see `plan-deferred-wifi-marauder.md`). Authoring works via `PayloadEngine`, but the "deploy" step needs the WiFi Marauder companion-app foundation.

Until then:

- Generate portal HTML via `forge_payload(payload_type="html", prompt="Corporate SSO clone…")` — or use the Payload Lab's Evil Portal tab for a UI-driven flow.
- Save the resulting HTML with `write_file(path="/ext/evilportal/…", content=<html>)`.
- Tell the user the deployment step is pending.

## Rules of thumb

1. **Validate before write.** `badusb_write` does this automatically, but if you're chaining `badusb_generate` → user-review → save via `write_file`, run `badusb_validate` first.
2. **Never propose `badusb_execute` unprompted.** The user should always ask for execution explicitly. If they generated a script, offer to save it; don't offer to run it.
3. **Keep scripts small.** DuckyScript is easier to debug in short blocks; if a payload needs to do 10 things, split it into stages the user can approve independently.
4. **Platform matters.** `platform="windows"` vs `platform="macos"` vs `platform="linux"` changes the keyboard shortcut set (`GUI r` vs `GUI SPACE`, etc.). Ask if unclear.
5. **Record the finding.** After a successful execution during an engagement, `vuln_submit` the demonstrated capability (`vuln_type="hid_injection"` or similar) so it lands in the report.
