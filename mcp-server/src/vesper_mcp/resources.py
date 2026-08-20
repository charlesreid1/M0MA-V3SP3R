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
            raise ValueError(result.get("error", {}).get("message", "read failed"))
        return result["data"]["content"]

    @mcp.resource("vesper://schema/actions", mime_type="application/json")
    async def _schema_actions() -> str:
        result = await schema.list_actions()
        return json.dumps(result)
