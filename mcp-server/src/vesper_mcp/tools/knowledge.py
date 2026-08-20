from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from ..runtime import err, knowledge_root, ok


@dataclass(frozen=True)
class LoreFile:
    topic: str
    name: str
    path: Path


def _corpus_dir(root: Path) -> Path:
    return root / "knowledge"


def _iter_lore(root: Path) -> list[LoreFile]:
    corpus = _corpus_dir(root)
    if not corpus.is_dir():
        return []
    out: list[LoreFile] = []
    for md in sorted(corpus.rglob("*.md")):
        rel = md.relative_to(corpus)
        parts = rel.parts
        if len(parts) == 1:
            # Root-level file (e.g. MANIFEST.md) — surface as topic "_root".
            topic = "_root"
            name = md.stem
        else:
            topic = parts[0]
            name = "/".join(parts[1:])[:-3]  # strip .md
        out.append(LoreFile(topic=topic, name=name, path=md))
    return out


def _find_lore(root: Path, topic: str, name: str) -> LoreFile | None:
    for lf in _iter_lore(root):
        if lf.topic == topic and lf.name == name:
            return lf
    return None


async def list_topics() -> dict:
    """List every topic in the corpus and the files under each.

    Corpus lives under `knowledge/` at the repo root. Each subdirectory is
    a topic (e.g. `subghz`, `marauder`, `firmware`), and each `.md` file
    inside is a named page under that topic (e.g. `subghz/protocols`).
    Read one with `read_doc(topic, name)`.
    """
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    topics: dict[str, list[str]] = {}
    for lf in _iter_lore(root):
        topics.setdefault(lf.topic, []).append(lf.name)
    return ok({
        "root": str(_corpus_dir(root)),
        "topic_count": len(topics),
        "file_count": sum(len(v) for v in topics.values()),
        "topics": topics,
    })


async def read_doc(topic: str, name: str = "README") -> dict:
    """Return the full body of one lore file.

    `topic` is a topic directory (e.g. `subghz`, `marauder`). `name` is a
    file name inside that directory without `.md` (defaults to `README`).
    Underscore and hyphen are treated interchangeably.
    """
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    t = topic.lower().replace("_", "-")
    n = name.replace("_", "-")
    lf = _find_lore(root, t, n)
    if lf is None:
        return err(
            f"unknown lore file topic={topic!r} name={name!r}; try list_topics()",
            code="unknown_topic",
        )
    return ok({
        "topic": lf.topic,
        "name": lf.name,
        "path": str(lf.path.relative_to(root)),
        "content": lf.path.read_text(encoding="utf-8"),
    })


async def search_docs(query: str, limit: int = 20) -> dict:
    """Substring search across the corpus (case-insensitive).

    Returns up to `limit` hits. Each hit carries topic, name, 1-indexed
    line number, and the matching line stripped of trailing whitespace.
    """
    if not query:
        return err("query must be non-empty", code="bad_query")
    try:
        root = knowledge_root()
    except FileNotFoundError as e:
        return err(str(e), code="corpus_missing")
    needle = query.lower()
    hits: list[dict] = []
    for lf in _iter_lore(root):
        try:
            text = lf.path.read_text(encoding="utf-8")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), start=1):
            if needle in line.lower():
                hits.append({
                    "topic": lf.topic,
                    "name": lf.name,
                    "line": i,
                    "text": line.rstrip(),
                })
                if len(hits) >= limit:
                    return ok(hits)
    return ok(hits)
