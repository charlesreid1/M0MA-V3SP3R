"""End-to-end tool tests against the real corpus.

Complements `test_knowledge_tools.py` (which uses a fake corpus fixture
to test the tool logic) by exercising the same tools against the
actual `knowledge/` shipped with the repo. This catches issues where
the tool works fine on synthetic input but chokes on real content —
UTF-8 edge cases, unexpected `_root` topic, large files, etc.

Mirrors PHR34CKER5's `test_bibliography_lookup` /
`test_search_records_query` — real store, real answers.
"""

from __future__ import annotations

from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]


@pytest.fixture(autouse=True)
def _use_real_corpus(monkeypatch):
    """Point tools at the repo's real corpus, not a fake fixture.

    Also clears the schema lru_cache in case a prior test loaded a fake
    schema — otherwise list_actions() would return that fake catalog.
    """
    monkeypatch.setenv("VESPER_MCP_KNOWLEDGE", str(REPO_ROOT))
    from vesper_mcp.tools import schema
    cache_clear = getattr(getattr(schema, "_load_schema", None), "cache_clear", None)
    if cache_clear is not None:
        cache_clear()


# --- list_topics --------------------------------------------------------------


async def test_list_topics_returns_all_plan_topics():
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    topics = result["data"]["topics"]
    for topic in (
        "flipper-hardware", "flipper-gpio", "flipper-storage", "flipper-cli",
        "firmware", "subghz", "ir", "nfc", "rfid", "ibutton",
        "marauder", "development", "skills", "legal", "vesper",
    ):
        assert topic in topics, f"list_topics missing {topic!r}"


async def test_list_topics_reports_root_metadata():
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    data = result["data"]
    assert data["file_count"] >= 30, (
        f"only {data['file_count']} files in corpus"
    )
    assert data["topic_count"] >= 15
    # `root` must point at the actual knowledge dir, not the repo root.
    assert data["root"].endswith("knowledge"), data["root"]


async def test_list_topics_surfaces_root_manifest_under_underscore_root():
    """MANIFEST.md is at knowledge/ (not under any subdirectory) so the
    tool surfaces it under the special `_root` topic id."""
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    root_files = result["data"]["topics"].get("_root", [])
    assert "MANIFEST" in root_files, (
        "knowledge/MANIFEST.md must appear as _root/MANIFEST"
    )


# --- read_doc ----------------------------------------------------------------


async def test_read_doc_returns_flipper_hardware_readme():
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("flipper-hardware", "README")
    assert result["ok"] is True
    body = result["data"]["content"]
    assert "STM32WB55" in body


async def test_read_doc_defaults_to_readme_when_name_omitted():
    """Callers can request `read_doc("marauder")` and get README."""
    from vesper_mcp.tools import knowledge
    a = await knowledge.read_doc("marauder")
    b = await knowledge.read_doc("marauder", "README")
    assert a["ok"] is True and b["ok"] is True
    assert a["data"]["content"] == b["data"]["content"]


async def test_read_doc_returns_path_relative_to_repo():
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("subghz", "protocols")
    assert result["ok"] is True
    assert result["data"]["path"] == "knowledge/subghz/protocols.md"


async def test_read_doc_underscore_hyphen_equivalent_on_real_corpus():
    from vesper_mcp.tools import knowledge
    a = await knowledge.read_doc("firmware", "compatibility_profile")
    b = await knowledge.read_doc("firmware", "compatibility-profile")
    assert a["ok"] is True and b["ok"] is True
    assert a["data"]["content"] == b["data"]["content"]


async def test_read_doc_unknown_pair_returns_error_envelope():
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("firmware", "nonesuch")
    assert result["ok"] is False
    assert result["error"]["code"] == "unknown_topic"


# --- search_docs -------------------------------------------------------------


@pytest.mark.parametrize("needle,expected_topic", [
    ("STM32WB55", "flipper-hardware"),
    ("Momentum", "firmware"),
    ("KeeLoq", "subghz"),
    ("ESP32", "marauder"),
    ("T5577", "rfid"),
    ("DS1990A", "ibutton"),
])
async def test_search_docs_finds_signature_terms(needle: str, expected_topic: str):
    """Each high-signal term must return at least one hit in the topic
    where the plan places it. This is the drift-alarm equivalent for
    substring search.

    `limit` is set high enough that even the most-repeated terms
    ("Momentum", "ESP32") don't exhaust it before reaching every topic.
    """
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs(needle, limit=500)
    assert result["ok"] is True
    hits = result["data"]
    assert any(h["topic"] == expected_topic for h in hits), (
        f"search_docs({needle!r}) returned no hits under topic "
        f"{expected_topic!r}. Topics with hits: "
        f"{sorted({h['topic'] for h in hits})}"
    )


async def test_search_docs_returns_line_numbers_and_text():
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs("Marauder", limit=5)
    assert result["ok"] is True
    for hit in result["data"]:
        assert "topic" in hit and "name" in hit
        assert isinstance(hit["line"], int) and hit["line"] >= 1
        assert "Marauder" in hit["text"] or "marauder" in hit["text"].lower()


async def test_search_docs_respects_limit():
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs("the", limit=7)
    assert result["ok"] is True
    assert len(result["data"]) <= 7


async def test_search_docs_case_insensitive_on_real_corpus():
    from vesper_mcp.tools import knowledge
    lower = await knowledge.search_docs("momentum", limit=50)
    upper = await knowledge.search_docs("MOMENTUM", limit=50)
    assert lower["ok"] is True and upper["ok"] is True
    # Same corpus, same needle case-folded — hit sets should match.
    def _key(h):
        return (h["topic"], h["name"], h["line"])
    assert sorted(map(_key, lower["data"])) == sorted(map(_key, upper["data"]))


async def test_search_docs_empty_query_returns_error():
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs("")
    assert result["ok"] is False
    assert result["error"]["code"] == "bad_query"


# --- schema interface --------------------------------------------------------


async def test_list_actions_returns_real_command_catalog():
    """The interface schema at docs/execute_command_schema.json is the
    Kotlin-generated action catalog. This test asserts the shape survives,
    not the exact contents — CommandAction changes will legitimately
    add/remove actions."""
    from vesper_mcp.tools import schema
    result = await schema.list_actions()
    assert result["ok"] is True
    actions = result["data"]
    assert isinstance(actions, list)
    assert len(actions) >= 30, f"only {len(actions)} actions in schema"
    # Canonical actions we expect regardless of enum churn.
    for expected in ("get_device_info", "execute_cli"):
        assert expected in actions, f"missing canonical action {expected!r}"


async def test_describe_action_returns_args_block_for_execute_cli():
    from vesper_mcp.tools import schema
    result = await schema.describe_action("execute_cli")
    assert result["ok"] is True
    data = result["data"]
    assert data["action"] == "execute_cli"
    assert "args" in data and isinstance(data["args"], dict)


async def test_describe_action_no_risk_field_leak():
    """Per plan-mcp.md §5.3: the MCP is a knowledge frontend; risk
    enforcement lives in the Android app. describe_action must NOT
    include a risk tier — that's the executor's business."""
    from vesper_mcp.tools import schema
    result = await schema.describe_action("get_device_info")
    assert result["ok"] is True
    assert "risk" not in result["data"]
