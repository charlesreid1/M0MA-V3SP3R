from __future__ import annotations


async def list_topics() -> dict:
    """List every documentation topic available in the corpus. (stub)"""
    return {"ok": True, "data": []}


async def read_doc(topic: str) -> dict:
    """Return the full body of one documentation topic. (stub)"""
    return {"ok": True, "data": {"topic": topic, "content": ""}}


async def search_docs(query: str, limit: int = 20) -> dict:
    """Substring search across the corpus. (stub)"""
    return {"ok": True, "data": []}
