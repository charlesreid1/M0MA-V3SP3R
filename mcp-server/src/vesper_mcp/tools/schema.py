from __future__ import annotations


async def list_actions() -> dict:
    """List every action id accepted by the app's execute_command tool. (stub)"""
    return {"ok": True, "data": []}


async def describe_action(action: str) -> dict:
    """Describe one action. (stub)"""
    return {"ok": True, "data": {"action": action, "args": {}, "risk": "unknown"}}
