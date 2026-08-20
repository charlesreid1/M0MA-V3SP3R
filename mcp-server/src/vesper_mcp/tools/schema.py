from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

from ..runtime import err, ok, schema_path


def _schema_path() -> Path:
    return schema_path()


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

    The schema does not tag which args apply to which action, so this call
    returns the union — every documented arg for the execute_command
    interface. Callers filter by action-appropriate keys."""
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
