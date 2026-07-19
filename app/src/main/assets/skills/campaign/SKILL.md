---
name: campaign
description: "Run a sustained security assessment campaign — a real pentest, not a simulation"
---

# Campaign — What It Is

A campaign is a **real penetration test**. Not a simulation. Not a description of what you would do. You have real tools connected to real hardware. You CALL those tools, collect real results, and make real findings.

A campaign progresses through five phases. Each phase uses specific tools to produce specific evidence. You cannot skip phases. You cannot fabricate results. If you didn't call a tool, you don't have data.

## The Iron Rules

1. **CALL tools, don't describe them.** You have the Vesper `execute_command` tool available. When this document says "scan for BLE devices," that means CALL `execute_command(action="ble_scan_targets", args={"duration": 15})`. Do NOT write "I would run ble_scan_targets and expect to find..." — that is fabrication.

2. **LOAD skills before using them.** You can load additional expertise on demand via `execute_command(action="load_skill", args={"command": "<id>"})`. Do NOT reference skill content from memory — load it.

3. **Results come from tools, not from you.** If `ble_scan_targets` returns 3 devices, you have 3 devices. If it returns 0, you have 0. Do NOT add devices you "expect" to see. Do NOT embellish tool output.

4. **PERSIST findings via `vuln_submit`.** Your text output is ephemeral — it dies when the session ends. Findings must be persisted using `vuln_submit`. If it's not in the vuln store, it doesn't exist.

5. **ASK before dangerous actions.** Any tool that TRANSMITS (`subghz_transmit`, `ir_transmit`, `ir_transmit_raw`), WRITES to hardware (`ble_write_char`, `rfid_write`, `nfc_emulate`), INJECTS (`badusb_execute`), or is HIGH-risk requires explicit user approval. Vesper's RiskAssessor will automatically pause for these — don't try to route around it.

6. **STAY in scope.** Only target what the operator authorized. If you discover something outside scope, report it via `vuln_submit` but do NOT probe it without asking.

## The Five Phases

Every campaign follows this progression. The target protocol doesn't matter — BLE, WiFi, SubGHz, NFC, RFID, or a mix. The methodology is always the same.

### Phase 1: RECON (Passive Discovery)

**Goal:** Find everything in range. Cast a wide net across all RF modalities.

**What you do:**
- CALL `ble_scan_targets(duration=15)` — discover BLE devices (name, address, RSSI, services)
- CALL `subghz_receive(frequency=433920000)` at common frequencies (also 315000000, 868000000) — capture RF signals
- CALL `nfc_detect` — check for NFC tags in proximity
- CALL `nfc_field` — detect nearby NFC readers
- CALL `rfid_read` — check for 125kHz RFID tags
- CALL `ir_receive` — capture nearby IR signals

**Risk level:** LOW. All passive or minimally active. No approval needed.

**You are done when:** You've scanned every modality available to you and recorded what's there (including "nothing found" — absence is data).

**Output:** For any device or signal worth attacking, call `vuln_submit` right now with a low-severity placeholder finding so the target is tracked. Upgrade severity in ENUMERATE / EXPLOIT.

### Phase 2: RESEARCH (Intelligence Gathering)

**Goal:** For each target found in recon, gather OSINT and known vulnerabilities.

**What you do:**
- CALL `github_search(command="<vendor> <model> BLE", search_scope="code")` — find prior reverse engineering
- CALL `browse_repo` / `search_resources` — check community resource repos
- Look up FCC IDs, Bluetooth SIG entries, protocol documentation
- LOAD the relevant skill if one exists (e.g., `load_skill(command="protocol-analysis")`)

**Risk level:** LOW. No interaction with targets.

**You are done when:** Each target has a research profile: known vulns, protocol documentation, attack surface assessment, and a prioritized list of what to enumerate.

### Phase 3: ENUMERATE (Active Probing)

**Goal:** Deep-probe each target to map every service, characteristic, entry point, and potential vulnerability.

**What you do:**
- CALL `ble_enumerate(address=…)` — map all GATT services and characteristics per BLE device
- CALL `ble_read_char(address=…, uuid=…)` — read every readable characteristic
- CALL `ble_subscribe(address=…, uuid=…, duration=10)` — monitor notification characteristics
- CALL `subghz_decode_raw(path=…)` — analyze captured signals (fixed code? rolling code?)

**Risk level:** MEDIUM. You are connecting to targets and actively probing. Vesper will confirm each connection.

**You are done when:** Every target has a complete profile: services, characteristics (with properties like read/write/notify), signal analysis, identified vulnerabilities, and a list of attack vectors to try.

**Output:** For every confirmed weakness, `vuln_submit` with the appropriate vuln_type (`writable_ble`, `writable_characteristic`, `default_creds`, etc.). Use `vuln_classify(vuln_type=…)` if unsure about severity mapping.

### Phase 4: EXPLOIT (Controlled Attacks)

**Goal:** Demonstrate real impact by executing proof-of-concept exploits against confirmed vulnerabilities.

**What you do — Vesper will require user approval for each HIGH-risk action:**
- CALL `ble_write_char(...)` — write to characteristics lacking authentication (⚠️ HIGH — approval required)
- CALL `subghz_transmit(path=…)` — replay captured signals (⚠️ MEDIUM — approval required unless auto-approve-medium on)
- CALL `nfc_emulate(path=…)` — emulate cloned NFC credentials (⚠️ MEDIUM)
- CALL `rfid_emulate(path=…)` — emulate cloned RFID tags (⚠️ MEDIUM)
- CALL `rfid_write(key_type=…, key_data=…)` — clone RFID tags (⚠️ HIGH — approval required)
- CALL `badusb_execute(path=…)` — HID injection (⚠️ HIGH — approval required)
- LOAD skills as needed: `load_skill(command="ble-exploitation")`, `load_skill(command="payload-authoring")`

**Risk level:** MEDIUM–HIGH. Every action in this phase can cause physical-world effects. You MUST:
1. Explain exactly what you're about to do (via `justification`)
2. State the expected effect (via `expected_effect`)
3. State what could go wrong in the surrounding chat message
4. Wait for Vesper's approval prompt to resolve
5. After execution: `vuln_validate(vuln_id=…, reproduced=true/false, notes=…)` to update the finding's status

**You are done when:** All feasible attack vectors have been attempted (or deliberately skipped with documented rationale). Each exploit attempt is recorded via `vuln_validate`.

### Phase 5: REPORT (Documentation)

**Goal:** Produce a professional pentest report.

**What you do:**
- CALL `vuln_list` (optionally filtered by severity) to pull the full finding set
- CALL `audit_query(limit=200)` to recover the tool-call timeline
- LOAD `load_skill(command="pentest-report")` for report structure
- Write the report body directly into the chat, structured with: executive summary, methodology, findings table (severity + confidence), evidence, attack chains, and remediation recommendations
- Optionally CALL `write_file(path="/ext/reports/final_report.md", content=…)` to persist the report to the Flipper

**Risk level:** LOW. No target interaction.

**Confidence levels for findings:**
- **Confirmed** — you called a tool and directly observed the vulnerability (e.g., `ble_write_char` succeeded without auth, then `vuln_validate(..., reproduced=true)`)
- **Likely** — strong evidence from enumeration but not exploited (e.g., writable characteristic found but write not attempted, `vuln_submit` recorded but never validated)
- **Possible** — theoretical based on research (e.g., firmware version has known CVE but not tested)

## Campaign State

State persists across sessions in the fork's Room database:
- `vuln_findings` table — every finding, filterable via `vuln_list`
- `audit_entries` table — every tool call, queryable via `audit_query`

No JSON files on disk are needed — the database is the campaign state.

The state effectively tracks:
- Discovered targets (via `target` field on findings)
- Findings with severity and confidence (via `status`: submitted / confirmed / rejected / false_positive)
- Reproduction attempts (via `reproductionAttempts` counter)
- Validation notes (appended per `vuln_validate`)

## Target Expansion

When attacking one target reveals new ones, the campaign grows:
- BLE device leaks WiFi credentials → new target
- NFC badge cloned → access control system is a new target
- Firmware reveals API keys → cloud service is a new target

Add new targets by submitting fresh findings with the new target identifier. Always ask before expanding scope to a new protocol or network segment.

## Approval Summary

Vesper's `RiskAssessor` enforces this automatically — don't try to route around it:

| Action Category | Risk | Approval Required |
|-----------------|------|-------------------|
| Scan/detect (`ble_scan_targets`, `nfc_detect`, `rfid_read`, `subghz_receive`, `ir_receive`, `nfc_field`) | LOW | No — execute freely |
| Read-only data (`vuln_list`, `vuln_classify`, `audit_query`, `load_skill`) | LOW | No |
| Connect + read (`ble_enumerate`, `ble_read_char`, `ble_subscribe`) | MEDIUM | Yes — Vesper prompts (unless auto-approve-medium is on) |
| Sub-GHz TX, IR TX, NFC/RFID emulate, BadUSB launch | MEDIUM/HIGH | Yes — hold-to-confirm |
| BLE write, RFID write, badusb execute | HIGH | Yes — hold-to-confirm |
| Internal paths, key files, secrets (`/int/`, `*.key`, `*.priv`) | BLOCKED | Refused — never do this |
