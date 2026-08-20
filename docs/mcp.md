# MCP setup — connecting a client to `vesper-mcp`

`mcp-server/` ships a FastMCP server that exposes the M0MA-V3SP3R runbook
corpus and the Flipper Zero `execute_command` schema over the Model
Context Protocol. Three transports are supported: `stdio` (default,
what Claude Desktop / opencode use), `sse` (HTTP + Server-Sent Events),
and `streamable-http` (HTTP + streaming JSON-RPC).

Before any of the snippets below work, bootstrap the venv:

```bash
cd mcp-server
python3.11 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -e '.[dev]'
```

The entry point lands at `mcp-server/.venv/bin/vesper-mcp`. All configs
below assume that exact path.

## Claude Desktop / opencode / Cursor — via `.mcp.json`

The repo already ships a `.mcp.json` at the repo root. Any MCP client
that autodetects it (Claude Desktop, opencode, Cursor) picks up a
`vesper` server on stdio automatically, provided the client starts with
the repo root as its working directory.

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

The bash wrapper sources `mcp-server/.env` if present (so
`VESPER_MCP_KNOWLEDGE` / `VESPER_MCP_LOG_LEVEL` overrides get picked up
without touching the client config), then execs the console script.

If your client wants absolute paths instead of `$PWD`, replace both
`$PWD` occurrences with the absolute path to this repo checkout.

## Raw stdio (any MCP client)

```
mcp-server/.venv/bin/vesper-mcp --transport stdio
```

Serves JSON-RPC over stdin/stdout; logs to stderr only. Never writes to
stdout other than protocol frames.

## SSE (remote MCP clients that speak SSE)

```
mcp-server/.venv/bin/vesper-mcp --transport sse --host 127.0.0.1 --port 8765
```

Verify with curl:

```bash
curl -N http://127.0.0.1:8765/sse | head -c 200
# expect: an "event: endpoint" line and a "data: /messages/?session_id=..." payload
```

## streamable-http (newer HTTP MCP transport)

```
mcp-server/.venv/bin/vesper-mcp --transport streamable-http --host 127.0.0.1 --port 8765
```

Verify with curl (the `Accept` header MUST include both mediatypes or
the server returns 406):

```bash
curl -X POST http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
# expect: a JSON-RPC response with serverInfo.name == "vesper"
```

`--host` / `--port` default to `127.0.0.1:8000` for both HTTP transports
and are silently ignored on stdio. The `8765` above is used only because
it's outside the `mentra-bridge/` block (8088/8089) and free on most
dev machines.

## Environment variables

| Variable                 | Default | Meaning                                    |
| ------------------------ | ------- | ------------------------------------------ |
| `VESPER_MCP_KNOWLEDGE`   | (auto)  | Override corpus root; must contain `docs/` |
| `VESPER_MCP_LOG_LEVEL`   | `INFO`  | Stderr log level (DEBUG/INFO/WARNING/ERROR) |

Transport / host / port are CLI-only on purpose — matches the reference
implementations (H4CKRF-6H05T, P1N3NUT5, PHR34CKER5).

## Tools and resources exposed

Tools (`tools/list` returns exactly these five):

- `list_topics()` — enumerate corpus docs.
- `read_doc(topic: str)` — return one doc body.
- `search_docs(query: str, limit: int = 20)` — substring search.
- `list_actions()` — every `execute_command` action id (~60).
- `describe_action(action: str)` — the schema's args block for one action.

Resources:

- `vesper://docs/index` (`application/json`) — list of topic ids.
- `vesper://docs/{topic}` (`text/markdown`) — doc body.
- `vesper://schema/actions` (`application/json`) — action catalog.

## Troubleshooting

- **`command not found: vesper-mcp`** — venv isn't activated or the
  `.venv` doesn't exist yet. Rerun the bootstrap block.
- **`Could not locate corpus`** — you're running the wheel outside a
  source checkout and no `_knowledge/` was packaged. Set
  `VESPER_MCP_KNOWLEDGE` to a directory containing a `docs/` folder.
- **stdio client sees corrupted frames** — something in the process is
  writing to stdout. Every module in this subproject must log via
  `logging.getLogger(__name__)`, never `print`.
- **`address already in use`** on sse / streamable-http — an earlier
  run of the server is still bound; `lsof -ti :<port> | xargs kill`.
