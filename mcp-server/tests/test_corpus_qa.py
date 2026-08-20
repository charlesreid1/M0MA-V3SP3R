"""Gold-standard Q/A regression tests for the M0MA-V3SP3R corpus.

Mirrors PHR34CKER5's `test_qa_corpus.py`. Each test is a concrete
factual question a DEFCON / village judge might ask, bound to a
specific substring the corpus MUST answer with. When someone edits a
page and drifts a value the plan promises, one of these tests goes
red and points at the drift.

This is not a test of the assistant's language ability — it's a test
that the numbers, chip names, and part numbers in the pages still
match what plan-knowledge-expand.md §3 says they should.

Source ground-truth: plan-knowledge-expand.md, ST datasheets
(STM32WB55, ST25R3916), TI CC1101 datasheet, Momentum wiki, Marauder
wiki. Each assertion is a plain substring — case-sensitive unless
noted — because these strings are cited technical facts, not
narrative prose that reshuffles freely.
"""

from __future__ import annotations

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CORPUS_ROOT = REPO_ROOT / "knowledge"


def _read(topic: str, name: str) -> str:
    path = CORPUS_ROOT / topic / f"{name}.md"
    assert path.is_file(), f"missing page {path.relative_to(REPO_ROOT)}"
    return path.read_text(encoding="utf-8")


def _assert_contains(topic: str, name: str, needles: list[str]) -> None:
    body = _read(topic, name)
    missing = [n for n in needles if n not in body]
    assert not missing, (
        f"{topic}/{name}.md is missing expected content: {missing!r}"
    )


# --- Flipper hardware — the MCU + radios --------------------------------------


def test_flipper_hardware_names_the_stm32wb55():
    """Success criterion §9.1: 'What chip does the Flipper's SubGHz radio
    use, and what's its frequency range?' must be answerable."""
    _assert_contains(
        "flipper-hardware", "README",
        ["STM32WB55", "CC1101", "ST25R3916"],
    )


def test_flipper_hardware_documents_dual_core():
    body = _read("flipper-hardware", "README")
    assert "Cortex-M4" in body and "Cortex-M0" in body, (
        "flipper-hardware/README must document the STM32WB55 dual-core layout"
    )


def test_subghz_frequency_windows_are_explicit():
    """SubGHz page must state the three CC1101 windows."""
    body = _read("subghz", "README")
    # Accept en-dash or hyphen; the plan uses "300-348, 387-464, 779-928 MHz".
    for range_marker in ("300", "348", "387", "464", "779", "928"):
        assert range_marker in body, (
            f"subghz/README missing frequency-window marker {range_marker!r}"
        )
    assert "CC1101" in body


def test_nfc_page_states_13_56_mhz():
    body = _read("nfc", "README")
    assert "13.56" in body, "nfc/README must state the 13.56 MHz HF carrier"


def test_rfid_page_states_125_khz():
    body = _read("rfid", "README")
    assert "125 kHz" in body or "125kHz" in body, (
        "rfid/README must state the LF carrier is 125 kHz"
    )


def test_rfid_page_covers_key_card_types():
    _assert_contains("rfid", "README", ["EM4100", "HID", "T5577"])


def test_ibutton_page_names_ds1990a_and_1_wire():
    body = _read("ibutton", "README")
    assert "1-Wire" in body or "1-wire" in body.lower()
    assert "DS1990A" in body


# --- Firmware ecosystem — the four families ------------------------------------


def test_firmware_families_names_all_four():
    """Success criterion §9.2: 'What's Momentum and how does it differ
    from stock?' must be answerable."""
    _assert_contains(
        "firmware", "families",
        ["Official", "Momentum", "Unleashed", "RogueMaster"],
    )


def test_momentum_is_flagged_as_m0ma_priority():
    body = _read("firmware", "momentum")
    # Look for the "M0MA priority" or "primary M0MA target" framing that
    # plan-knowledge-expand.md §3 promises.
    assert (
        "M0MA" in body
        or "primary target" in body
        or "primary firmware" in body.lower()
    ), "firmware/momentum should explicitly call out its M0MA priority status"


def test_momentum_page_covers_worldwide_subghz():
    """Success criterion §9.5: 'Can the Flipper transmit on 868 MHz in the
    US, and what changes with Momentum?' — the answer needs the region
    story documented on momentum."""
    body = _read("firmware", "momentum")
    assert "region" in body.lower() or "worldwide" in body.lower()
    assert "SubGHz" in body


def test_compatibility_profile_explains_detection():
    """The Vesper FirmwareCompatibilityProfile is what routes CLI verbs by
    firmware. The page must document `device_info` probing."""
    body = _read("firmware", "compatibility-profile")
    assert "device_info" in body, (
        "firmware/compatibility-profile must document device_info probing"
    )


# --- SubGHz protocols ---------------------------------------------------------


def test_subghz_protocols_covers_named_families():
    """Success criterion §9.4 requires KeeLoq. Plan §3 also promises the
    other well-known family names."""
    body = _read("subghz", "protocols")
    required = ["Princeton", "CAME", "KeeLoq", "Security+", "Somfy"]
    missing = [n for n in required if n not in body]
    assert not missing, (
        f"subghz/protocols is missing named families: {missing}"
    )


def test_keeloq_notes_rolling_code():
    """§9.4: forging a KeeLoq packet requires the reader to know it's a
    rolling code with the specific Microchip HCS parts."""
    body = _read("subghz", "protocols")
    assert "rolling" in body.lower()
    assert "HCS" in body, "KeeLoq entry must reference the Microchip HCS parts"


def test_sub_format_documents_key_and_raw_data():
    """§9.6: distinguishing `.sub` with `Protocol=Princeton` from `RAW_Data`
    is exactly the drift alarm here."""
    body = _read("subghz", "sub-format")
    for needle in ("Filetype", "Frequency", "Preset", "Protocol", "RAW_Data"):
        assert needle in body, (
            f"subghz/sub-format must document `.sub` field {needle!r}"
        )


def test_ir_format_documents_parsed_vs_raw():
    body = _read("ir", "ir-format")
    assert "parsed" in body.lower()
    assert "raw" in body.lower()


def test_nfc_format_documents_atqa_sak_uid():
    body = _read("nfc", "nfc-format")
    for needle in ("ATQA", "SAK", "UID"):
        assert needle in body, (
            f"nfc/nfc-format must document field {needle!r}"
        )


# --- Marauder — the WiFi devboard ---------------------------------------------


def test_marauder_names_the_esp32():
    """Success criterion §9.3: 'Which pins on the GPIO header does Marauder
    use?' + §9.8 require Marauder pages to be substantive on the ESP32
    and its role."""
    body = _read("marauder", "README")
    assert "ESP32" in body


def test_marauder_wiring_references_gpio_pins():
    body = _read("marauder", "wiring")
    # Wiring page must at least mention TX/RX/GND/3V3 lines.
    for pin_hint in ("TX", "RX", "GND", "3V3"):
        assert pin_hint in body, (
            f"marauder/wiring must reference pin/rail {pin_hint!r}"
        )


def test_marauder_commands_covers_scan_attack_groups():
    """§9.8 requires the Marauder command doc to distinguish scan / attack /
    utility categories with concrete verbs."""
    body = _read("marauder", "commands")
    # Concrete verbs from the mainline command set.
    for verb in ("scanap", "sniffbeacon", "deauth"):
        assert verb in body, (
            f"marauder/commands must document verb {verb!r}"
        )


def test_marauder_commands_flags_legality():
    """Every Marauder capability page ends with a legal note per plan §2.4."""
    body = _read("marauder", "commands")
    assert "legal" in body.lower() or "law" in body.lower() or "authoriz" in body.lower(), (
        "marauder/commands must carry a legal/safety note"
    )


# --- GPIO -----------------------------------------------------------------------


def test_gpio_pinout_lists_the_18_pin_header():
    body = _read("flipper-gpio", "pinout")
    assert "18" in body, "flipper-gpio/pinout must state the header is 18 pins"
    # A pinout doc without any pin identifiers is meaningless.
    assert "3V3" in body or "3.3V" in body
    assert "GND" in body


def test_gpio_extensions_documents_the_wifi_devboard():
    body = _read("flipper-gpio", "extensions")
    assert "Wi-Fi" in body or "WiFi" in body or "wifi" in body.lower()
    assert "ESP32" in body


# --- Storage / CLI -----------------------------------------------------------


def test_storage_page_documents_int_and_ext():
    body = _read("flipper-storage", "README")
    assert "/int" in body
    assert "/ext" in body


def test_storage_page_lists_expected_subdirs():
    body = _read("flipper-storage", "README")
    for subdir in ("/ext/subghz", "/ext/nfc", "/ext/badusb"):
        assert subdir in body, (
            f"flipper-storage/README must reference {subdir}"
        )


def test_cli_page_covers_command_verb_categories():
    body = _read("flipper-cli", "README")
    for verb in ("storage", "subghz", "device_info"):
        assert verb in body, (
            f"flipper-cli/README must document CLI verb category {verb!r}"
        )


# --- Development pages -------------------------------------------------------


def test_fap_apps_page_documents_furi_and_ufbt():
    """§9.7: 'How do I write a new .fap app?' — the answer must name
    FURI (Flipper's RTOS) and uFBT."""
    body = _read("development", "fap-apps")
    assert "FURI" in body
    assert "uFBT" in body or "ufbt" in body.lower()


def test_firmware_build_page_names_fbt_target():
    """Plan §3 Group E: firmware target for the Flipper Zero is `f7`."""
    body = _read("development", "firmware-build")
    assert "fbt" in body.lower()
    assert "f7" in body


def test_js_runner_is_momentum_specific():
    body = _read("development", "js-runner")
    assert "Momentum" in body


# --- Legal / safety ----------------------------------------------------------


def test_legal_page_covers_the_big_regimes():
    """Plan §3 Group G: FCC (US), ETSI (EU), CFAA / access-control,
    intentional-interference — the legal page must cite each."""
    body = _read("legal", "README")
    for regime in ("FCC", "ETSI"):
        assert regime in body, f"legal/README must reference {regime}"
    # CFAA or "unauthorized" — either satisfies the CFAA-adjacent framing.
    assert "CFAA" in body or "unauthorized" in body.lower()


# --- Vesper positioning ------------------------------------------------------


def test_architecture_page_documents_risk_tiers():
    body = _read("vesper", "architecture")
    # The Vesper risk tiers are LOW / MEDIUM / HIGH / BLOCKED.
    for tier in ("LOW", "MEDIUM", "HIGH", "BLOCKED"):
        assert tier in body, (
            f"vesper/architecture must document risk tier {tier!r}"
        )


def test_mcp_page_documents_three_transports():
    body = _read("vesper", "mcp")
    for transport in ("stdio", "sse", "streamable-http"):
        assert transport in body, (
            f"vesper/mcp must document transport {transport!r}"
        )


def test_campaigns_page_documents_ralph_phases():
    body = _read("vesper", "campaigns").lower()
    # Ralph's five phases per FlipperAgent — the page may render them as
    # ALL-CAPS or Title Case, so match case-insensitive.
    phases = ["recon", "research", "enumerate", "exploit", "report"]
    missing = [p for p in phases if p not in body]
    assert not missing, (
        f"vesper/campaigns must document Ralph phases; missing {missing}"
    )
