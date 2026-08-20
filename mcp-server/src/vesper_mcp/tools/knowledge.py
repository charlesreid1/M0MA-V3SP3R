from __future__ import annotations

from pathlib import Path

from ..runtime import err, knowledge_root, ok


def _md_files(root: Path) -> list[Path]:
    files: list[Path] = []
    docs = root / "docs"
    if docs.is_dir():
        files.extend(sorted(p for p in docs.glob("*.md") if p.is_file()))
    readme = root / "README.md"
    if readme.is_file():
        files.append(readme)
    return files


def _topic_id(path: Path) -> str:
    return path.stem.lower().replace("_", "-")


def _index(root: Path) -> dict[str, Path]:
    return {_topic_id(p): p for p in _md_files(root)}


async def list_topics() -> dict:
    """List every documentation topic available in the corpus.

    Returns {"ok": true, "data": [{"topic": str, "path": str, "bytes": int}, ...]}.
    Topics are derived from *.md files under docs/ plus the top-level README.md.
    Topic ids are lowercase, hyphenated file stems (e.g. app-build-process)."""
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    return ok([
        {"topic": tid, "path": str(p.relative_to(root)), "bytes": p.stat().st_size}
        for tid, p in _index(root).items()
    ])


async def read_doc(topic: str) -> dict:
    """Return the full body of one documentation topic.

    `topic` is a topic id from list_topics (e.g. "architecture", "campaigns",
    "labs", "app-build-process", "readme"). Case-insensitive. Underscore and
    hyphen are interchangeable."""
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    tid = topic.lower().replace("_", "-")
    idx = _index(root)
    if tid not in idx:
        return err(f"unknown topic {topic!r}; try list_topics()", code="unknown_topic")
    return ok({"topic": tid, "content": idx[tid].read_text(encoding="utf-8")})


async def search_docs(query: str, limit: int = 20) -> dict:
    """Substring search across the corpus (case-insensitive).

    Returns up to `limit` hits, each with topic, 1-indexed line number, and
    the matching line stripped of trailing whitespace."""
    if not query:
        return err("query must be non-empty", code="bad_query")
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    needle = query.lower()
    hits: list[dict] = []
    for tid, path in _index(root).items():
        for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if needle in line.lower():
                hits.append({"topic": tid, "line": i, "text": line.rstrip()})
                if len(hits) >= limit:
                    return ok(hits)
    return ok(hits)
