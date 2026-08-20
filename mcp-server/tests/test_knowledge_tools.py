from __future__ import annotations


async def test_list_topics_returns_grouped_shape(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    topics = result["data"]["topics"]
    assert "vesper" in topics
    assert set(topics["vesper"]) == {"architecture", "campaigns"}
    assert result["data"]["file_count"] >= 3


async def test_list_topics_includes_nested_folders(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    topics = result["data"]["topics"]
    assert "skills" in topics
    assert "wifi-attack" in topics["skills"]


async def test_list_topics_root_level_is_underscore_root(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.list_topics()
    assert result["ok"] is True
    topics = result["data"]["topics"]
    assert "_root" in topics
    assert "MANIFEST" in topics["_root"]


async def test_read_doc_returns_nested_body(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("skills", "wifi-attack")
    assert result["ok"] is True
    assert "Kilo lima mike." in result["data"]["content"]


async def test_read_doc_returns_body(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("vesper", "architecture")
    assert result["ok"] is True
    assert "Alpha bravo charlie." in result["data"]["content"]


async def test_read_doc_defaults_to_readme(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("marauder")
    assert result["ok"] is True
    assert "November oscar papa." in result["data"]["content"]


async def test_read_doc_underscore_hyphen_equivalent(fake_corpus):
    from vesper_mcp.tools import knowledge
    a = await knowledge.read_doc("skills", "wifi_attack")
    b = await knowledge.read_doc("skills", "wifi-attack")
    assert a["ok"] is True
    assert b["ok"] is True
    assert a["data"]["content"] == b["data"]["content"]


async def test_read_doc_unknown(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.read_doc("nowhere", "nothing")
    assert result["ok"] is False
    assert result["error"]["code"] == "unknown_topic"


async def test_search_docs_matches_case_insensitive(fake_corpus):
    from vesper_mcp.tools import knowledge
    result = await knowledge.search_docs("DELTA")
    assert result["ok"] is True
    assert any(
        hit["topic"] == "vesper" and hit["name"] == "campaigns"
        for hit in result["data"]
    )


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
