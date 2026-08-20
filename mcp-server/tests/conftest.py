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
    try:
        from vesper_mcp.tools import schema
        cache_clear = getattr(getattr(schema, "_load_schema", None), "cache_clear", None)
        if cache_clear is not None:
            cache_clear()
    except ImportError:
        pass
    return tmp_path
