# plan-mcp.md — Add a FastMCP server to M0MA-V3SP3R

Goal: bolt a Python FastMCP server onto this repo alongside the existing
Android app and the Node `mentra-bridge/`. Match the wiring idiom of
H4CKRF-6H05T, P1N3NUT5, and PHR34CKER5, and support all three transports the
user asked for: `stdio`, `sse`, `streamable-http`.

**Scope note.** The MCP server is an **alternative frontend** to the
Android app, not a layer on top of it — see §9. The MVP (§§1–8, §10.1–10.5)
is a knowledge-focused corpus server: it exposes the runbook and the
execute_command schema for planning and inspection, but does not execute
against Flipper hardware. Execution-side capability is a future design
pass, not a "phase 2" of this plan.

If any instruction below is underspecified, prefer the shape that matches
P1N3NUT5 / PHR34CKER5 — those are the closest reference points.

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

**Decision for M0MA-V3SP3R:** use the PHR34CKER5/P1N3NUT5 form
(`app.run(transport=…)`). MVP holds no long-lived connections — nothing
to tear down. The explicit-async form is only worth adopting if MCP later
grows a stateful executor (§9).

Ignoring `--host` / `--port` on stdio is intentional and matches all three
references — do not error, do not warn. It's a no-op.

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
│   ├── .gitignore               # ignores .venv/, dist/, build/, *.egg-info/, __pycache__/, .pytest_cache/, .ruff_cache/
│   ├── README.md
│   ├── src/vesper_mcp/
│   │   ├── __init__.py          # sets __version__ = "0.1.0" only
│   │   ├── __main__.py          # `from .server import main; main()` under __name__ guard
│   │   ├── server.py            # FastMCP instance + main() + tool registration
│   │   ├── logging_config.py    # stderr-only (H4CKRF idiom)
│   │   ├── runtime.py           # knowledge_root(), envelope helper, env-var readers
│   │   ├── tools/
│   │   │   ├── __init__.py      # empty
│   │   │   ├── knowledge.py     # list_topics, read_doc, search_docs
│   │   │   └── schema.py        # list_actions, describe_action (reads execute_command_schema.json)
│   │   ├── resources.py         # vesper://... URIs (registered in server.py)
│   │   └── _knowledge/          # PACKAGED corpus copy (see §2 force-include)
│   │       ├── docs/            # mirror of repo-root docs/
│   │       └── README.md        # mirror of repo-root README.md
│   └── tests/
│       ├── conftest.py          # pytest fixtures (tmp corpus dir, env-var patcher)
│       ├── test_server_boot.py
│       ├── test_transport_dispatch.py
│       └── test_knowledge_tools.py
├── .mcp.json                    # NEW — Claude Desktop / opencode
└── .claude/                     # (deferred; not part of this plan)
```

Package name: `vesper_mcp` (matches app namespace `com.vesper.flipper`).
Console script: `vesper-mcp`.

**Corpus location — two resolution modes.** The knowledge tools need to
locate `docs/` at runtime, and the path differs depending on install mode:

1. **Editable install / running from source** (`pip install -e .`, `pytest`,
   `python -m vesper_mcp`): the repo-root `docs/` exists at
   `<repo>/docs/`. `runtime.knowledge_root()` walks up from
   `Path(__file__).resolve()` looking for a directory that contains a
   `docs/` subdirectory AND a sibling `mcp-server/`. That's the repo root.
2. **Installed wheel** (`pip install vesper-mcp` or `pipx install`): the
   repo checkout isn't available. The wheel bundles `_knowledge/` via
   `force-include` (§2), so `runtime.knowledge_root()` falls back to
   `Path(__file__).resolve().parent / "_knowledge"`.
3. **Override**: `$VESPER_MCP_KNOWLEDGE` (also settable via `--knowledge`)
   wins over both. Must be an existing directory that contains at least a
   `docs/` subdirectory — validated at first tool call, not at boot.

Exact `knowledge_root()` implementation is in §5.

## 2. `pyproject.toml`

Hatchling backend, `mcp>=1.2.0,<2` pin (same as all three references —
`mcp.server.fastmcp` was dropped in mcp 2.0), force-include the repo `docs/`
and `README.md` so `vesper-mcp` still works when installed from a built wheel.

```toml
[project]
name = "vesper-mcp"
version = "0.1.0"
description = "FastMCP server exposing the M0MA-V3SP3R runbook corpus and Flipper Zero command schema."
readme = "README.md"
requires-python = ">=3.11"
license = { file = "../LICENSE" }
authors = [{ name = "M0MA-V3SP3R contributors" }]
dependencies = [
    # mcp 2.0 dropped mcp.server.fastmcp; pin <2. Same rationale as
    # H4CKRF-6H05T, P1N3NUT5, PHR34CKER5.
    "mcp>=1.2.0,<2",
    "httpx>=0.27.0",
    "pydantic>=2.6",
]

[project.optional-dependencies]
dev = [
    "pytest>=8",
    "pytest-asyncio>=0.23",
    "ruff>=0.5",
]

[project.scripts]
vesper-mcp = "vesper_mcp.__main__:main"

[build-system]
requires = ["hatchling>=1.24"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/vesper_mcp"]

# Bundle the repo-root docs into the wheel so `pip install vesper-mcp`
# (no repo checkout) still has a corpus. Paths are resolved relative to
# this pyproject.toml.
[tool.hatch.build.targets.wheel.force-include]
"../docs" = "vesper_mcp/_knowledge/docs"
"../README.md" = "vesper_mcp/_knowledge/README.md"

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]

[tool.ruff]
line-length = 100
target-version = "py311"

[tool.ruff.lint]
select = ["E", "F", "I", "UP", "B"]
```

**Bootstrap commands (documented in `mcp-server/README.md`).** No guessing:

```bash
cd mcp-server
python3.11 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -e '.[dev]'
```

`pip install -e '.[dev]'` puts an entry point at `.venv/bin/vesper-mcp`.
The `.mcp.json` in §7 assumes that exact path.

## 3. `server.py` — FastMCP instance + `main()`

Full contents. Copy-paste this file into `src/vesper_mcp/server.py`:

```python
# src/vesper_mcp/server.py
from __future__ import annotations
import argparse
import os

from mcp.server.fastmcp import FastMCP

from .logging_config import configure_logging
from .resources import register_resources
from .tools import knowledge, schema

mcp = FastMCP(
    name="vesper",
    instructions=(
        "M0MA-V3SP3R — knowledge-focused corpus server for the Vesper "
        "Flipper Zero project. Use list_topics / read_doc / search_docs "
        "to browse the runbook; list_actions / describe_action to inspect "
        "the execute_command schema. This server does not execute commands "
        "against a Flipper — it is a planning and reference surface."
    ),
)

# Registration list (P1N3NUT5 idiom). Leaf modules stay import-safe
# for tests — they don't reference `mcp` at import time.
_TOOLS = (
    knowledge.list_topics,
    knowledge.read_doc,
    knowledge.search_docs,
    schema.list_actions,
    schema.describe_action,
)


def _apply_transport_settings(args: argparse.Namespace) -> None:
    """Only sse / streamable-http honor --host / --port. stdio ignores them silently."""
    if args.transport in ("sse", "streamable-http"):
        if args.host is not None:
            mcp.settings.host = args.host
        if args.port is not None:
            mcp.settings.port = args.port


def _register_tools() -> None:
    for tool in _TOOLS:
        mcp.tool()(tool)


def main() -> None:
    parser = argparse.ArgumentParser(prog="vesper-mcp")
    parser.add_argument(
        "--transport",
        choices=["stdio", "sse", "streamable-http"],
        default="stdio",
        help=(
            "MCP transport. 'stdio' is what Claude Desktop / opencode use; "
            "'sse' and 'streamable-http' expose an HTTP server for remote clients."
        ),
    )
    parser.add_argument(
        "--host",
        default=None,
        help="Bind host for sse / streamable-http transports (default: 127.0.0.1).",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=None,
        help="Bind port for sse / streamable-http transports (default: 8000).",
    )
    parser.add_argument(
        "--knowledge",
        default=None,
        help=(
            "Override corpus root (also readable from $VESPER_MCP_KNOWLEDGE). "
            "Must be an existing directory containing a docs/ subdirectory."
        ),
    )
    args = parser.parse_args()

    # 1. Env-var overrides FIRST — must land before anything reads them.
    if args.knowledge:
        os.environ["VESPER_MCP_KNOWLEDGE"] = args.knowledge

    # 2. Logging BEFORE any log emitter runs (i.e. before register/resource
    #    hooks that may log at import or bind time).
    configure_logging()

    # 3. Wire tools + resources.
    _register_tools()
    register_resources(mcp)

    # 4. Apply transport settings (no-op on stdio).
    _apply_transport_settings(args)

    # 5. Dispatch. FastMCP maps 'streamable-http' → run_streamable_http_async
    #    internally; we don't need the explicit-async form yet.
    mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
```

And `src/vesper_mcp/__main__.py`:

```python
from .server import main

if __name__ == "__main__":
    main()
```

And `src/vesper_mcp/__init__.py`:

```python
__version__ = "0.1.0"
```

## 4. `logging_config.py` — stderr-only

Neither P1N3NUT5 nor PHR34CKER5 configures logging; H4CKRF-6H05T does, and
it's the right call — a stray stdout write corrupts the JSON-RPC frame on
`stdio` transport. Copy the H4CKRF pattern.

**Hard rule for every module in this subproject:** never call `print(...)`
and never write to `sys.stdout`. Emit via `logging.getLogger(__name__)`
which is guaranteed by `configure_logging()` to go to stderr. If you need
to debug locally, run with `VESPER_MCP_LOG_LEVEL=DEBUG`.

```python
# src/vesper_mcp/logging_config.py
from __future__ import annotations
import logging
import os
import sys


def configure_logging() -> None:
    level = os.environ.get("VESPER_MCP_LOG_LEVEL", "INFO").upper()
    root = logging.getLogger()
    for h in list(root.handlers):
        root.removeHandler(h)
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(
        logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s")
    )
    root.addHandler(handler)
    root.setLevel(level)
    logging.captureWarnings(True)
```

## 5. Tool organization

Follow P1N3NUT5: leaves are plain `async def` functions, `server.py`
imports them and registers via loop. **Do not** decorate with `@mcp.tool()`
inside leaves — keeps them import-safe for tests without `mcp` on-path.

Every tool function is:
- `async def`
- module-level (not nested in a class)
- typed (Python type hints on every parameter and the return)
- docstring is the first line — FastMCP surfaces it as the tool description
- returns JSON-serializable values (dict / list / str / int / bool / None)

MVP tools (no phone hardware required):

- `knowledge.list_topics()` — enumerate corpus docs.
- `knowledge.read_doc(topic: str)` — return one doc body.
- `knowledge.search_docs(query: str, *, limit: int = 20)` — substring search.
- `schema.list_actions()` — all `execute_command` action ids from
  `docs/execute_command_schema.json` (auto-generated from Kotlin, currently
  contains 60+ actions).
- `schema.describe_action(action: str)` — the schema's args block for one action.

### 5.1 `runtime.py` — corpus resolution + envelope helper

```python
# src/vesper_mcp/runtime.py
from __future__ import annotations
import os
from pathlib import Path
from typing import Any


def knowledge_root() -> Path:
    """Locate the corpus root. Resolution order:

    1. $VESPER_MCP_KNOWLEDGE (if set and points to a directory containing docs/).
    2. Editable / source install: walk up from this file until we find a
       directory containing both a `docs/` subdir and a `mcp-server/` subdir.
    3. Wheel install: the packaged copy at <package>/_knowledge/.

    Raises FileNotFoundError with a specific message if none succeed."""
    override = os.environ.get("VESPER_MCP_KNOWLEDGE")
    if override:
        p = Path(override).expanduser().resolve()
        if not (p / "docs").is_dir():
            raise FileNotFoundError(
                f"VESPER_MCP_KNOWLEDGE={override!r} does not contain a docs/ subdirectory"
            )
        return p

    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / "docs").is_dir() and (parent / "mcp-server").is_dir():
            return parent

    packaged = here.parent / "_knowledge"
    if (packaged / "docs").is_dir():
        return packaged

    raise FileNotFoundError(
        "Could not locate corpus. Set $VESPER_MCP_KNOWLEDGE, run from a "
        "source checkout, or reinstall the wheel (which bundles _knowledge/)."
    )


def ok(data: Any) -> dict[str, Any]:
    return {"ok": True, "data": data}


def err(message: str, *, code: str = "error") -> dict[str, Any]:
    return {"ok": False, "error": {"code": code, "message": message}}
```

### 5.2 `tools/knowledge.py`

Topic-id ↔ path mapping is derived, not hardcoded — every `*.md` under
`docs/` and the top-level `README.md` become a topic. Topic id is the file
stem, kebab-cased and lowercased. `README.md` → `readme`.

```python
# src/vesper_mcp/tools/knowledge.py
from __future__ import annotations
from pathlib import Path

from ..runtime import knowledge_root, ok, err


def _md_files(root: Path) -> list[Path]:
    files: list[Path] = []
    docs = root / "docs"
    if docs.is_dir():
        files.extend(sorted(p for p in docs.glob("*.md") if p.is_file()))
    readme = root / "README.md"
    if readme.is_file():
        files.append(readme)
    return files


def _topic_id(path: Path) -> str:
    return path.stem.lower().replace("_", "-")


def _index(root: Path) -> dict[str, Path]:
    return {_topic_id(p): p for p in _md_files(root)}


async def list_topics() -> dict:
    """List every documentation topic available in the corpus.

    Returns {"ok": true, "data": [{"topic": str, "path": str, "bytes": int}, ...]}.
    Topics are derived from *.md files under docs/ plus the top-level README.md.
    Topic ids are lowercase, hyphenated file stems (e.g. app-build-process)."""
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    return ok([
        {"topic": tid, "path": str(p.relative_to(root)), "bytes": p.stat().st_size}
        for tid, p in _index(root).items()
    ])


async def read_doc(topic: str) -> dict:
    """Return the full body of one documentation topic.

    `topic` is a topic id from list_topics (e.g. "architecture", "campaigns",
    "labs", "app-build-process", "readme"). Case-insensitive. Underscore and
    hyphen are interchangeable."""
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    tid = topic.lower().replace("_", "-")
    idx = _index(root)
    if tid not in idx:
        return err(f"unknown topic {topic!r}; try list_topics()", code="unknown_topic")
    return ok({"topic": tid, "content": idx[tid].read_text(encoding="utf-8")})


async def search_docs(query: str, limit: int = 20) -> dict:
    """Substring search across the corpus (case-insensitive).

    Returns up to `limit` hits, each with topic, 1-indexed line number, and
    the matching line stripped of trailing whitespace."""
    if not query:
        return err("query must be non-empty", code="bad_query")
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    needle = query.lower()
    hits: list[dict] = []
    for tid, path in _index(root).items():
        for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if needle in line.lower():
                hits.append({"topic": tid, "line": i, "text": line.rstrip()})
                if len(hits) >= limit:
                    return ok(hits)
    return ok(hits)
```

### 5.3 `tools/schema.py`

Loads `docs/execute_command_schema.json` once per call (small file,
correctness > caching for MVP). Actions come from the top-level
`properties.action.enum` array; parameter descriptions come from
`properties.args.properties`.

Risk tier is deliberately **not** returned. The Kotlin `RiskAssessor`
lives inside the Android app and MCP is a separate frontend (§9); risk
enforcement is not the MCP server's job in this MVP. The MCP tools return
what the JSON schema knows and nothing more.

```python
# src/vesper_mcp/tools/schema.py
from __future__ import annotations
import json
from functools import lru_cache
from pathlib import Path

from ..runtime import knowledge_root, ok, err


def _schema_path() -> Path:
    return knowledge_root() / "docs" / "execute_command_schema.json"


@lru_cache(maxsize=1)
def _load_schema() -> dict:
    return json.loads(_schema_path().read_text(encoding="utf-8"))


async def list_actions() -> dict:
    """List every action id accepted by the app's execute_command tool.

    These come from docs/execute_command_schema.json (auto-generated from
    the Kotlin CommandAction enum). Returns {"ok": true, "data": [str, ...]}."""
    try:
        schema = _load_schema()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    actions = schema.get("properties", {}).get("action", {}).get("enum", [])
    return ok(sorted(actions))


async def describe_action(action: str) -> dict:
    """Describe one action: the full args.properties block from the schema.

    Returns every documented arg for the execute_command interface; the
    schema does not tag which args apply to which action, so this call
    returns the union."""
    try:
        schema = _load_schema()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    actions = schema.get("properties", {}).get("action", {}).get("enum", [])
    if action not in actions:
        return err(f"unknown action {action!r}; try list_actions()", code="unknown_action")
    args_props = schema.get("properties", {}).get("args", {}).get("properties", {})
    return ok({
        "action": action,
        "args": args_props,
        "required_top_level": schema.get("required", []),
    })
```

### Beyond MVP

Execution-side capability (turning the ~60 schema actions into MCP tools
that actually reach hardware) is out of scope until MCP grows into a peer
executor — see §9 for the architectural framing. If and when that lands,
enum-driven registration in the style of H4CKRF-6H05T's tool factory
(`src/hackrf_agent/mcp/server.py:81-194`) is the natural template.

## 6. Resources — `vesper://…` URI scheme

Match PHR34CKER5's inline decorator style (one file, low volume), but keep
the module registration-driven so `server.py` stays the single source of
wiring. `resources.py` exports `register_resources(mcp)` that `server.py`
calls once at boot.

URIs, exact handlers:

- `vesper://docs/index` → JSON list of topic ids (MIME `application/json`).
- `vesper://docs/{topic}` → Markdown body of one topic (MIME `text/markdown`).
- `vesper://schema/actions` → the raw `execute_command_schema.json` bytes
  (MIME `application/json`).

```python
# src/vesper_mcp/resources.py
from __future__ import annotations
import json

from mcp.server.fastmcp import FastMCP

from .tools import knowledge, schema


def register_resources(mcp: FastMCP) -> None:
    @mcp.resource("vesper://docs/index", mime_type="application/json")
    async def _docs_index() -> str:
        result = await knowledge.list_topics()
        return json.dumps(result)

    @mcp.resource("vesper://docs/{topic}", mime_type="text/markdown")
    async def _docs_body(topic: str) -> str:
        result = await knowledge.read_doc(topic)
        if not result.get("ok"):
            # FastMCP surfaces exceptions as resource-read errors.
            raise ValueError(result.get("error", {}).get("message", "read failed"))
        return result["data"]["content"]

    @mcp.resource("vesper://schema/actions", mime_type="application/json")
    async def _schema_actions() -> str:
        result = await schema.list_actions()
        return json.dumps(result)
```

## 7. Config artifacts

### 7.1 `mcp-server/.env.example`

Documents only the env vars actually consumed by the code. Transport /
host / port are CLI-only on purpose — matches the three references.

```
# Optional. Overrides corpus root. Must be a directory containing docs/.
# Leave unset to auto-detect (source checkout) or use the packaged copy (wheel).
# VESPER_MCP_KNOWLEDGE=/absolute/path/to/repo

# Logging level for stderr output. One of DEBUG / INFO / WARNING / ERROR.
# VESPER_MCP_LOG_LEVEL=INFO
```

### 7.2 `mcp-server/.gitignore`

```
.venv/
dist/
build/
*.egg-info/
__pycache__/
.pytest_cache/
.ruff_cache/
.env
```

### 7.3 `.mcp.json` at repo root

PHR34CKER5's bash-sources-`.env` trick, pointed at the sibling venv:

```json
{
  "mcpServers": {
    "vesper": {
      "command": "bash",
      "args": [
        "-c",
        "[ -f \"$PWD/mcp-server/.env\" ] && . \"$PWD/mcp-server/.env\"; exec \"$PWD/mcp-server/.venv/bin/vesper-mcp\""
      ]
    }
  }
}
```

Users who open this repo in Claude Desktop / opencode / Cursor get a `vesper`
server on stdio automatically. The `$PWD` reference means the launcher must
start with the repo root as cwd — every one of the three references relies
on the same behavior.

## 8. Tests

Layout: `mcp-server/tests/`. Pytest + pytest-asyncio (auto mode is set in
`pyproject.toml`).

### 8.1 `tests/conftest.py`

Provides a fixture that lays out a minimal fake corpus in a tmp dir and
points `$VESPER_MCP_KNOWLEDGE` at it. This isolates tests from the repo's
real `docs/` (so a doc change can't red-line the suite).

```python
# mcp-server/tests/conftest.py
from __future__ import annotations
import json
from pathlib import Path

import pytest


@pytest.fixture
def fake_corpus(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    docs = tmp_path / "docs"
    docs.mkdir()
    (docs / "architecture.md").write_text("# Architecture\n\nAlpha bravo charlie.\n")
    (docs / "campaigns.md").write_text("# Campaigns\n\nDelta echo foxtrot.\n")
    (docs / "execute_command_schema.json").write_text(json.dumps({
        "properties": {
            "action": {"enum": ["list_directory", "read_file", "get_device_info"]},
            "args": {"properties": {
                "path": {"type": "string", "description": "target path"},
            }},
        },
        "required": ["action", "args", "justification", "expected_effect"],
    }))
    (tmp_path / "README.md").write_text("# Readme\n\nGolf hotel india.\n")
    monkeypatch.setenv("VESPER_MCP_KNOWLEDGE", str(tmp_path))
    # Clear the schema lru_cache in case a prior test loaded a different one.
    from vesper_mcp.tools import schema
    schema._load_schema.cache_clear()
    return tmp_path
```

### 8.2 `tests/test_server_boot.py`

```python
# mcp-server/tests/test_server_boot.py
from __future__ import annotations
import subprocess
import sys


def test_server_module_imports_and_names_itself():
    from vesper_mcp import server
    assert server.mcp.name == "vesper"


def test_help_lists_all_three_transports():
    result = subprocess.run(
        [sys.executable, "-m", "vesper_mcp", "--help"],
        capture_output=True, text=True, check=True,
    )
    out = result.stdout
    for transport in ("stdio", "sse", "streamable-http"):
        assert transport in out, f"missing transport {transport!r} in --help"
```

### 8.3 `tests/test_transport_dispatch.py`

Monkeypatches `mcp.run` so `main()` returns without actually binding a
socket or attaching to stdio.

```python
# mcp-server/tests/test_transport_dispatch.py
from __future__ import annotations
import sys

import pytest


@pytest.mark.parametrize("transport", ["stdio", "sse", "streamable-http"])
def test_run_dispatches_selected_transport(monkeypatch: pytest.MonkeyPatch, transport: str):
    from vesper_mcp import server
    captured = {}

    def fake_run(*, transport):
        captured["transport"] = transport

    monkeypatch.setattr(server.mcp, "run", fake_run)
    monkeypatch.setattr(sys, "argv", ["vesper-mcp", "--transport", transport])
    server.main()
    assert captured == {"transport": transport}


def test_host_port_ignored_on_stdio(monkeypatch: pytest.MonkeyPatch):
    from vesper_mcp import server
    monkeypatch.setattr(server.mcp, "run", lambda **kw: None)
    original_host = server.mcp.settings.host
    original_port = server.mcp.settings.port
    monkeypatch.setattr(sys, "argv",
        ["vesper-mcp", "--transport", "stdio", "--host", "0.0.0.0", "--port", "9999"])
    server.main()
    assert server.mcp.settings.host == original_host
    assert server.mcp.settings.port == original_port


@pytest.mark.parametrize("transport", ["sse", "streamable-http"])
def test_host_port_applied_on_http_transports(monkeypatch: pytest.MonkeyPatch, transport: str):
    from vesper_mcp import server
    monkeypatch.setattr(server.mcp, "run", lambda **kw: None)
    monkeypatch.setattr(sys, "argv",
        ["vesper-mcp", "--transport", transport, "--host", "0.0.0.0", "--port", "9999"])
    server.main()
    assert server.mcp.settings.host == "0.0.0.0"
    assert server.mcp.settings.port == 9999
```

### 8.4 `tests/test_knowledge_tools.py`

```python
# mcp-server/tests/test_knowledge_tools.py
from __future__ import annotations

import pytest


async def test_list_topics_lists_fake_corpus(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    topics = {row["topic"] for row in result["data"]}
    assert {"architecture", "campaigns", "readme"} <= topics


async def test_read_doc_returns_body(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("architecture")
    assert result["ok"] is True
    assert "Alpha bravo charlie." in result["data"]["content"]


async def test_read_doc_underscore_hyphen_equivalent(fake_corpus):
    from vesper_mcp.tools import knowledge
    a = await knowledge.read_doc("app_build_process")
    b = await knowledge.read_doc("app-build-process")
    # Both should behave the same (both unknown here — fixture doesn't include it).
    assert a["ok"] == b["ok"]


async def test_read_doc_unknown(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("no-such-thing")
    assert result["ok"] is False
    assert result["error"]["code"] == "unknown_topic"


async def test_search_docs_matches_case_insensitive(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs("DELTA")
    assert result["ok"] is True
    assert any(hit["topic"] == "campaigns" for hit in result["data"])


async def test_list_actions_from_schema(fake_corpus):
    from vesper_mcp.tools import schema
    result = await schema.list_actions()
    assert result["ok"] is True
    assert result["data"] == ["get_device_info", "list_directory", "read_file"]


async def test_describe_action_known(fake_corpus):
    from vesper_mcp.tools import schema
    result = await schema.describe_action("read_file")
    assert result["ok"] is True
    assert result["data"]["action"] == "read_file"
    assert "path" in result["data"]["args"]


async def test_describe_action_unknown(fake_corpus):
    from vesper_mcp.tools import schema
    result = await schema.describe_action("no_such_action")
    assert result["ok"] is False
    assert result["error"]["code"] == "unknown_action"
```

### 8.5 CI

Add `.github/workflows/mcp-python.yml`:

```yaml
name: mcp-python
on:
  push:
    paths:
      - "mcp-server/**"
      - "docs/**"
      - ".github/workflows/mcp-python.yml"
  pull_request:
    paths:
      - "mcp-server/**"
      - "docs/**"
      - ".github/workflows/mcp-python.yml"

jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: mcp-server
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.11"
      - name: Install
        run: |
          python -m pip install --upgrade pip
          pip install -e '.[dev]'
      - name: Lint
        run: ruff check .
      - name: Test
        run: pytest -q
```

Repo has zero CI today — this is additive and only gates the new subproject.
It runs on `docs/**` changes too so a schema regeneration that breaks
`describe_action` shows up before merge.

## 9. Later phases

**Architectural note.** MCP and the Android app are **alternative frontends**
onto the same conceptual product, not layers of one system. When a user is
driving the MCP server (from Claude Desktop / opencode / a scripted client),
they are not driving the phone; and when they're driving the phone, they
are not driving the MCP server. The two share the corpus (`docs/`,
`README.md`, `execute_command_schema.json`) but not runtime state.

The eventual goal is for MCP to grow into a peer executor — reaching a
Flipper directly (USB serial, Flipper CLI protocol, or similar) without
routing through the app. But the near-term posture is deliberate:

**The MCP is a knowledge-focused corpus server.** It helps a user reason
about, plan, and inspect Flipper operations — read the runbook, search
the docs, look up an action's arg shape. It does not fire commands at
hardware. Everything the MVP ships (§§1–8, §10.1–10.5) already covers
this scope.

Future execution capability, when it lands, will be its own design pass
against the "MCP as peer executor" model, not a bridge to the Android
app. Design decisions from that pass (transport channel, risk-tier
enforcement Python-side, lifespan teardown, signal handling, audit
surface) are all deferred until that model is worked out. Nothing in
§10 depends on them.

Two housekeeping fixes carried into the MVP so the code doesn't lie
about scope:
- `describe_action` no longer references an app-side risk source or an
  app-side arg filter. Risk is simply omitted; the schema returns what
  the schema knows.
- The reserved `tools/faphub.py`, `tools/github.py`, `tools/bridge.py`
  filenames in the §1 tree are removed — they were placeholders for the
  wrong architecture.

## 10. Landing order — branches, not PRs

Workflow: cut a topic branch off `main` for each slice, work it to green,
then `git checkout main && git merge --no-ff <branch>` to land. The `--no-ff`
keeps the branch shape visible in `git log --graph` so the slices stay
legible after the fact. Delete the branch after merge; no PR review round.

For each slice: the "green criteria" section lists the exact commands you
must run and see pass before merging. Don't skip.

### 10.1 `feature/mcp-bootstrap`

Files added:
- `mcp-server/pyproject.toml` (§2, exact contents)
- `mcp-server/src/vesper_mcp/__init__.py` — one line: `__version__ = "0.1.0"`
- `mcp-server/src/vesper_mcp/__main__.py` — placeholder that just prints
  the version to stderr (real `main` lands in the next slice)
- `mcp-server/.env.example` (§7.1)
- `mcp-server/.gitignore` (§7.2)
- `mcp-server/README.md` — bootstrap commands (§2) + link to plan-mcp.md
- root `.gitignore` amend: add `mcp-server/.venv/`, `mcp-server/dist/`,
  `mcp-server/build/`, `mcp-server/*.egg-info/`, `mcp-server/.pytest_cache/`,
  `mcp-server/.ruff_cache/` if not already covered by generic rules.

Green criteria:
```
cd mcp-server
python3.11 -m venv .venv
. .venv/bin/activate
pip install -e '.[dev]'
python -m vesper_mcp   # should exit 0 and log version to stderr
which vesper-mcp       # should point inside .venv/bin
```

Merge: `git checkout main && git merge --no-ff feature/mcp-bootstrap`.

### 10.2 `feature/mcp-transports`

Files added / replaced:
- `mcp-server/src/vesper_mcp/server.py` (§3, exact contents; but the
  `_TOOLS` tuple is empty and `register_resources` is a no-op stub until
  the next slice — we're testing transport dispatch here, nothing else)
- `mcp-server/src/vesper_mcp/logging_config.py` (§4, exact contents)
- `mcp-server/src/vesper_mcp/__main__.py` — replace bootstrap placeholder
  with `from .server import main` / `main()`
- `mcp-server/src/vesper_mcp/resources.py` — stub `def register_resources(mcp): pass`
- `mcp-server/src/vesper_mcp/tools/__init__.py` — empty
- `mcp-server/src/vesper_mcp/tools/knowledge.py` — stub with `list_topics`,
  `read_doc`, `search_docs` returning `{"ok": True, "data": []}` /
  empty string (real bodies land next slice; we want the registration loop
  to exercise every symbol)
- `mcp-server/src/vesper_mcp/tools/schema.py` — same stub treatment
- `mcp-server/tests/conftest.py` (§8.1)
- `mcp-server/tests/test_server_boot.py` (§8.2)
- `mcp-server/tests/test_transport_dispatch.py` (§8.3)

Manual smoke (in three terminals; kill each with Ctrl-C after verifying):

```
# stdio (should print nothing; wait for JSON-RPC on stdin)
.venv/bin/vesper-mcp --transport stdio

# sse
.venv/bin/vesper-mcp --transport sse --port 8765
curl -N http://127.0.0.1:8765/sse | head -c 200   # expect an SSE stream, "event:" lines

# streamable-http
.venv/bin/vesper-mcp --transport streamable-http --port 8765
curl -X POST http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
# expect a JSON-RPC response with a serverInfo.name of "vesper"
```

Green criteria: `pytest -q` all pass, all three manual smokes produce the
expected output.

Merge: `git checkout main && git merge --no-ff feature/mcp-transports`.

### 10.3 `feature/mcp-tools-knowledge`

Files replaced (stubs → real bodies):
- `mcp-server/src/vesper_mcp/runtime.py` (§5.1, exact contents)
- `mcp-server/src/vesper_mcp/tools/knowledge.py` (§5.2, exact contents)
- `mcp-server/src/vesper_mcp/tools/schema.py` (§5.3, exact contents)
- `mcp-server/src/vesper_mcp/resources.py` (§6, exact contents)
- `mcp-server/tests/test_knowledge_tools.py` (§8.4)

Green criteria:
```
cd mcp-server
pytest -q         # every test in §8.2 / §8.3 / §8.4 passes
ruff check .      # clean
```

Manual: launch stdio server and confirm `tools/list` returns exactly
`list_topics`, `read_doc`, `search_docs`, `list_actions`, `describe_action`.

Merge: `git checkout main && git merge --no-ff feature/mcp-tools-knowledge`.

### 10.4 `feature/mcp-client-wiring`

Files added:
- `.mcp.json` at repo root (§7.3, exact contents)
- `docs/mcp.md` — setup snippets for Claude Desktop, opencode, Cursor,
  and raw curl for each HTTP transport. Include the exact `.venv/bin/vesper-mcp`
  paths and the three curl commands from §10.2's smoke test.

Green criteria: open the repo in Claude Desktop (or opencode); `vesper`
appears in the MCP server list; `list_topics` returns the real corpus
topics (`architecture`, `campaigns`, `labs`, `app-build-process`, `readme`).

Merge: `git checkout main && git merge --no-ff feature/mcp-client-wiring`.

### 10.5 `feature/mcp-ci`

Files added:
- `.github/workflows/mcp-python.yml` (§8.5, exact contents)

Green criteria: push the branch, watch the workflow go green in the PR
preview / GitHub Actions tab. `ruff check .` and `pytest -q` both pass.

Merge: `git checkout main && git merge --no-ff feature/mcp-ci`.

### 10.6 What's not in this plan

Execution-side capability (running commands against a Flipper directly
from MCP, without the Android app) is out of scope — see §9. When that
lands, it will need its own design pass and its own plan document, not
a phase-2 continuation of this one.

## 11. Ports

`mentra-bridge/` already uses 8088 (HTTP), 8089 (WS), 3000 (MentraOS).
FastMCP default for sse / streamable-http is `127.0.0.1:8000` — that's
outside the bridge block, so **keep FastMCP's default 8000** rather than
inventing a new one. Users who want to override pass `--port`.

Smoke-test commands in §10.2 use `8765` deliberately — a value nobody uses
so parallel dev servers don't collide.

## 12. Defaulted decisions (flag for redirect)

- **Python (not TS).** All three reference repos are Python; the mcp Python
  SDK is the mature path.
- **`mcp-server/` sibling location.** Matches `mentra-bridge/` naming; keeps
  the Kotlin build root clean.
- **PHR34CKER5-form `main()` (unified `app.run(transport=…)`).** H4CKRF's
  explicit-async form is only needed for lifespan teardown, which the MVP
  doesn't have.
- **Transport CLI-only, no env var.** All three references do this. Change
  only if a specific deployment needs it.
- **MVP scope = knowledge/schema tools only.** MCP and the Android app are
  alternative frontends, not a stack; execution-side capability is a future
  design pass, not a phase 2 (§9).
- **Package `mcp>=1.2.0,<2`.** All three references pin this; the streamable-http
  transport lives inside that range.
- **Corpus is bundled into the wheel via `force-include`.** Alternative:
  only support source installs. Rejected because pipx / remote install
  workflows would silently break tools.
- **Envelope format `{"ok": bool, "data": ..., "error": {"code","message"}}`.**
  Consistent across every tool return. Alternative: raise on error. Rejected
  because FastMCP surfaces exceptions as generic tool-call errors and we
  want richer, machine-readable codes for callers.
- **`describe_action` returns the schema's full args block, no risk tier.**
  Per-action arg filtering and risk enforcement belong to whichever frontend
  actually executes commands. MCP is the corpus frontend, so it doesn't
  synthesize either.
