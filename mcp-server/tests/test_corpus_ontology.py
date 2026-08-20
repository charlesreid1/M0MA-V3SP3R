"""Ontology + coverage tests for the corpus.

Mirrors PHR34CKER5's `test_plan_ontology_is_fully_covered` /
`test_region_era_coverage` pattern: the plan
(`plan-knowledge-expand.md`) promises certain topics and certain
canonical pages. If someone renames or drops one, this test lights up.

The corpus doesn't have typed records like PHR34CKER5, so ontology
here means: which topic directories must exist, and which canonical
pages under each. Each canonical page ties to a Group A-H entry in
plan-knowledge-expand.md §3.
"""

from __future__ import annotations

from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
CORPUS_ROOT = REPO_ROOT / "knowledge"


# Group → topic directories the plan promises. Missing here means the
# corpus lost a Group A-H slice from plan-knowledge-expand.md.
PLAN_TOPICS = frozenset({
    # Group A — Flipper Zero hardware & platform
    "flipper-hardware",
    "flipper-gpio",
    "flipper-storage",
    "flipper-cli",
    # Group B — Firmware ecosystem
    "firmware",
    # Group C — RF subsystems
    "subghz",
    "ir",
    "nfc",
    "rfid",
    "ibutton",
    # Group D — Marauder
    "marauder",
    # Group E — Extension & development
    "development",
    # Group F — Methodology playbooks
    "skills",
    # Group G — Legal & safety
    "legal",
    # Group H — Vesper positioning
    "vesper",
})


def test_every_plan_topic_directory_exists():
    have = {p.name for p in CORPUS_ROOT.iterdir() if p.is_dir()}
    missing = PLAN_TOPICS - have
    assert not missing, (
        f"plan-knowledge-expand.md topics missing from knowledge/: {sorted(missing)}"
    )


def test_no_surprise_topic_directories():
    """Every top-level topic must be one of the plan's — additions need a
    plan-knowledge-expand.md update."""
    have = {p.name for p in CORPUS_ROOT.iterdir() if p.is_dir()}
    extra = have - PLAN_TOPICS
    assert not extra, (
        f"unrecognized topic directories (add to plan-knowledge-expand.md, "
        f"then update PLAN_TOPICS): {sorted(extra)}"
    )


# Canonical `<topic>/<name>` pages the plan promises. Each entry maps to
# a specific §3 line in plan-knowledge-expand.md.
CANONICAL_PAGES = [
    # Group A
    ("flipper-hardware", "README"),
    ("flipper-gpio", "pinout"),
    ("flipper-gpio", "extensions"),
    ("flipper-storage", "README"),
    ("flipper-cli", "README"),
    # Group B — Momentum is the M0MA priority target
    ("firmware", "families"),
    ("firmware", "momentum"),
    ("firmware", "compatibility-profile"),
    ("firmware", "updating"),
    # Group C
    ("subghz", "README"),
    ("subghz", "protocols"),
    ("subghz", "sub-format"),
    ("ir", "README"),
    ("ir", "ir-format"),
    ("nfc", "README"),
    ("nfc", "nfc-format"),
    ("rfid", "README"),
    ("ibutton", "README"),
    # Group D — Marauder is the M0MA WiFi priority
    ("marauder", "README"),
    ("marauder", "firmware"),
    ("marauder", "wiring"),
    ("marauder", "commands"),
    # Group E
    ("development", "fap-apps"),
    ("development", "firmware-build"),
    ("development", "js-runner"),
    # Group F — the seven synced skills
    ("skills", "README"),
    ("skills", "ble-exploitation"),
    ("skills", "campaign"),
    ("skills", "payload-authoring"),
    ("skills", "pentest-report"),
    ("skills", "protocol-analysis"),
    ("skills", "signal-analysis"),
    ("skills", "wifi-attack"),
    # Group G
    ("legal", "README"),
    # Group H
    ("vesper", "architecture"),
    ("vesper", "campaigns"),
    ("vesper", "labs"),
    ("vesper", "app-build-process"),
    ("vesper", "mcp"),
]


@pytest.mark.parametrize("topic,name", CANONICAL_PAGES)
def test_canonical_page_exists(topic: str, name: str):
    """Each plan-promised page must be present. Rename = red test."""
    path = CORPUS_ROOT / topic / f"{name}.md"
    assert path.is_file(), (
        f"canonical page {topic}/{name} missing (expected at "
        f"{path.relative_to(REPO_ROOT)}). If renamed, update the "
        f"CANONICAL_PAGES list AND plan-knowledge-expand.md §3."
    )


def test_manifest_lists_every_topic():
    """MANIFEST.md must reference every topic directory — otherwise
    users browsing from the TOC can't discover it."""
    manifest = (CORPUS_ROOT / "MANIFEST.md").read_text(encoding="utf-8")
    missing = []
    for topic in sorted(PLAN_TOPICS):
        # Match either `topic/` or `knowledge/topic` — MANIFEST is inside
        # knowledge/ so relative refs typically look like `topic/name`.
        if f"{topic}/" not in manifest and f"`{topic}`" not in manifest:
            missing.append(topic)
    assert not missing, f"MANIFEST.md does not reference topics: {missing}"
