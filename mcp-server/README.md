# mcp-server — FastMCP server for M0MA-V3SP3R

Python FastMCP server that exposes the M0MA-V3SP3R runbook corpus and the
Flipper Zero `execute_command` schema over the MCP protocol. Supports
`stdio`, `sse`, and `streamable-http` transports.

See [`../plan-mcp.md`](../plan-mcp.md) for the full plan, rationale, and
landing order.

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

## Test

```bash
pytest -q
ruff check .
```
