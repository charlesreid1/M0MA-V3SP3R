# Skill playbooks

> These are the seven methodology playbooks the Vesper Android app's
> `SkillRegistry` loads on demand. Copies live here so the MCP corpus
> can expose them via `list_topics()`.

## What this directory is

The files in this directory are **generated copies** of the canonical
`SKILL.md` playbooks under `app/src/main/assets/skills/<name>/SKILL.md`.
The Android app loads the originals directly; the MCP knowledge server
loads these copies via `docs/**/*.md` globbing (see
`mcp-server/src/vesper_mcp/tools/knowledge.py`).

Do not edit these files directly. Edit the source under
`app/src/main/assets/skills/<name>/SKILL.md` and re-run
`mcp-server/scripts/sync_skills.py`. CI enforces this: the
`mcp-python.yml` workflow runs the sync in `--check` mode and fails
the build if it detects drift.

## The seven skills

Under `list_topics()`, each appears as `skills-<name>` (e.g.
`skills-wifi-attack`).

| Topic id                  | What it's for                                                        |
|---------------------------|----------------------------------------------------------------------|
| `skills-ble-exploitation` | BLE reconnaissance, spoofing, spam methodology.                       |
| `skills-campaign`         | Autonomous multi-step campaign planning + execution.                  |
| `skills-payload-authoring`| BadUSB / DuckyScript payload authoring.                               |
| `skills-pentest-report`   | Structured pentest write-ups.                                         |
| `skills-protocol-analysis`| Protocol reversing methodology.                                       |
| `skills-signal-analysis`  | RF signal capture + decoding methodology.                             |
| `skills-wifi-attack`      | Wi-Fi reconnaissance + Marauder attack methodology.                   |

## When a caller should request one

- **`skills-signal-analysis`** — user is capturing an unknown RF signal
  and wants to reason about the waveform, modulation, or protocol.
  Feeds `subghz-overview.md` and `subghz-protocols.md`.
- **`skills-wifi-attack`** — user is working with a Marauder devboard.
  Feeds `marauder-overview.md` and `marauder-commands.md`.
- **`skills-ble-exploitation`** — BLE recon / advertisement spam /
  paired-device exploitation.
- **`skills-payload-authoring`** — writing a BadUSB payload. Feeds
  `flipper-fap-apps.md` (BadUSB section) and `flipper-storage.md`
  (`/ext/badusb/`).
- **`skills-protocol-analysis`** — reverse-engineering an unknown
  protocol from captured samples.
- **`skills-campaign`** — orchestrating a multi-step Ralph-driven
  session. Feeds `campaigns.md`.
- **`skills-pentest-report`** — structuring findings for a report.

## See also

- `architecture.md` — where `SkillRegistry` sits in the app pipeline.
- `campaigns.md` — the higher-level UX these methodologies plug into.
- `mcp.md` — how MCP clients reach these via `read_doc`.
