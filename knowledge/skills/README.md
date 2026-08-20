# Skill playbooks

> These are the seven methodology playbooks the Vesper Android app's
> `SkillRegistry` loads on demand. Copies live here so the MCP corpus
> can expose them via `list_topics()` / `read_doc("skills", name)`.

## What this directory is

The files in this directory are **generated copies** of the canonical
`SKILL.md` playbooks under `app/src/main/assets/skills/<name>/SKILL.md`.
The Android app loads the originals directly; the MCP knowledge server
loads these copies as topic `skills` (see
`mcp-server/src/vesper_mcp/tools/knowledge.py`).

Do not edit these files directly. Edit the source under
`app/src/main/assets/skills/<name>/SKILL.md` and re-run
`mcp-server/scripts/sync_skills.py`. CI enforces this: the
`mcp-python.yml` workflow runs the sync in `--check` mode and fails
the build if it detects drift.

## The seven skills

Each appears as a name under topic `skills` — reach it with
`read_doc("skills", "<name>")` or the resource
`vesper://skills/<name>`.

| Name                  | What it's for                                                        |
|-----------------------|----------------------------------------------------------------------|
| `ble-exploitation`    | BLE reconnaissance, spoofing, spam methodology.                       |
| `campaign`            | Autonomous multi-step campaign planning + execution.                  |
| `payload-authoring`   | BadUSB / DuckyScript payload authoring.                               |
| `pentest-report`      | Structured pentest write-ups.                                         |
| `protocol-analysis`   | Protocol reversing methodology.                                       |
| `signal-analysis`     | RF signal capture + decoding methodology.                             |
| `wifi-attack`         | Wi-Fi reconnaissance + Marauder attack methodology.                   |

## When a caller should request one

- **`skills/signal-analysis`** — user is capturing an unknown RF
  signal and wants to reason about the waveform, modulation, or
  protocol. Feeds `subghz/README` and `subghz/protocols`.
- **`skills/wifi-attack`** — user is working with a Marauder
  devboard. Feeds `marauder/README` and `marauder/commands`.
- **`skills/ble-exploitation`** — BLE recon / advertisement spam /
  paired-device exploitation.
- **`skills/payload-authoring`** — writing a BadUSB payload. Feeds
  `development/fap-apps` (BadUSB section) and `flipper-storage`
  (`/ext/badusb/`).
- **`skills/protocol-analysis`** — reverse-engineering an unknown
  protocol from captured samples.
- **`skills/campaign`** — orchestrating a multi-step Ralph-driven
  session. Feeds `vesper/campaigns`.
- **`skills/pentest-report`** — structuring findings for a report.

## See also

- `vesper/architecture` — where `SkillRegistry` sits in the app
  pipeline.
- `vesper/campaigns` — the higher-level UX these methodologies plug
  into.
- `vesper/mcp` — how MCP clients reach these via `read_doc`.
