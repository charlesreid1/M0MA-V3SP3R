from __future__ import annotations

import sys

import pytest


@pytest.mark.parametrize("transport", ["stdio", "sse", "streamable-http"])
def test_run_dispatches_selected_transport(monkeypatch: pytest.MonkeyPatch, transport: str):
    from vesper_mcp import server
    captured = {}

    def fake_run(*, transport):
        captured["transport"] = transport

    monkeypatch.setattr(server.mcp, "run", fake_run)
    monkeypatch.setattr(sys, "argv", ["vesper-mcp", "--transport", transport])
    server.main()
    assert captured == {"transport": transport}


def test_host_port_ignored_on_stdio(monkeypatch: pytest.MonkeyPatch):
    from vesper_mcp import server
    monkeypatch.setattr(server.mcp, "run", lambda **kw: None)
    original_host = server.mcp.settings.host
    original_port = server.mcp.settings.port
    monkeypatch.setattr(sys, "argv",
        ["vesper-mcp", "--transport", "stdio", "--host", "0.0.0.0", "--port", "9999"])
    server.main()
    assert server.mcp.settings.host == original_host
    assert server.mcp.settings.port == original_port


@pytest.mark.parametrize("transport", ["sse", "streamable-http"])
def test_host_port_applied_on_http_transports(monkeypatch: pytest.MonkeyPatch, transport: str):
    from vesper_mcp import server
    monkeypatch.setattr(server.mcp, "run", lambda **kw: None)
    monkeypatch.setattr(sys, "argv",
        ["vesper-mcp", "--transport", transport, "--host", "0.0.0.0", "--port", "9999"])
    server.main()
    assert server.mcp.settings.host == "0.0.0.0"
    assert server.mcp.settings.port == 9999
