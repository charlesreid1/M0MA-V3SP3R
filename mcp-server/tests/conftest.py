from __future__ import annotations

import json
from pathlib import Path

import pytest


@pytest.fixture
def fake_corpus(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    """Lay out a minimal fake corpus mirroring the real repo shape.

    - `knowledge/<topic>/<name>.md` files provide list_topics / read_doc /
      search_docs fixtures.
    - `docs/execute_command_schema.json` is the interface schema used by
      list_actions / describe_action.
    """
    knowledge = tmp_path / "knowledge"
    (knowledge / "vesper").mkdir(parents=True)
    (knowledge / "vesper" / "architecture.md").write_text(
        "# Architecture\n\nAlpha bravo charlie.\n"
    )
    (knowledge / "vesper" / "campaigns.md").write_text(
        "# Campaigns\n\nDelta echo foxtrot.\n"
    )
    (knowledge / "skills").mkdir()
    (knowledge / "skills" / "wifi-attack.md").write_text(
        "# WiFi attack\n\nKilo lima mike.\n"
    )
    (knowledge / "marauder").mkdir()
    (knowledge / "marauder" / "README.md").write_text(
        "# Marauder\n\nNovember oscar papa.\n"
    )
    (knowledge / "MANIFEST.md").write_text(
        "# Manifest\n\nRoot-level lore file — surfaces as topic `_root`.\n"
    )

    docs = tmp_path / "docs"
    docs.mkdir()
    (docs / "execute_command_schema.json").write_text(json.dumps({
        "properties": {
            "action": {"enum": ["list_directory", "read_file", "get_device_info"]},
            "args": {"properties": {
                "path": {"type": "string", "description": "target path"},
            }},
        },
        "required": ["action", "args", "justification", "expected_effect"],
    }))

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
