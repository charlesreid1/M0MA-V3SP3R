# mcp-server — FastMCP server for M0MA-V3SP3R

Python FastMCP server that exposes the M0MA-V3SP3R knowledge corpus and
the Flipper Zero `execute_command` schema over the Model Context
Protocol. Supports `stdio`, `sse`, and `streamable-http` transports.

**Corpus scope (as of `feature/expand-knowledge`):** 41 topics covering
the Flipper Zero platform (hardware, GPIO, storage, CLI), firmware
families (Official / Momentum / Unleashed / RogueMaster), RF subsystems
(SubGHz, IR, NFC, LF RFID, iButton), the WiFi Marauder devboard, app
and firmware development, legal/safety, and seven methodology
playbooks. Start at [`../docs/index.md`](../docs/index.md).

See [`../plan-mcp.md`](../plan-mcp.md) for the MCP-server plan and
[`../plan-knowledge-expand.md`](../plan-knowledge-expand.md) for how
the corpus was built up.

## Bootstrap

```bash
cd mcp-server
python3.11 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -e '.[dev]'
```

That puts an entry point at `.venv/bin/vesper-mcp`.

## Run

```bash
# stdio (Claude Desktop / opencode default)
vesper-mcp --transport stdio

# SSE
vesper-mcp --transport sse --port 8765

# streamable-http
vesper-mcp --transport streamable-http --port 8765
```

`--host` / `--port` are honored only for `sse` / `streamable-http`; on
`stdio` they are silently ignored.

## Env vars

| Variable                 | Default | Meaning                                    |
| ------------------------ | ------- | ------------------------------------------ |
| `VESPER_MCP_KNOWLEDGE`   | (auto)  | Override corpus root; must contain `docs/` |
| `VESPER_MCP_LOG_LEVEL`   | `INFO`  | Stderr log level (DEBUG/INFO/WARNING/ERROR) |

Transport / host / port are CLI-only on purpose.

## Corpus layout

The knowledge server discovers Markdown docs by globbing recursively:

- `docs/**/*.md` — every doc under `docs/` becomes a topic. Topic ids
  are derived as `parent-stem`: `docs/subghz-overview.md` →
  `subghz-overview`, `docs/skills/wifi-attack.md` → `skills-wifi-attack`.
- `README.md` (repo root) — topic id `readme`.

Underscores and hyphens are interchangeable; `read_doc("app_build_process")`
and `read_doc("app-build-process")` return the same file.

### `docs/skills/` — synced playbooks

The seven files under `docs/skills/*.md` are **generated copies** of
the canonical `SKILL.md` playbooks under
`app/src/main/assets/skills/<name>/SKILL.md`. The Android app's
`SkillRegistry` loads the originals directly; the MCP server exposes
the copies. Regenerate with:

```bash
python scripts/sync_skills.py           # write
python scripts/sync_skills.py --check   # verify (used by CI)
```

CI (`.github/workflows/mcp-python.yml`) runs the `--check` mode before
lint and fails the build on drift.

## Test

```bash
python scripts/sync_skills.py --check
ruff check .
pytest -q
```
