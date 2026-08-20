from __future__ import annotations


async def test_list_topics_lists_fake_corpus(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    topics = {row["topic"] for row in result["data"]}
    assert {"architecture", "campaigns", "readme"} <= topics


async def test_read_doc_returns_body(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("architecture")
    assert result["ok"] is True
    assert "Alpha bravo charlie." in result["data"]["content"]


async def test_read_doc_underscore_hyphen_equivalent(fake_corpus):
    from vesper_mcp.tools import knowledge
    a = await knowledge.read_doc("app_build_process")
    b = await knowledge.read_doc("app-build-process")
    assert a["ok"] == b["ok"]


async def test_read_doc_unknown(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("no-such-thing")
    assert result["ok"] is False
    assert result["error"]["code"] == "unknown_topic"


async def test_search_docs_matches_case_insensitive(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs("DELTA")
    assert result["ok"] is True
    assert any(hit["topic"] == "campaigns" for hit in result["data"])


async def test_list_actions_from_schema(fake_corpus):
    from vesper_mcp.tools import schema
    result = await schema.list_actions()
    assert result["ok"] is True
    assert result["data"] == ["get_device_info", "list_directory", "read_file"]


async def test_describe_action_known(fake_corpus):
    from vesper_mcp.tools import schema
    result = await schema.describe_action("read_file")
    assert result["ok"] is True
    assert result["data"]["action"] == "read_file"
    assert "path" in result["data"]["args"]
    assert "risk" not in result["data"]


async def test_describe_action_unknown(fake_corpus):
    from vesper_mcp.tools import schema
    result = await schema.describe_action("no_such_action")
    assert result["ok"] is False
    assert result["error"]["code"] == "unknown_action"
