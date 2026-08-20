from __future__ import annotations

import os
from pathlib import Path
from typing import Any


def knowledge_root() -> Path:
    """Locate the corpus root. Resolution order:

    1. $VESPER_MCP_KNOWLEDGE (if set and points to a directory containing knowledge/).
    2. Editable / source install: walk up from this file until we find a
       directory containing both a `knowledge/` subdir and a `mcp-server/` subdir.
    3. Wheel install: the packaged copy at <package>/_knowledge/.

    Raises FileNotFoundError with a specific message if none succeed."""
    override = os.environ.get("VESPER_MCP_KNOWLEDGE")
    if override:
        p = Path(override).expanduser().resolve()
        if not (p / "knowledge").is_dir():
            raise FileNotFoundError(
                f"VESPER_MCP_KNOWLEDGE={override!r} does not contain a knowledge/ subdirectory"
            )
        return p

    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / "knowledge").is_dir() and (parent / "mcp-server").is_dir():
            return parent

    packaged = here.parent / "_knowledge"
    if (packaged / "knowledge").is_dir():
        return packaged

    raise FileNotFoundError(
        "Could not locate corpus. Set $VESPER_MCP_KNOWLEDGE, run from a "
        "source checkout, or reinstall the wheel (which bundles _knowledge/)."
    )


def schema_path() -> Path:
    """The Kotlin-generated execute_command JSON Schema.

    Lives at `docs/execute_command_schema.json` in a source checkout, or
    at `<package>/_knowledge/docs/execute_command_schema.json` inside the
    wheel. Distinct from the corpus (`knowledge/`) — this is the *interface*
    to the Android executor, not lore about the Flipper.
    """
    return knowledge_root() / "docs" / "execute_command_schema.json"


def ok(data: Any) -> dict[str, Any]:
    return {"ok": True, "data": data}


def err(message: str, *, code: str = "error") -> dict[str, Any]:
    return {"ok": False, "error": {"code": code, "message": message}}
