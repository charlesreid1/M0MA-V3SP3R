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
        "M0MA-V3SP3R — Flipper Zero campaign runbook + on-device tool "
        "catalog. Use list_topics / read_doc / search_docs for corpus "
        "access; list_actions / describe_action to inspect the "
        "execute_command schema exposed by the Android app."
    ),
)

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

    if args.knowledge:
        os.environ["VESPER_MCP_KNOWLEDGE"] = args.knowledge

    configure_logging()

    _register_tools()
    register_resources(mcp)

    _apply_transport_settings(args)

    mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
