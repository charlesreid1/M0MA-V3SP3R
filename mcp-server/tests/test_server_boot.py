from __future__ import annotations
import subprocess
import sys


def test_server_module_imports_and_names_itself():
    from vesper_mcp import server
    assert server.mcp.name == "vesper"


def test_help_lists_all_three_transports():
    result = subprocess.run(
        [sys.executable, "-m", "vesper_mcp", "--help"],
        capture_output=True, text=True, check=True,
    )
    out = result.stdout
    for transport in ("stdio", "sse", "streamable-http"):
        assert transport in out, f"missing transport {transport!r} in --help"
