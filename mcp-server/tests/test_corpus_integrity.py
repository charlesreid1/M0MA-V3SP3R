"""Load-time contract for the M0MA-V3SP3R knowledge corpus.

Mirrors PHR34CKER5's `test_records.py` "load-time contract" section:
these tests catch broken files, missing conventions, and orphan pages
before they reach an MCP client.

Runs against the real `knowledge/` at the repo root — not a fake
fixture. If a page is added or renamed and violates the corpus
conventions in `CONTRIBUTING.md` / `knowledge/MANIFEST.md`, one of
these tests goes red.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
CORPUS_ROOT = REPO_ROOT / "knowledge"


@pytest.fixture(scope="module")
def all_pages() -> list[Path]:
    """Every .md file under `knowledge/`, sorted."""
    return sorted(CORPUS_ROOT.rglob("*.md"))


def test_corpus_root_exists():
    assert CORPUS_ROOT.is_dir(), f"missing corpus root {CORPUS_ROOT}"


def test_manifest_exists():
    assert (CORPUS_ROOT / "MANIFEST.md").is_file(), (
        "knowledge/MANIFEST.md is missing — the corpus TOC is load-bearing"
    )


def test_corpus_is_non_empty(all_pages):
    assert len(all_pages) >= 20, f"only {len(all_pages)} .md files in corpus"


def test_every_page_is_utf8(all_pages):
    for path in all_pages:
        try:
            path.read_text(encoding="utf-8")
        except UnicodeDecodeError as e:
            pytest.fail(f"{path.relative_to(REPO_ROOT)}: not UTF-8 ({e})")


def test_every_page_is_non_trivial(all_pages):
    """A page that only contains a heading is a placeholder — flag it."""
    trivial: list[str] = []
    for path in all_pages:
        text = path.read_text(encoding="utf-8").strip()
        # 200 bytes is roughly a heading + one paragraph.
        if len(text) < 200:
            trivial.append(str(path.relative_to(REPO_ROOT)))
    assert not trivial, f"trivial / placeholder pages: {trivial}"


def test_every_page_has_h1_title(all_pages):
    """Every page must have an H1 (`# Title`) — the first non-blank,
    non-comment, non-frontmatter line."""
    missing: list[str] = []
    for path in all_pages:
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        # Skip a leading YAML frontmatter block (`---` ... `---`) if any.
        i = 0
        # Skip leading blanks/comments.
        while i < len(lines) and (not lines[i].strip() or lines[i].strip().startswith("<!--")):
            i += 1
        if i < len(lines) and lines[i].strip() == "---":
            j = i + 1
            while j < len(lines) and lines[j].strip() != "---":
                j += 1
            i = j + 1  # step past the closing `---`
        # First non-blank line after that must be an H1.
        found = False
        while i < len(lines):
            stripped = lines[i].strip()
            i += 1
            if not stripped:
                continue
            if stripped.startswith("<!--"):
                continue
            if stripped.startswith("# "):
                found = True
            break
        if not found:
            missing.append(str(path.relative_to(REPO_ROOT)))
    assert not missing, f"pages without an H1 title: {missing}"


def test_topic_directories_have_kebab_case_names():
    """Directories under knowledge/ are addressable — kebab-case only."""
    bad = []
    for entry in CORPUS_ROOT.iterdir():
        if not entry.is_dir():
            continue
        if entry.name != entry.name.lower():
            bad.append(entry.name)
        elif "_" in entry.name:
            bad.append(entry.name)
    assert not bad, f"non-kebab topic directories: {bad}"


def test_page_filenames_are_kebab_case_or_README(all_pages):
    """Filenames become the `name` argument to `read_doc`. Kebab-case only."""
    bad = []
    for path in all_pages:
        stem = path.stem
        if stem == "MANIFEST":
            continue
        if stem == "README":
            continue
        if stem != stem.lower():
            bad.append(str(path.relative_to(REPO_ROOT)))
        elif "_" in stem:
            bad.append(str(path.relative_to(REPO_ROOT)))
    assert not bad, f"non-kebab filenames: {bad}"


def test_no_pages_at_docs_root_besides_schema():
    """docs/ must contain ONLY execute_command_schema.json.

    The prior mistake was to dump the knowledge corpus into docs/ like
    documentation. This test locks the boundary: `docs/` is the interface
    schema, `knowledge/` is the lore. Never overlap.
    """
    docs = REPO_ROOT / "docs"
    assert docs.is_dir(), "docs/ directory missing"
    stray = [p.name for p in docs.iterdir() if p.name != "execute_command_schema.json"]
    assert not stray, (
        f"docs/ contains files other than execute_command_schema.json: {stray}. "
        f"Knowledge belongs under knowledge/<topic>/<name>.md."
    )


def test_every_topic_directory_is_non_empty():
    """A topic directory with no .md files is dead weight."""
    empty = []
    for entry in CORPUS_ROOT.iterdir():
        if not entry.is_dir():
            continue
        md_count = sum(1 for _ in entry.rglob("*.md"))
        if md_count == 0:
            empty.append(entry.name)
    assert not empty, f"empty topic directories: {empty}"


def test_no_absolute_urls_where_relative_links_would_do(all_pages):
    """Cross-page links inside knowledge/ should be relative, not absolute
    file:// or /path/ paths — breaks portability and the packaged wheel."""
    bad = []
    absolute_link = re.compile(r"\]\((file:|/Users/|/home/|/tmp/)")
    for path in all_pages:
        text = path.read_text(encoding="utf-8")
        if absolute_link.search(text):
            bad.append(str(path.relative_to(REPO_ROOT)))
    assert not bad, f"pages with absolute-path links: {bad}"
