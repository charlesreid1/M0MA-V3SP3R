---
name: campaign
description: "Run a sustained security assessment campaign against Flipper-reachable targets — quantitative phase gates, target prioritization, and session-resume protocol"
---

# Campaign

A campaign is a real penetration test executed through Vesper's tools against real hardware. Every claim in a campaign traces back to a tool call recorded in `audit_entries` and a finding recorded in `vuln_findings`. If neither exists, the claim is fabrication.

Load this skill on any prompt matching: "start a campaign", "run an engagement", "pentest this", "assess these targets", "sweep the area", "what's around us", or when resuming prior work in this project.

## Non-negotiables

| # | Rule | Enforcement |
|---|------|-------------|
| 1 | Every finding must reference at least one `audit_entries` row (tool call ID). No tool call → no finding. | Self-check before `vuln_submit`; refuse if no audit ID |
| 2 | Tool output is authoritative. Do not augment device lists, RSSI values, service UUIDs, or bytes with values you "expect". | Compare draft finding text against the raw tool result before submit |
| 3 | Skill content is only valid when loaded via `load_skill` in this session. Do not quote skill rules from memory. | If you need a rule, call `load_skill` first, then cite |
| 4 | Scope is whatever the operator authorized at campaign start. New protocol, new network segment, new frequency band = expansion → ask. | See §Scope-expansion gate |
| 5 | Any TRANSMIT / WRITE / INJECT / EMULATE goes through Vesper's RiskAssessor. Do not attempt to bypass, batch-approve, or pre-authorize. | RiskAssessor blocks automatically; do not argue with it |

## Session-resume protocol

The database is the campaign state — chat context is not. On **every** new session, before doing anything else:

1. `vuln_list()` — pull all prior findings for this project
2. `audit_query(limit=200)` — last 200 tool calls
3. Compare to the current user prompt. Answer these before proposing next actions:
   - What phase was the previous session in? (infer from tool mix — see §Phase inference)
   - Are there any findings with `status = submitted` that were never validated? Those are the resumption candidates.
   - Are there targets in `audit_entries` that never got a `vuln_submit`? Those are dropped threads.
4. Present a one-paragraph campaign state summary, then ask the user which thread to pick up (unless they've already said).

Skip this only if the user's first message is unambiguously "start a fresh campaign".

### Phase inference from audit mix

| Recent audit signature (last 20 calls) | Likely phase |
|----------------------------------------|--------------|
| Mostly `*_scan_targets`, `*_receive`, `*_detect` | RECON |
| `github_search`, `browse_repo`, `search_resources`, `load_skill` | RESEARCH |
| `ble_enumerate`, `ble_read_char`, `ble_subscribe`, `subghz_decode_raw` | ENUMERATE |
| `*_write`, `*_transmit`, `*_emulate`, `badusb_execute`, `vuln_validate` | EXPLOIT |
| `vuln_list`, `audit_query`, `write_file` in `/ext/reports/` | REPORT |

## The five phases

The methodology is fixed regardless of target protocol. You may re-enter an earlier phase (e.g., ENUMERATE reveals a new target → return to RESEARCH for that target), but you cannot skip forward.

---

### Phase 1 — RECON

**Objective:** Enumerate every RF-reachable target across every modality Vesper exposes.

**Required calls (execute in parallel where the hardware allows):**

| Call | Args | Notes |
|------|------|-------|
| `ble_scan_targets` | `duration=15` | Second pass at `duration=30` if <3 devices found |
| `subghz_receive` | `frequency=433920000`, then `315000000`, then `868000000`, each `duration=10` | Skip a band if operator said out-of-scope |
| `nfc_detect` | — | Passive tag detect |
| `nfc_field` | — | Reader-field detect (different physical prompt: hover near reader) |
| `rfid_read` | — | 125 kHz card in field |
| `ir_receive` | `duration=10` | Only if an IR remote is being pressed nearby |

**Phase-exit gate — ALL must be true:**
- Every in-scope modality has ≥1 call recorded in `audit_entries` this session
- Zero-result modalities are explicitly noted ("no BLE devices in range" is a valid finding for RECON completeness)
- Every discovered target has been added to the target list (see §Target tracking)

**Do NOT `vuln_submit` in RECON unless the discovery itself is the vulnerability** (e.g., a device broadcasting PII in its advertisement name). RECON produces *targets*, not findings. Track targets via §Target tracking.

**Risk:** LOW. No approval required.

---

### Phase 2 — RESEARCH

**Objective:** For each target above the priority threshold, gather OSINT and prior work.

**Required calls per target (top-N by priority score):**

| Call | Purpose |
|------|---------|
| `github_search(command="<vendor> <model> <protocol>", search_scope="code")` | Prior reverse engineering |
| `github_search(command="<service UUID>", search_scope="code")` | Known BLE service semantics |
| `browse_repo(url="…")` | Follow up on the highest-signal hit |
| `search_resources(query="<vendor> firmware")` | Community datasets |
| `load_skill(command="protocol-analysis")` or `signal-analysis` or `ble-exploitation` | Load domain expertise before ENUMERATE |

**Phase-exit gate:**
- Each researched target has at minimum: vendor guess, protocol identified, ≥1 GitHub search performed, and either a listed known-vuln (with CVE / advisory link) or an explicit "no prior work found"
- Skills relevant to the target set are loaded

**Do NOT** `vuln_submit` for a CVE at this phase — mark it as a research note only. A CVE is a *possible* finding (see §Confidence). It becomes *likely* / *confirmed* only in ENUMERATE / EXPLOIT.

**Risk:** LOW. No approval required.

---

### Phase 3 — ENUMERATE

**Objective:** For each researched target, map attack surface. Every readable byte, every writable characteristic, every observable state.

**Required calls per BLE target:**
- `ble_enumerate(address=…)` — full GATT map
- `ble_read_char(address=…, uuid=…)` for every characteristic with `read` property
- `ble_subscribe(address=…, uuid=…, duration=10)` for every characteristic with `notify` property

**Required calls per Sub-GHz target:**
- `subghz_decode_raw(path=<capture from RECON>)` — protocol / fixed-vs-rolling determination
- Second capture of the same trigger → if identical bytes, fixed code confirmed → replay is in-play for EXPLOIT

**Required calls per NFC/RFID target:**
- `nfc_read` for full sector read (or vendor-specific equivalent)
- `rfid_read` twice — UID stability check

**Vulnerability submission rules:**

| Observation | `vuln_type` | Severity band |
|-------------|-------------|---------------|
| Characteristic with `write` or `write_without_response` and no pairing required | `writable_ble` | HIGH |
| Characteristic exposing serial number, credentials, or PII on read | `info_disclosure_ble` | MEDIUM |
| Sub-GHz fixed code confirmed (two identical captures) | `replay_subghz` | HIGH |
| NFC/RFID low-security card (MIFARE Classic default keys, EM4100 clone-able) | `weak_credential_rf` | HIGH |
| Advertised default / factory MAC or name pattern | `default_creds` (after research confirms default) | MEDIUM |

Use `vuln_classify(vuln_type=…)` if the mapping is uncertain. Do not invent new `vuln_type` strings.

**Phase-exit gate:**
- Every priority-≥50 target has a completed enumeration record
- Every enumerated weakness has a `vuln_submit` with confidence = **likely** (default) or **possible** (if inferred, not observed)
- A prioritized exploit list exists — highest-severity findings first, but weighted down by expected user-approval cost

**Risk:** MEDIUM. Vesper prompts per connection unless auto-approve-medium is on.

---

### Phase 4 — EXPLOIT

**Objective:** Convert *likely* findings into *confirmed* findings by demonstrating impact.

**Pre-exploit checklist — must be complete for each attempt:**
1. Finding exists in `vuln_findings` with confidence = *likely*
2. Exploit hypothesis written in-chat: "If I call X with Y, I expect Z"
3. Blast radius stated: what could go wrong for the target, for adjacent devices, for the operator?
4. Skill loaded (`ble-exploitation`, `payload-authoring`, `signal-analysis`, etc.)
5. User has approved the specific action in the current session

**Execution loop per finding:**
```
1. CALL the exploit tool (ble_write_char / subghz_transmit / etc.)
2. OBSERVE the tool result verbatim
3. CALL vuln_validate(vuln_id=…, reproduced=<bool>, notes="<what happened>")
4. If reproduced=false: revise hypothesis or mark finding false_positive; do not retry blindly
5. If reproduced=true: consider what new target this reveals (see §Target expansion)
```

**Rate limits:**
- Max 1 HIGH-risk action per 60 s per target
- Max 3 failed exploit attempts against one finding before pausing and reassessing
- If the same tool call fails 2× with the same error, stop and diagnose — do not retry a third time

**Risk:** MEDIUM–HIGH. RiskAssessor gates every transmit/write/inject.

---

### Phase 5 — REPORT

**Objective:** Produce a report backed entirely by `vuln_findings` + `audit_entries`.

**Required calls:**
1. `vuln_list()` — full finding set
2. `vuln_list(min_severity="high")` — for the exec summary
3. `audit_query(limit=500)` — timeline for methodology section
4. `load_skill(command="pentest-report")` — structural template
5. Draft the report in chat
6. Optional: `write_file(path="/ext/reports/campaign_<date>.md", content=…)` to persist to the Flipper

**Every finding in the report must include:**
- `vuln_id` from `vuln_findings`
- Confidence tier (see §Confidence)
- At least one audit_entries reference (tool call that produced the evidence)
- Remediation recommendation

**Do NOT** include findings whose evidence you cannot cite from the database. If it isn't in `vuln_findings`, cut it from the report.

**Risk:** LOW.

---

## Confidence tiers

Assigned when calling `vuln_submit` and updated by `vuln_validate`. Used verbatim in the report.

| Tier | Definition | How it's assigned |
|------|------------|-------------------|
| **Confirmed** | Exploit tool call succeeded and produced the predicted effect | `vuln_validate(reproduced=true)` |
| **Likely** | Enumeration observed the weakness directly but exploit not attempted | Default at `vuln_submit` after ENUMERATE |
| **Possible** | Inferred from research (CVE, protocol weakness) but not observed on this target | Set explicitly at `vuln_submit` with note = "research-only" |

Never mark a finding *confirmed* without a corresponding `vuln_validate(reproduced=true)` audit entry.

## Target tracking

RECON produces targets, not findings. A target has: `identifier` (MAC / frequency+timestamp / UID), `modality`, `priority_score`.

Persist targets by submitting a lightweight finding only when the target advances beyond RECON. During RECON, keep the target list in a chat-visible table so the user can review it before Phase 2 kicks off.

### Target priority score

Compute per target after RECON. Enumerate the top ~5 first; below 30, skip unless the user asks.

```
priority_score = 0
+30  if vendor/product identifiable from advertisement / signal fingerprint
+20  if RSSI ≥ -70 dBm (strong signal, stable connection likely)
+20  if research surfaces ≥1 known vulnerability for the vendor/model
+15  if target category is high-impact (lock, camera, medical, industrial)
+10  if target is unique in class (only one device of its kind in the scan)
+ 5  if target appeared in two independent recon passes (persistence)
-15  if target is transient (seen once, gone on rescan) — probably passer-by
-20  if RSSI < -85 dBm (edge of range, unreliable enumeration)
-25  if likely out-of-scope (public infrastructure, neighbor's device) — ask before proceeding
```

Bands: `≥60` enumerate first · `30–59` enumerate if time permits · `<30` skip.

## Scope-expansion gate

An action counts as expansion (→ ask before proceeding) if **any** of:
- New RF band or protocol not in the initial authorization
- New physical target discovered during exploitation (e.g., firmware leaked WiFi creds → the WiFi AP is now a candidate)
- Network segment reachable only via a compromised device
- Any target where the owner is not the engagement's authorizing party

Recording a finding about the out-of-scope target is fine — that's data. Probing it is not.

## When a phase produces nothing

| Situation | Action |
|-----------|--------|
| RECON returns 0 devices across all modalities | Re-run at a different location / time before declaring the environment empty. Do not proceed to RESEARCH with an empty target list. |
| RESEARCH finds no prior work on any target | Continue to ENUMERATE — enumeration is often the *reason* research is thin |
| ENUMERATE finds no writable / weak surface on any target | Report the negative result honestly. A campaign with only *possible* findings is a valid outcome. |
| EXPLOIT: every attempt fails | Do not fabricate a positive result. Downgrade findings to *likely* / *possible* as appropriate and report. |

## Approval reference

Vesper's `RiskAssessor` is authoritative. This table is a hint for what to expect — do not use it to justify skipping a prompt.

| Category | Risk | Approval |
|----------|------|----------|
| Scan / detect / receive (all `*_scan_targets`, `*_detect`, `*_receive`) | LOW | None |
| Read-only queries (`vuln_list`, `audit_query`, `vuln_classify`, `load_skill`) | LOW | None |
| Connect + read (`ble_enumerate`, `ble_read_char`, `ble_subscribe`) | MEDIUM | Prompt unless auto-approve-medium is on |
| Transmit / emulate (`subghz_transmit`, `ir_transmit*`, `nfc_emulate`, `rfid_emulate`, `badusb_execute` on existing script) | MEDIUM–HIGH | Hold-to-confirm |
| Write / clone (`ble_write_char`, `rfid_write`, `badusb_write` + `badusb_execute` chained) | HIGH | Hold-to-confirm |
| Protected paths (`/int/`, `*.key`, `*.priv`) | BLOCKED | Refused by policy |

## Related skills

Load with `execute_command(action="load_skill", args={"command": "<id>"})`:

| Skill | Load when |
|-------|-----------|
| `protocol-analysis` | Decoding an unknown RF or BLE protocol |
| `signal-analysis` | Sub-GHz capture needs fixed-vs-rolling determination or timing analysis |
| `ble-exploitation` | About to write to a GATT characteristic or bypass pairing |
| `payload-authoring` | Generating BadUSB / Evil Portal / IR / Sub-GHz payloads |
| `wifi-attack` | Post-compromise pivot to WiFi surface (ESP32 Marauder path) |
| `pentest-report` | Phase 5 report structure |
