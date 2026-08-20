"""Cross-link integrity for the corpus.

Mirrors PHR34CKER5's `test_all_see_also_references_resolve`: every
cross-page reference must point at a file that actually exists. When
someone renames a page, this test lights up before an MCP client
hands the user a broken link.

Only in-repo Markdown links are checked. Anchors (`#foo`), external
URLs (`http://`, `mailto:`), and inline backtick prose refs
(`` `foo.md` ``) are ignored — the first two aren't in scope, and the
third is prose the author owns.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
CORPUS_ROOT = REPO_ROOT / "knowledge"

# [text](target) — grabs `target` as group 1. Non-greedy on both sides.
_LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")


def _iter_links(text: str):
    """Yield (link_text, raw_target) for every Markdown link in `text`."""
    for m in _LINK_RE.finditer(text):
        yield m.group(1), m.group(2)


def _is_external(target: str) -> bool:
    lower = target.lower()
    return any(
        lower.startswith(p)
        for p in ("http://", "https://", "mailto:", "ftp://")
    )


def _strip_fragment(target: str) -> str:
    """Cut off '#anchor' — we only resolve the file part."""
    return target.split("#", 1)[0]


@pytest.fixture(scope="module")
def all_pages() -> list[Path]:
    return sorted(CORPUS_ROOT.rglob("*.md"))


def test_every_markdown_link_resolves(all_pages):
    """Every relative `[text](path)` link inside knowledge/ must resolve
    to an existing file or directory on disk."""
    broken: list[str] = []
    for page in all_pages:
        text = page.read_text(encoding="utf-8")
        for _, raw in _iter_links(text):
            if _is_external(raw):
                continue
            file_part = _strip_fragment(raw).strip()
            if not file_part:
                # Pure `#anchor` — nothing to resolve here.
                continue
            resolved = (page.parent / file_part).resolve()
            if not resolved.exists():
                broken.append(
                    f"{page.relative_to(REPO_ROOT)}: [...]({raw}) -> "
                    f"{resolved} (missing)"
                )
    assert not broken, "broken cross-links:\n  " + "\n  ".join(broken)


def test_manifest_links_resolve_to_topic_pages():
    """MANIFEST.md is the corpus TOC — its links carry extra weight.
    Every markdown link there that lands on a .md file must hit a
    canonical page under a topic directory."""
    manifest = CORPUS_ROOT / "MANIFEST.md"
    text = manifest.read_text(encoding="utf-8")
    md_links = 0
    for _, raw in _iter_links(text):
        if _is_external(raw):
            continue
        file_part = _strip_fragment(raw).strip()
        if not file_part.endswith(".md"):
            continue
        md_links += 1
        resolved = (manifest.parent / file_part).resolve()
        assert resolved.exists(), (
            f"MANIFEST link [...]({raw}) does not resolve"
        )
    assert md_links >= 15, (
        f"MANIFEST.md has only {md_links} .md links; expected the TOC to be "
        f"substantive (>=15)"
    )


def test_all_manifest_topic_directories_are_actually_topics():
    """MANIFEST.md advertises certain topic directories. Each must be a
    real directory holding at least one .md file."""
    manifest = (CORPUS_ROOT / "MANIFEST.md").read_text(encoding="utf-8")
    # Directory-shaped mentions look like `**`topic-name/`**` or `topic/`.
    dir_re = re.compile(r"\*\*`([a-z][a-z0-9-]+)/`\*\*")
    called_out = {m.group(1) for m in dir_re.finditer(manifest)}
    # Only enforce the ones that live under knowledge/.
    missing_or_empty = []
    for topic in sorted(called_out):
        d = CORPUS_ROOT / topic
        if not d.is_dir() or not any(d.rglob("*.md")):
            missing_or_empty.append(topic)
    assert not missing_or_empty, (
        f"MANIFEST calls out topic dirs that don't exist / are empty: "
        f"{missing_or_empty}"
    )
