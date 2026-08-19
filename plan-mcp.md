# plan-mcp.md — Add a FastMCP server to M0MA-V3SP3R

Goal: bolt a Python FastMCP server onto this repo alongside the existing
Android app and the Node `mentra-bridge/`. Match the wiring idiom of
H4CKRF-6H05T, P1N3NUT5, and PHR34CKER5, and support all three transports the
user asked for: `stdio`, `sse`, `streamable-http`.

## 0. Cross-repo comparison (the shared pattern to match)

All three reference repos converge on the same FastMCP wiring — the only
non-trivial variation is how tools get registered. Everything else is copy-and-paste.

| Concern              | H4CKRF-6H05T                          | P1N3NUT5                       | PHR34CKER5                      |
| -------------------- | ------------------------------------- | ------------------------------ | ------------------------------- |
| SDK                  | `mcp>=1.2.0,<2`                       | `mcp>=1.2.0,<2`                | `mcp>=1.2.0,<2`                 |
| Import path          | `from mcp.server.fastmcp import FastMCP` | same                        | same                            |
| Console script       | `hackrf-agent-mcp`                    | `p1n3nut5-mcp`                 | `phr34cker5-mcp`                |
| Entry chain          | `__main__:main` → `server.main`       | same                           | same                            |
| Transport flag       | argparse `--transport` (choices)      | same                           | same                            |
| Transport choices    | `stdio`, `sse`, `streamable-http`     | `stdio`, `sse`, `streamable-http` | `stdio`, `sse`, `streamable-http` |
| Default transport    | `stdio`                               | `stdio`                        | `stdio`                         |
| Host/port flags      | `--host`, `--port`                    | same                           | same                            |
| Host/port apply      | mutate `app.settings.host`/`port`     | same                           | same                            |
| Dispatch call        | `await app.run_{stdio,sse,streamable_http}_async()` | `app.run(transport=…)` | `app.run(transport=…)` |
| Env var for transport| none                                  | none                           | none                            |
| Tool registration    | **enum×Pydantic factory** → loop      | tuple → loop                   | inline `@mcp.tool()` in server.py |
| Stderr logging setup | yes                                   | no                             | no                              |
| Lifespan/teardown    | manual `_boot_deps()`+`teardown()`    | none                           | none                            |
| Signal handlers      | yes (SIGINT/SIGTERM)                  | no                             | no                              |
| `.mcp.json`          | plain command                         | absent                         | bash-sources `.env` first       |

### The canonical `main()` (PHR34CKER5 / P1N3NUT5 form — simplest, all three transports)

```python
def main() -> None:
    parser = argparse.ArgumentParser(prog="vesper-mcp")
    parser.add_argument("--transport",
        choices=["stdio", "sse", "streamable-http"], default="stdio",
        help="MCP transport. 'stdio' is what Claude Desktop / opencode use; "
             "'sse' and 'streamable-http' expose an HTTP server for remote clients.")
    parser.add_argument("--host", default=None,
        help="Bind host for sse / streamable-http (default: 127.0.0.1).")
    parser.add_argument("--port", type=int, default=None,
        help="Bind port for sse / streamable-http (default: 8000).")
    args = parser.parse_args()

    if args.transport in ("sse", "streamable-http"):
        if args.host is not None: app.settings.host = args.host
        if args.port is not None: app.settings.port = args.port

    app.run(transport=args.transport)
```

H4CKRF-6H05T's variant wraps the same three calls but does the dispatch
explicitly because it needs an async context around them for the lifespan
teardown:

```python
async def _run() -> None:
    deps, teardown = await _boot_deps(...)
    try:
        app = _build_app(deps)
        if args.transport in ("sse", "streamable-http"):
            if args.host is not None: app.settings.host = args.host
            if args.port is not None: app.settings.port = args.port
        if args.transport == "stdio":
            await app.run_stdio_async()
        elif args.transport == "sse":
            await app.run_sse_async()
        else:
            await app.run_streamable_http_async()
    finally:
        await teardown()
```

**Decision for M0MA-V3SP3R:** start with the PHR34CKER5/P1N3NUT5 form
(`app.run(transport=…)`). We can migrate to the explicit-async form later if
we need lifespan teardown (e.g. once the bridge to the phone is up and we're
holding an ADB or WebSocket connection).

## 1. Where the Python subproject lives

M0MA-V3SP3R has no Python today — clean slate. Repo root is a Kotlin/Gradle
build root, so a top-level `pyproject.toml` would collide. Follow the
`mentra-bridge/` sibling-subproject convention:

```
M0MA-V3SP3R/
├── app/                         # Android (unchanged)
├── mentra-bridge/               # Node bridge (unchanged)
├── mcp-server/                  # NEW — Python FastMCP server
│   ├── pyproject.toml
│   ├── .env.example
│   ├── README.md
│   ├── src/vesper_mcp/
│   │   ├── __init__.py          # __version__ only
│   │   ├── __main__.py          # 4-line delegate to server.main
│   │   ├── server.py            # FastMCP + main() + tool registration
│   │   ├── logging_config.py    # stderr-only (H4CKRF idiom)
│   │   ├── runtime.py           # env/config + envelope helper
│   │   ├── tools/
│   │   │   ├── __init__.py
│   │   │   ├── knowledge.py     # docs/, campaigns.md, labs.md, architecture.md
│   │   │   ├── schema.py        # execute_command_schema.json → per-action tool
│   │   │   ├── faphub.py        # (later) search_faphub, install_faphub_app
│   │   │   ├── github.py        # (later) github_search, browse_repo
│   │   │   └── bridge.py        # (later) proxy to Android app
│   │   └── resources.py         # vesper://... URIs
│   └── tests/
│       ├── test_server_boot.py
│       ├── test_transport_dispatch.py
│       └── test_knowledge_tools.py
├── .mcp.json                    # NEW — Claude Desktop / opencode
└── .claude/                     # NEW (optional; can add later)
```

Package name: `vesper_mcp` (matches app namespace `com.vesper.flipper`).
Console script: `vesper-mcp`.

## 2. `pyproject.toml`

Hatchling backend, `mcp>=1.2.0,<2` pin (same as all three references —
`mcp.server.fastmcp` was dropped in mcp 2.0), force-include the repo `docs/`
so `vesper-mcp` works when installed from a built wheel.

```toml
[project]
name = "vesper-mcp"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    # mcp 2.0 dropped mcp.server.fastmcp; pin <2. Same rationale as
    # H4CKRF-6H05T, P1N3NUT5, PHR34CKER5.
    "mcp>=1.2.0,<2",
    "httpx>=0.27.0",
    "pydantic>=2.6",
]

[project.optional-dependencies]
dev = ["pytest>=8", "pytest-asyncio>=0.23", "ruff>=0.5"]

[project.scripts]
vesper-mcp = "vesper_mcp.__main__:main"

[build-system]
requires = ["hatchling>=1.24"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/vesper_mcp"]

[tool.hatch.build.targets.wheel.force-include]
"../docs" = "vesper_mcp/_knowledge/docs"
"../README.md" = "vesper_mcp/_knowledge/README.md"
```

## 3. `server.py` — FastMCP instance + `main()`

```python
# src/vesper_mcp/server.py
from __future__ import annotations
import argparse, os
from mcp.server.fastmcp import FastMCP

from .logging_config import configure_logging
from .tools import knowledge, schema

mcp = FastMCP(
    name="vesper",
    instructions=(
        "M0MA-V3SP3R — Flipper Zero campaign runbook + on-device tool "
        "catalog. Use list_topics/read_doc for corpus access, "
        "list_actions/describe_action to inspect the execute_command "
        "schema exposed by the Android app."
    ),
)

# Registration loop (P1N3NUT5 idiom). Keeps leaf modules importable without
# `mcp` on-path for tests.
_TOOLS = (
    knowledge.list_topics,
    knowledge.read_doc,
    knowledge.search_docs,
    schema.list_actions,
    schema.describe_action,
)


def main() -> None:
    parser = argparse.ArgumentParser(prog="vesper-mcp")
    parser.add_argument("--transport",
        choices=["stdio", "sse", "streamable-http"], default="stdio",
        help="MCP transport. 'stdio' is what Claude Desktop / opencode use; "
             "'sse' and 'streamable-http' expose an HTTP server for remote clients.")
    parser.add_argument("--host", default=None,
        help="Bind host for sse / streamable-http transports (default: 127.0.0.1).")
    parser.add_argument("--port", type=int, default=None,
        help="Bind port for sse / streamable-http transports (default: 8000).")
    parser.add_argument("--knowledge",
        help="Override corpus root (also readable from $VESPER_MCP_KNOWLEDGE).")
    args = parser.parse_args()

    configure_logging()

    if args.knowledge:
        os.environ["VESPER_MCP_KNOWLEDGE"] = args.knowledge

    for tool in _TOOLS:
        mcp.tool()(tool)

    if args.transport in ("sse", "streamable-http"):
        if args.host is not None: mcp.settings.host = args.host
        if args.port is not None: mcp.settings.port = args.port

    mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
```

Line-for-line the PHR34CKER5 `main()` plus the P1N3NUT5 registration loop.

## 4. `logging_config.py` — stderr-only

Neither P1N3NUT5 nor PHR34CKER5 configures logging; H4CKRF-6H05T does, and
it's the right call — a stray stdout write corrupts the JSON-RPC frame on
`stdio` transport. Copy the H4CKRF pattern:

```python
import logging, os, sys

def configure_logging() -> None:
    level = os.environ.get("VESPER_MCP_LOG_LEVEL", "INFO").upper()
    root = logging.getLogger()
    for h in list(root.handlers):
        root.removeHandler(h)
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(logging.Formatter(
        "%(asctime)s %(levelname)s %(name)s %(message)s"))
    root.addHandler(handler)
    root.setLevel(level)
    logging.captureWarnings(True)
```

## 5. Tool organization

Follow P1N3NUT5: leaves are plain `async def` functions, `server.py`
imports them and registers via loop. **Do not** decorate with `@mcp.tool()`
inside leaves — keeps them import-safe for tests without `mcp` installed.

MVP tools (no phone hardware required):

- `knowledge.list_topics()` — enumerate corpus docs (`architecture`,
  `campaigns`, `labs`, `app_build_process`, `README`).
- `knowledge.read_doc(topic: str)` — return one doc body.
- `knowledge.search_docs(query: str)` — substring search across corpus.
- `schema.list_actions()` — the ~60 `execute_command` actions from
  `docs/execute_command_schema.json` (auto-generated from Kotlin).
- `schema.describe_action(action: str)` — one action's params + risk tier.

Later, per §9.

### Beyond MVP: enum-driven registration (steal from H4CKRF-6H05T)

Once we're ready to bridge to the app, the `execute_command_schema.json`
becomes exactly the same shape as H4CKRF-6H05T's `CommandAction` enum. Reuse
their pattern: for each action, build an MCP tool wrapper with typed
kwargs derived from the schema, prepending `justification` and
`expected_effect` params (same as H4CKRF's tool factory,
`src/hackrf_agent/mcp/server.py:81-194`). That way the ~60 tools stay in
sync with the app automatically.

## 6. Resources — `vesper://…` URI scheme

Match PHR34CKER5's inline decorator style (one file, low volume):

- `vesper://docs/index` — list of doc topics
- `vesper://docs/{topic}` — doc body
- `vesper://schema/actions` — full action catalog JSON

## 7. Config artifacts

- `mcp-server/.env.example` — documents `VESPER_MCP_KNOWLEDGE`,
  `VESPER_MCP_LOG_LEVEL`. **Not** `VESPER_MCP_TRANSPORT` / `_HOST` / `_PORT`
  — the three references intentionally leave transport CLI-only, and we
  should match. (If a user wants a remote server they'll set up a proper
  wrapper unit; env-var confusion isn't worth it.)
- `.mcp.json` at repo root — PHR34CKER5's bash-sources-`.env` trick,
  pointed at the sibling venv:
  ```json
  {"mcpServers": {"vesper": {
    "command": "bash",
    "args": ["-c",
      "[ -f \"$PWD/mcp-server/.env\" ] && . \"$PWD/mcp-server/.env\"; exec \"$PWD/mcp-server/.venv/bin/vesper-mcp\""]
  }}}
  ```

## 8. Tests

`mcp-server/tests/`, pytest + pytest-asyncio.

- `test_server_boot.py` — import `server`, assert `mcp.name == "vesper"`;
  parse `--help` and confirm all three transports appear in the choices
  (regression against picking a wrong mcp pin that drops streamable-http).
- `test_transport_dispatch.py` — monkeypatch `mcp.run`, run `main()` with
  each `--transport` value, assert it's called with the right kwarg AND
  that `mcp.settings.host` / `port` are only mutated for `sse` /
  `streamable-http`.
- `test_knowledge_tools.py` — point `VESPER_MCP_KNOWLEDGE` at a fixture
  dir and exercise `list_topics`, `read_doc`, `search_docs`.

CI: add `.github/workflows/mcp-python.yml` running `pytest` + `ruff check`
scoped to `mcp-server/`. Repo has zero CI today — this is additive and only
gates the new subproject.

## 9. Later phases (deferred to their own branches)

1. **App bridge.** Decide the channel: ADB `tcp:` forward to a small HTTP
   listener the app already runs, or a WebSocket like `mentra-bridge/`.
   Once picked, `tools/bridge.py` wraps `execute_command` so each schema
   action becomes an MCP tool. Reuse H4CKRF-6H05T's factory pattern —
   iterate the schema, generate one wrapper per action.
2. **Risk-tier passthrough + elicitation.** App is authoritative on
   LOW/MEDIUM/HIGH/BLOCKED. On HIGH-tier tools, surface the confirmation
   through `session.elicit(...)` (H4CKRF pattern, `approval_port.py:74-89`).
3. **Lifespan teardown.** Once we hold long-lived connections (bridge or
   audit stream), switch `main()` to the H4CKRF explicit-async form so we
   have a proper `try/finally` teardown around
   `run_stdio_async` / `run_sse_async` / `run_streamable_http_async`.
4. **Audit resource.** `vesper://audit/{session}/events` from the app's
   audit log.
5. **Signal handling.** Copy H4CKRF's two-level SIGINT (`server.py:434-440`)
   once we have in-flight tool tasks worth cancelling.

## 10. Landing order — branches, not PRs

Workflow: cut a topic branch off `main` for each slice, work it to green,
then `git checkout main && git merge --no-ff <branch>` to land. The `--no-ff`
keeps the branch shape visible in `git log --graph` so the slices stay
legible after the fact. Delete the branch after merge; no PR review round.

- [ ] **`feature/mcp-bootstrap`** — `mcp-server/pyproject.toml`, empty
      `src/vesper_mcp/{__init__,__main__}.py`, `.env.example`, `README.md`,
      `.gitignore` amend. Package builds; no `mcp` import yet.
      Merge: `git merge --no-ff feature/mcp-bootstrap`.
- [ ] **`feature/mcp-transports`** — `server.py` with the PHR34CKER5-form
      `main()`, `logging_config.py`, one `ping` tool. Manual smoke:
      - `vesper-mcp --transport stdio` + a stdio client (`mcp` CLI)
      - `vesper-mcp --transport sse --port 8765` + `curl http://127.0.0.1:8765/sse`
      - `vesper-mcp --transport streamable-http --port 8765` + curl
        `http://127.0.0.1:8765/mcp` with the MCP initialize handshake.
      Boot + transport-dispatch tests. Merge: `git merge --no-ff feature/mcp-transports`.
- [ ] **`feature/mcp-tools-knowledge`** — `tools/knowledge.py`,
      `tools/schema.py`, `resources.py`, tests.
      Merge: `git merge --no-ff feature/mcp-tools-knowledge`.
- [ ] **`feature/mcp-client-wiring`** — `.mcp.json` at repo root,
      `docs/mcp.md` with setup snippets for Claude Desktop, opencode,
      Cursor, and raw curl for each HTTP transport.
      Merge: `git merge --no-ff feature/mcp-client-wiring`.
- [ ] **`feature/mcp-ci`** — `.github/workflows/mcp-python.yml`.
      Merge: `git merge --no-ff feature/mcp-ci`.
- [ ] Deferred (own branches, §9): `feature/mcp-bridge`,
      `feature/mcp-elicitation`, `feature/mcp-audit`, `feature/mcp-signals`.

## 11. Ports

`mentra-bridge/` already uses 8088 (HTTP), 8089 (WS), 3000 (MentraOS).
FastMCP default for sse / streamable-http is `127.0.0.1:8000` — that's
outside the bridge block, so **keep FastMCP's default 8000** rather than
inventing a new one. Users who want to override pass `--port`.

## 12. Defaulted decisions (flag for redirect)

- **Python (not TS).** All three reference repos are Python; the mcp Python
  SDK is the mature path.
- **`mcp-server/` sibling location.** Matches `mentra-bridge/` naming; keeps
  the Kotlin build root clean.
- **PHR34CKER5-form `main()` (unified `app.run(transport=…)`).** H4CKRF's
  explicit-async form is only needed once we have lifespan teardown; that's
  §9.3.
- **Transport CLI-only, no env var.** All three references do this. Change
  only if a specific deployment needs it.
- **MVP scope = knowledge/schema tools.** Bridge to the phone is the
  interesting version but needs its own design pass (§9.1).
- **Package `mcp>=1.2.0,<2`.** All three references pin this; the streamable-http
  transport lives inside that range.
