```
 ██╗   ██╗ ██████╗ ███████╗██████╗ ███████╗██████╗
 ██║   ██║╚════██╗██╔════╝██╔══██╗██╔════╝██╔══██╗
 ██║   ██║ █████╔╝███████╗██████╔╝█████╗  ██████╔╝
 ╚██╗ ██╔╝ ╚═══██╗╚════██║██╔═══╝ ██╔══╝  ██╔══██╗
  ╚████╔╝ ██████╔╝███████║██║     ███████╗██║  ██║
   ╚═══╝  ╚═════╝ ╚══════╝╚═╝     ███████╗╚═╝  ╚═╝
```

# M0MA-V3SP3R — AI-driven Flipper Zero control from Android

M0MA-V3SP3R (Vesper) is an Android app that pairs an OpenRouter-hosted LLM with a Flipper Zero over BLE. The AI issues structured commands; the phone enforces risk, permissions, and approvals; the Flipper does the hardware work. You talk (or type, or use smart glasses), and the app runs the resulting SubGHz / IR / NFC / BadUSB / GPIO / BLE-recon action against your device with a human-in-the-loop safety layer.

This repository is a fork of [elder-plinius/V3SP3R](https://github.com/elder-plinius/V3SP3R) that merges the agentic loop and autonomous-campaign features from [FlipperAgent](https://github.com/charlesreid1/FlipperAgent) into the base Vesper app. See [`MERGE_PLAN_FlipperAgent_into_M0MA-V3SP3R.md`](MERGE_PLAN_FlipperAgent_into_M0MA-V3SP3R.md) for the chunk-by-chunk merge history.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

---

## What actually works today

This section is deliberately factual — everything below is backed by code in this repo. See [What's experimental or opt-in](#whats-experimental-or-opt-in) for features that ship disabled or behind a flag.

### Interactive chat with tool calling
- One conversation surface in `ChatScreen`. Text, voice input, and photo attachments all funnel through `VesperAgent` → `OpenRouterClient`.
- Model output is constrained to a single `execute_command` tool schema — no ad-hoc execution paths, no fan-out (`MAX_TOOL_CALLS_PER_RESPONSE = 1`).
- Client-side rate limiter: 30 requests / minute.
- Voice input uses **on-device** Android speech recognition (`SpeechRecognitionHelper`); audio does not leave the phone.
- Text-to-speech playback of AI responses uses OpenRouter's audio-capable models (`OpenRouterTtsService`) — no separate TTS key needed.
- Photo attachments are preprocessed through a cheap vision model (Gemini Flash by default, falling back to `gemini-2.5-flash` / `gpt-4o-mini`) that returns a text description. The primary tool-calling model only ever sees the description string, not the image bytes.

### Hardware control (via `CommandAction` enum, `Command.kt`)
Every action goes through `CommandExecutor` and is risk-classified by `RiskAssessor` before it runs.

- **Filesystem** — list, read, write (with diff), create directory, delete (recursive optional), move, rename, copy.
- **Device info** — `get_device_info`, `get_storage_info`, `get_system_info`.
- **SubGHz** — transmit from file, passive receive with duration, decode RAW_Data.
- **Infrared** — transmit named signal, transmit raw waveform (duty cycle configurable), passive receive.
- **NFC / RFID / iButton** — emulate from file, NFC detect / field, RFID read / write (`EM4100`, `HIDProx`, …), iButton emulate.
- **BadUSB** — generate (LLM-authored DuckyScript), validate, diff against existing, write to `/ext/badusb/`, execute.
- **GPIO** — read pin, set pin, set mode (input / output). `PA7`, `PC3`, etc.
- **BLE recon** — using the **phone's** Bluetooth adapter, not the Flipper's: scan targets, enumerate GATT, read / write / subscribe characteristic. Managed by `BleServiceManager` and `BleReconService`, separate from `FlipperBleService`.
- **BLE spam** — mode string passed via `app_args` (e.g. `apple`).
- **Music** — write a Flipper Music Format file and launch Music Player.
- **Peripherals** — LED (RGB 0–255), vibro on/off.
- **CLI escape hatch** — `execute_cli` for anything not covered by a typed action. Sub-classified against `SAFE_CLI_PREFIXES` (auto-execute) and `MEDIUM_CLI_PREFIXES` (one confirm); everything unmatched → HIGH-risk.

### App discovery and content
- **FapHub browser** — search and install `.fap` apps (`search_faphub`, `install_faphub_app`).
- **Resource browser** — search community catalogs, browse GitHub repos, download to a Flipper path (`search_resources`, `browse_repo`, `download_resource`, `github_search`).
- **Vault** — `list_vault` for filtered inventory of stored signals/scripts.
- **Runbooks** — pre-canned diagnostic / smoke-test sequences (`run_runbook`, currently used by Ops Center).

### Vulnerability triage
- `vuln_submit` → assigns UUID, persists to `VulnTriageService`.
- `vuln_validate` → mark reproduced / not reproduced, append notes.
- `vuln_list` → filter by severity / status.
- `vuln_classify` → reclassify an existing finding.
- Backed by a Room table; no cloud sync.

### Audit and skills
- **Audit log** — every command, result, and approval decision is logged to Room (`AuditService`). Queryable from the app or via the `audit_query` tool action (limit + risk level filter).
- **Loadable skills** — 7 methodology playbooks bundled under `app/src/main/assets/skills/` (`ble-exploitation`, `campaign`, `payload-authoring`, `pentest-report`, `protocol-analysis`, `signal-analysis`, `wifi-attack`). The model pulls a skill on demand via `load_skill`; keeps the system prompt small.

### Ops Center
Reads live pipeline health from the BLE layer:
- Firmware profile (Official / Unleashed / RogueMaster / Xtreme via `FirmwareCompatibilityProfile`)
- CLI readiness (`CliCapabilityStatus`: PROBING / READY / DEGRADED / UNAVAILABLE)
- Command pipeline autotune (`CommandPipelineAutotuneStatus`) — measures MTU + round-trip and adjusts chunk sizing
- Connection diagnostics report — bundled snapshot for logs
- Runbook launcher for recovery / smoke-tests

### Labs
Consolidated bottom-nav slot that hubs three sub-surfaces:
- **Alchemy Lab** — hand-authored / visually edited SubGHz-IR-NFC signal construction via `ForgeEngine`, validated by `ForgeValidator` before write.
- **Payload Lab** — LLM-authored BadUSB / SubGHz / IR / NFC payloads. Same validate-before-ship pattern.
- **Campaigns** (experimental — see below).

### Smart glasses (Mentra)
- Voice + camera pipeline through a WebSocket bridge server (`mentra-bridge/`, Node.js).
- Wake word: "Hey Vesper" (native mode) or explicit tap (relay mode).
- Vision triggers ("what am I looking at", "scan this", …) request an on-demand photo.
- TTS echo suppression so the mic doesn't re-transcribe the glasses' own speaker.
- Wire protocol and setup: [`mentra-bridge/README.md`](mentra-bridge/README.md).

---

## What's experimental or opt-in

### Ralph — autonomous campaigns (feature-flagged off by default)

The **Ralph** loop manager is ported from FlipperAgent. It runs multi-phase autonomous engagements against a scoped target list:

**RECON → RESEARCH → ENUMERATE → EXPLOIT → REPORT**

Each phase runs in its own `WorkManager` worker with a fresh LLM conversation. State passes between phases via a Room `campaign_finding` table, not via LLM context.

- **Off by default.** The `RALPH_ENABLED` DataStore key defaults to `false`. `RalphOrchestrator.startCampaign` refuses to schedule work while the flag is off.
- **Kill switch.** `RalphOrchestrator.stopAllCampaigns` is wired to a settings toggle and pauses every running campaign.
- **Scope enforcement.** Every campaign carries an `in_scope` / `out_of_scope` target list. `RiskAssessor` refuses out-of-scope targets *before* normal risk classification and returns BLOCKED regardless of the action's normal tier.
- **HIGH-risk always pauses.** Neither `AUTONOMOUS_SAFE` nor `AUTONOMOUS_TRUSTED` mode ever auto-executes a HIGH-risk action; the campaign transitions to `AWAITING_APPROVAL` and waits for a human.
- **Approval Inbox** UI collects paused HIGH-risk actions across all running campaigns.
- **Exploit gate.** The EXPLOIT phase requires an additional explicit user go-ahead before it runs at all.
- **Convergence detection.** Three consecutive no-new-findings iterations → stop.
- **Rate caps.** Per-phase iteration limit, wall-clock cap, and tool-call cap.
- **Notifications.** Campaign state changes surface via `CampaignNotifications`.

To enable: Settings → **Experimental** → **Ralph autonomous campaigns**. **Only run against systems you own or have explicit written authorization to test.**

### Other opt-ins
- **Auto-approve per risk tier** — `autoApproveMedium` / `autoApproveHigh` in Settings. HIGH auto-approve is bypassed by Ralph's HIGH-always-pauses invariant, but applies for interactive chat.
- **Sailor-mouth mode** on the glasses bridge — toggles voice-response tone.

---

## Safety model

Vesper's safety story is **layered**, not a single check:

1. **Prompt-level.** `VesperPrompts.SYSTEM_PROMPT` instructs the model to refuse clearly malicious requests. This is a soft hint — a jailbroken or swapped-out model can bypass it.
2. **Executor-level (the actual enforcement).** Every action flows through `CommandExecutor` → `RiskAssessor` → approval gate. The model cannot bypass any of it, because it never touches BLE / filesystem / CLI directly.

Risk tiers:
- **LOW** — read-only ops. Auto-execute.
- **MEDIUM** — mutations with limited blast radius. Single-tap "Approve" button, with a diff preview for writes. Auto-executes when the assessment sets neither `requiresDiff` nor `requiresConfirmation`. `autoApproveMedium` can suppress the prompt.
- **HIGH** — destructive or exfiltration-adjacent. **Double-tap to confirm** (second tap within 1.8 s). `autoApproveHigh` bypasses the prompt for interactive chat; Ralph campaigns ignore it and always pause.
- **BLOCKED** — protected system paths (`/int/…`, firmware areas), sensitive extensions (`.key`, `.priv`, `.secret`), and out-of-campaign-scope targets. Refused until unlocked in Settings → **Permissions**.

Escalation: `WRITE_FILE`, `CREATE_DIRECTORY`, `COPY`, and `PUSH_ARTIFACT` (of `fap` / `app` / `executable` artifacts) are bumped up a tier when the target path is outside the currently permitted scope.

All decisions, approvals, and command results are written to the Room-backed audit log.

**Full details:** [`docs/architecture.md`](docs/architecture.md) — including the on-device / cloud data-flow boundary, the multimodal preprocessing pipeline, and the CLI prefix allowlists.

---

## Requirements

| Item | Notes |
|------|-------|
| **Flipper Zero** | Any recent firmware. Momentum / Unleashed / RogueMaster / Xtreme add features that the app auto-detects via `FirmwareCompatibilityProfile`. |
| **Android device** | Android 8.0+ (API 26). Bluetooth + Location permissions required (Location is what Android requires for BLE scanning). |
| **OpenRouter account** | [openrouter.ai](https://openrouter.ai). Pay-per-use; conversations typically cost cents. |
| **Optional: Mentra glasses** | For hands-free operation. See [`mentra-bridge/README.md`](mentra-bridge/README.md). |

---

## AI models

Vesper works with any tool-calling model on OpenRouter. Model IDs change often — check `openrouter.ai/models` for current names.

| Family | Notes |
|--------|-------|
| **Nous Hermes** | Purpose-built for agentic tool-use. Top pick for power users. |
| **Anthropic Claude Sonnet / Opus** | Sonnet is the safest default; Opus for hard reasoning. |
| **Anthropic Claude Haiku** | Fast + cheap for simple reads and quick device queries. |
| **OpenAI GPT-4-class** | Solid general-purpose alternative. |

`OpenRouterClient.buildToolModelCandidates` walks a fallback list of tool-capable models automatically and caches per-model tool-support results for 5 minutes, so a temporarily unavailable pick isn't fatal.

---

## Build & install

```bash
git clone https://github.com/charlesreid1/M0MA-V3SP3R.git
cd M0MA-V3SP3R
```

Open in [Android Studio](https://developer.android.com/studio), let Gradle sync, then **Build > Build APK(s)**. APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Command-line (requires Android SDK + JDK 17+):

```bash
./gradlew assembleDebug
```

First launch:
1. Grant Bluetooth + Location + Notifications permissions.
2. Settings → paste your OpenRouter API key. Stored in encrypted DataStore.
3. Device tab → Scan → tap your Flipper. Make sure Flipper Bluetooth is ON and no other device (e.g. qFlipper) is holding the connection.
4. Chat tab → start talking.

---

## App structure

Bottom navigation is four tabs. Everything else is reachable through them.

| Tab | What lives there |
|-----|------------------|
| **Chat** | Main conversational surface. Audit log is a sub-screen. |
| **Labs** | Alchemy Lab, Payload Lab, Campaigns list, New Campaign, Campaign detail, Approval Inbox. |
| **Device** | BLE connection management, Ops Center, File Browser, FapHub, Signal Arsenal, Spectral Oracle. |
| **Settings** | API key, permissions, auto-approve toggles, Ralph feature flag, smart-glasses bridge URL. |

Repository layout (Kotlin sources):

```
app/src/main/java/com/vesper/flipper/
├── ai/                     # OpenRouterClient, VesperAgent, VesperPrompts, PayloadEngine, FlipperToolExecutor
├── ble/                    # FlipperBleService, FlipperProtocol, FlipperFileSystem, MarauderBridge,
│                           # BleServiceManager, BleReconService, FirmwareCompatibilityProfile,
│                           # CliCapabilityStatus, CommandPipelineAutotuneStatus, ConnectionDiagnosticsReport
├── glasses/                # GlassesIntegration, GlassesBridgeClient
├── voice/                  # SpeechRecognitionHelper, OpenRouterTtsService
├── domain/
│   ├── executor/           # CommandExecutor, RiskAssessor, ForgeEngine, ForgeValidator
│   ├── ralph/              # RalphOrchestrator + 5 PhaseWorker subclasses + ApprovalCleanupWorker
│   ├── model/              # Command, Campaign, Permission, ...
│   ├── service/            # AuditService, DiffService, PermissionService, SkillRegistry, VulnTriageService
│   └── protocol/           # SubGHz, Pwnagotchi
├── data/                   # SettingsStore (encrypted DataStore), VesperDatabase (Room)
├── security/               # Input validation, sanitization
├── ui/                     # Jetpack Compose screens, viewmodels, components
└── widget/                 # Home screen widget

app/src/main/assets/skills/  # Bundled methodology playbooks (SKILL.md per subdirectory)
mentra-bridge/               # Smart-glasses relay server (Node.js, TypeScript)
docs/                        # Architecture doc + auto-generated command schema
```

---

## Command schema

The full `execute_command` schema — every action, every arg, with descriptions and examples — is auto-generated from the `CommandAction` enum. Canonical copy:

- [`docs/execute_command_schema.json`](docs/execute_command_schema.json) — JSON Schema Draft-07, mirrors `com.vesper.flipper.domain.model.ExecuteCommand`.

The schema regenerator runs as a Gradle task so drift between the enum and the schema is impossible.

---

## Troubleshooting

<details>
<summary><strong>Flipper not found when scanning</strong></summary>

1. On Flipper: Settings > Bluetooth > make sure it's ON.
2. Toggle Bluetooth off/on on your phone.
3. Make sure Flipper isn't connected to another device (e.g. qFlipper).
4. Move within ~1 m.
5. Check that Location permission is granted (Android requires it for BLE scanning).
</details>

<details>
<summary><strong>Build failed in Android Studio</strong></summary>

1. Ensure JDK 17+ is installed.
2. File > Sync Project with Gradle Files.
3. Build > Clean Project > Rebuild Project.
4. If still failing: close Android Studio, delete `.gradle` folder, reopen.
</details>

<details>
<summary><strong>AI not responding</strong></summary>

1. Verify your OpenRouter API key in Settings.
2. Check your OpenRouter credit balance at [openrouter.ai](https://openrouter.ai).
3. Check internet connection.
4. Try a different model — some may be temporarily unavailable, or may not support tool use (Vesper caches that decision for 5 minutes and falls back automatically).
</details>

<details>
<summary><strong>"Could not parse tool arguments" errors</strong></summary>

The model returned malformed JSON. `OpenRouterClient` includes automatic JSON repair, but some models are more reliable than others. Try:
1. Tap **Retry** on the error message.
2. Switch to a Nous Hermes or Anthropic Claude Sonnet model.
3. Simplify your request.
</details>

<details>
<summary><strong>Permission denied errors</strong></summary>

- Protected paths (`/int/…`, firmware areas) and sensitive extensions (`.key`, `.priv`, `.secret`) are BLOCKED by default.
- Settings > **Permissions** to unlock specific paths.
- `autoApproveMedium` / `autoApproveHigh` in Settings move faster when you trust the workflow.
- Ralph campaigns always pause on HIGH regardless of the auto-approve setting.
</details>

<details>
<summary><strong>Ralph doesn't do anything</strong></summary>

Ralph is off by default. Settings > **Experimental** > **Ralph autonomous campaigns**. Then create a campaign from Labs > Campaigns. Every campaign requires an explicit in-scope target list.
</details>

---

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — full architecture, data-flow boundary, risk classification details, per-feature backing services.
- [`docs/execute_command_schema.json`](docs/execute_command_schema.json) — auto-generated command schema.
- [`mentra-bridge/README.md`](mentra-bridge/README.md) — smart-glasses bridge wire protocol and setup.
- [`MERGE_PLAN_FlipperAgent_into_M0MA-V3SP3R.md`](MERGE_PLAN_FlipperAgent_into_M0MA-V3SP3R.md) — the chunk-by-chunk plan used to merge FlipperAgent into this fork.
- [`SECURITY.md`](SECURITY.md) — vulnerability disclosure.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contribution guidelines.

---

## Safety & legal

- Vesper is a tool for **education and legitimate security research**.
- Only use on devices you own or have explicit written authorization to test. This applies with particular force to Ralph autonomous campaigns and BLE recon against third-party devices.
- All AI actions are logged and auditable.
- The system prompt instructs the model to refuse clearly malicious requests; the executor (`CommandExecutor` + `RiskAssessor` + `PermissionService` + `ProtectedPaths`) is the enforcement backstop.
- Destructive operations require explicit user confirmation (double-tap on HIGH-risk actions).
- You are responsible for complying with all applicable laws in your jurisdiction.

---

## License

GPL-3.0 — see [LICENSE](LICENSE).

Upstream V3SP3R is © the original elder-plinius repository authors under the same GPL-3.0 license. FlipperAgent contributions are merged under the same license.
