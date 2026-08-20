from __future__ import annotations

import json

from mcp.server.fastmcp import FastMCP

from .tools import knowledge, schema

URI_SCHEME = "vesper"


def register_resources(mcp: FastMCP) -> None:
    @mcp.resource(f"{URI_SCHEME}://index", mime_type="text/markdown")
    async def _index() -> str:
        """Human-readable index of the corpus."""
        result = await knowledge.list_topics()
        if not result.get("ok"):
            raise ValueError(result.get("error", {}).get("message", "list_topics failed"))
        data = result["data"]
        lines = ["# M0MA-V3SP3R corpus", f"root: {data['root']}", ""]
        for topic in sorted(data["topics"]):
            lines.append(f"## {topic}")
            for name in data["topics"][topic]:
                lines.append(f"- [{name}]({URI_SCHEME}://{topic}/{name})")
            lines.append("")
        return "\n".join(lines)

    @mcp.resource(f"{URI_SCHEME}://schema/actions", mime_type="application/json")
    async def _schema_actions() -> str:
        result = await schema.list_actions()
        return json.dumps(result)

    @mcp.resource(URI_SCHEME + "://{topic}/{name}", mime_type="text/markdown")
    async def _lore(topic: str, name: str) -> str:
        result = await knowledge.read_doc(topic, name)
        if not result.get("ok"):
            raise ValueError(result.get("error", {}).get("message", "read failed"))
        return result["data"]["content"]
