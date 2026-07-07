# Vesper Architecture — What's Local vs. What Goes to the AI

Vesper (V3SP3R) is an Android app that turns a Flipper Zero into an AI-driven hardware lab. This document captures the overall architecture and, in particular, **the data-flow boundary between on-device logic and the remote AI model.**

## The Overall Pattern

The whole design turns on a single principle, stated in `VesperPrompts.kt` under the heading "Command-Reality Separation":

> **"You issue commands; Android enforces security."** The model never touches BLE or raw device primitives. It only issues structured commands through the `execute_command` interface.

That collapses to three layers with a one-way pipe between them:

```
┌────────────────────────────────────────────────────────┐
│  CLOUD (OpenRouter → some LLM)                         │
│  Sees: system prompt, chat history, tool schema,       │
│         text descriptions of images, JSON tool results │
└──────────────▲──────────────────────────┬──────────────┘
   HTTPS to openrouter.ai/api/v1/…        │ tool call:
   Bearer <user's OpenRouter key>         │ execute_command({action, args,
                                          │   justification, expected_effect})
┌──────────────┴──────────────────────────▼──────────────┐
│  ANDROID (local - the "reality enforcer")              │
│  VesperAgent  →  CommandExecutor  →  RiskAssessor      │
│                  ├─ PermissionService (scoped grants)  │
│                  ├─ DiffService (shown to user)        │
│                  ├─ Approval UI (medium/high risk)     │
│                  └─ AuditService (Room DB)             │
└──────────────────────────┬─────────────────────────────┘
                           │ FlipperProtocol frames
                           │ over GATT serial characteristics
┌──────────────────────────▼─────────────────────────────┐
│  FLIPPER ZERO (BLE peripheral)                         │
└────────────────────────────────────────────────────────┘
```

The AI has no BLE stack, no filesystem handle, no CLI. It only knows how to emit `execute_command` JSON. Everything past that is the Kotlin side's problem, and every command is filtered through the same funnel.

## Core Principles

1. **Command-Reality Separation** — You issue commands; Android enforces security. The model never touches BLE or raw device primitives.
2. **One command interface** — All operations go through a single, well-defined `execute_command` tool. No ad-hoc execution paths.
3. **Diffs before writes** — Any modification to an existing file shows a diff before execution.
4. **Confirm only when necessary** — Reads and safe writes happen silently. Destructive operations require confirmation (medium: diff review; high: 1.5s hold-to-confirm).
5. **Everything is logged** — All agent actions are auditable and replayable via the Room-backed audit log.

## What Stays Local — Never Leaves the Phone

- **BLE / GATT traffic.** `FlipperBleService` (a foreground `Service`) owns the `BluetoothGatt`, discovers Flipper's serial TX/RX characteristics, and frames traffic through `FlipperProtocol`. The model has no awareness this exists.
- **Raw Flipper responses.** CLI stdout, raw `.sub`/`.ir`/`.nfc` bytes, RAW_Data samples — none of it is streamed to OpenRouter. Only a `CommandResult` is echoed back, and even that is trimmed (e.g. `FlipperToolExecutor.executeFlipperCommand` takes only the first non-blank line, capped at 160 chars, for the "message" preview, though full output is included in the `data` map).
- **Risk classification.** `RiskAssessor` runs on-device. The model can *suggest* an action, but the tier (LOW/MEDIUM/HIGH/BLOCKED) is decided locally and cannot be overridden by prompt.
- **Path locks & sensitive extensions.** `RiskAssessor`, using the `ProtectedPaths` table in `RiskLevel.kt`, gates `/int/`, firmware paths, and sensitive extensions (`.key`, `.priv`, `.secret`). `PermissionService` is a separate layer that tracks scoped, time-limited grants issued by the user. The model can request them; the phone refuses.
- **Approval decisions.** Medium-risk shows a diff (`DiffService`); high-risk requires a 1.5s hold-to-confirm. The model never sees the approval UI state directly — it just gets a tool result saying "approved and executed" or "rejected."
- **The user's OpenRouter API key.** Held in an encrypted DataStore (`SettingsStore`) and only used to set the `Authorization: Bearer …` header.
- **Audit log & chat history.** `Room` database (`VesperDatabase`, `ChatDao`, `AuditService`). Sessions can be replayed offline.
- **Voice.** `SpeechRecognitionHelper` uses on-device Android speech recognition — the audio itself doesn't hit OpenRouter, only the transcribed text does. (`ElevenLabsTtsService` does upload text for TTS, but that's a separate flow.)
- **Image bytes past preprocessing.** The user's photo is first sent to a fast vision model (Gemini Flash 2.0 by default) for a text description; the primary tool-calling model then receives *only that description string* — see the multimodal boundary section below.
- **The Flipper's `raw_data` in signals.** Even though `.sub` files are readable, RAW_Data payloads are handled by `ForgeEngine` / signal-processing code locally when possible.

## What Gets Sent to the AI (OpenRouter → the LLM)

Every request goes to `https://openrouter.ai/api/v1/chat/completions` with a body assembled by `OpenRouterClient.buildToolCallingRequest` (`OpenRouterClient.kt:437`). Contents:

1. **System prompt** — `VesperPrompts.SYSTEM_PROMPT` (~300 lines) describing the Flipper path structure, risk tiers, file-format templates (SubGHz, IR, BadUSB), safety boundaries, and the required JSON envelope. Appends `SMARTGLASSES_ADDENDUM` when glasses are enabled.
2. **Trimmed conversation history** — last **24 messages only** (`MAX_CONTEXT_MESSAGES = 24` at `OpenRouterClient.kt:1399`). Older turns are dropped from the wire; they stay in the local Room DB.
3. **One tool schema** — `EXECUTE_COMMAND_TOOL` (`OpenRouterClient.kt:1473`). A single function with an enum of ~30 allowed actions (`list_directory`, `read_file`, `subghz_transmit`, `forge_payload`, `request_photo`, etc.). No provider-side tool proliferation — one funnel.
4. **Tool results from prior turns** — the JSON-serialized `CommandResult` from `formatResult()` (`OpenRouterClient.kt:1241`). This is where the model learns what happened: file listings, decoded file contents, device info, CLI output snippets, error messages. **This is the leakiest part of the pipe.**
5. **Image descriptions, not images** — see the multimodal boundary section below.
6. **HTTP headers** — `Authorization: Bearer <user key>`, plus `HTTP-Referer: https://vesper.flipper.app` and `X-Title: Vesper Flipper Control` (OpenRouter attribution).

Rate-limited to 30 req/min locally (`RateLimiter`) and capped at 1 tool call per response (`MAX_TOOL_CALLS_PER_RESPONSE = 1`) — so the AI can't fan out actions in a single turn.

## The Multimodal Boundary

`OpenRouterClient.preprocessImagesAsText` at line 191 is the key trick and worth copying for other hardware:

- User attaches a photo of, e.g., a car remote.
- The phone sends **just that image + a fixed vision prompt** to a cheap vision model (`google/gemini-2.0-flash-001`, falling back to `gemini-2.5-flash` or `gpt-4o-mini`).
- The returned text description ("Black plastic key fob, model XYZ, 4 buttons, FCC ID …") replaces the image attachment in the conversation.
- The primary tool-calling model (Hermes 4, Claude Sonnet, etc.) sees only text.

This means (a) the primary model doesn't need vision, (b) sensitive image content only touches one specific provider, and (c) the primary model gets a normalized, task-scoped view. Smart glasses `request_photo` uses the same pipeline via `describeImageForAgent`.

## The Command Envelope

Every AI-issued action is defined inline as `EXECUTE_COMMAND_TOOL` in `OpenRouterClient.kt` (there is no separate schema JSON file) and enforced by the tool-call handling in `OpenRouterClient` and by `CommandExecutor`:

```json
{
  "action": "read_file",
  "args": { "path": "/ext/subghz/Garage.sub" },
  "justification": "User asked to change the frequency - must read first.",
  "expected_effect": "Return current file contents including Frequency field."
}
```

The `justification` and `expected_effect` fields are the discipline-inducing part. They're logged into the audit trail *before* execution, so the model has to state intent, and the user can see it later. `VesperPrompts` also enforces a hard "maximum 1 command per response" and a "read-verify-write" pattern.

## Component Reference

### Presentation Layer

- **ChatScreen** — Main interaction point with the AI agent (voice, images, text)
- **FileBrowserScreen** — Direct file system navigation
- **DeviceScreen** — BLE connection management and device info
- **AuditScreen** — View and filter action history
- **SettingsScreen** — Configure API keys, permissions, and preferences

### Domain Layer

- **VesperAgent** — Orchestrates the conversation flow and command execution
- **OpenRouterClient** — LLM API, tool calling, JSON repair, image preprocessing, model fallback
- **CommandExecutor** — Chokepoint every action passes through; wires risk assessment, permissions, diff, approval, and audit. Entry point: `CommandExecutor.execute` at `CommandExecutor.kt:51`
- **RiskAssessor** — Classifies operations by risk level; owns the protected-path/extension rules
- **PermissionService** — Tracks scoped, time-limited grants the user issues
- **DiffService** — Computes file diffs for write operations
- **AuditService** — Logs all actions for accountability
- **ForgeEngine** — Local payload/signal generation

### Data Layer

- **FlipperFileSystem** — High-level file operations
- **FlipperProtocol** — Frame-based serial protocol implementation
- **FlipperBleService** — BLE connection and GATT operations
- **VesperDatabase** — Room database for audit logs and chat history (`ChatDao`, `AuditDao`)
- **SettingsStore** — Encrypted DataStore for preferences and the OpenRouter API key

## Risk Classification Flow

```
Command Received
       │
       ▼
┌──────────────────┐
│  Risk Assessment │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌──────┐  ┌───────┐
│ LOW  │  │BLOCKED│──────────► Reject with reason
└──┬───┘  └───────┘
   │
   ▼
Execute immediately
   │
   └────────────────┐
                    │
    ┌───────────────┴───────────────┐
    │                               │
    ▼                               ▼
┌───────┐                       ┌──────┐
│MEDIUM │                       │ HIGH │
└───┬───┘                       └──┬───┘
    │                              │
    ▼                              ▼
Show diff                   Hold-to-confirm
    │                              │
    ▼                              ▼
User clicks               User holds button
"Apply"                      1.5 seconds
    │                              │
    └──────────────┬───────────────┘
                   │
                   ▼
              Execute command
                   │
                   ▼
              Log to audit
```

## BLE Communication

The app communicates with Flipper Zero over BLE using the device's serial profile:

1. **Discovery** — Scan for devices advertising the Flipper service UUID
2. **Connection** — Connect to selected device with GATT
3. **Service Discovery** — Find serial TX/RX characteristics
4. **Notifications** — Enable notifications on RX characteristic
5. **Protocol** — Frame-based communication via `FlipperProtocol`

## Security Considerations

- API keys stored in encrypted DataStore (`SettingsStore`)
- Permissions are scoped and time-limited via `PermissionService`
- Protected paths (`/int/`, firmware) and sensitive extensions (`.key`, `.priv`, `.secret`) are BLOCKED by `RiskAssessor` until explicitly unlocked in Settings
- All operations are logged to the Room audit table via `AuditService`
- Hold-to-confirm prevents accidental destructive actions
- No raw BLE access from the AI model — every action funnels through `execute_command` → `CommandExecutor`
- Rate limiter: 30 req/min to OpenRouter; 1 tool call per model response
- Multimodal input is text-normalized before reaching the primary reasoning model

## Applying This Pattern to Other Hardware

Transferable pieces if you're porting this to a different device:

1. **A single narrow tool** — one `execute_command` function with an enum of allowed actions, not one tool per operation. Keeps model output predictable, keeps the trust surface tiny.
2. **A `RiskAssessor` on-device** — the classification is data (`RiskLevel` per action + path rules), not model judgment. Never let the model self-classify.
3. **A `CommandExecutor` chokepoint** — one function every action passes through, so audit/permission/diff/approval logic isn't duplicated.
4. **Result formatting is intentional** — `formatResult` decides what goes back into context. Don't just echo raw device output; shape it into a compact JSON structure with `success`, `message`, and a small `data` map.
5. **Multimodal → text bridge** — if the hardware has any sensor/camera surface, add a preprocessing step so the primary reasoning model only sees text. Cheaper, safer, model-portable.
6. **`justification` + `expected_effect` fields** — cheap to add, hugely valuable for audit logs and user trust.
7. **Model fallback list** — `buildToolModelCandidates` walks a list of tool-capable models and caches which ones don't support tool-use for 5 minutes. Nice pattern for OpenRouter-mediated setups.

The one thing to be aware of when copying: **tool results are where sensitive device state actually crosses the boundary**. `formatResult` currently serializes the whole `CommandResult`, so directory listings, file contents from `read_file`, and CLI output all end up in the LLM's context. If you're targeting hardware with more sensitive contents (credentials, health data, etc.), that's the exact function you'll want to redact through.

## Dependencies

- **Jetpack Compose** — Declarative UI
- **Hilt** — Dependency injection
- **Room** — Local database (audit + chat history)
- **DataStore** — Encrypted preferences storage
- **OkHttp** — Network requests
- **Kotlinx Serialization** — JSON parsing
- **java-diff-utils** — Diff computation
