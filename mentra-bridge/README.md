# mentra-bridge

WebSocket relay server that connects V3SP3R (the Android app) to smart glasses — either directly, or through the MentraOS cloud SDK.

```
[Glasses] ──(MentraOS Cloud)──> [mentra-bridge] ──(WebSocket)──> [V3SP3R Android]
```

Nothing in this bridge is stateful across restarts. It holds live connections and a small amount of session state (wake-count, echo-suppression timers, sailor-mouth toggle) in memory, and drops it all on process exit.

## Modes

The server picks a mode from environment variables at startup:

- **Standalone relay (default).** No `MENTRA_API_KEY`. Glasses and the Android app both connect directly to the WebSocket port. The bridge relays messages between them and inspects text for vision-trigger phrases (`"what am I looking at"`, `"take a photo"`, `"scan this"`, etc.) to request an on-demand capture.
- **MentraOS native (`MENTRA_API_KEY` set).** Uses `@mentra/sdk` `AppServer` to run as a MentraOS mini-app. Adds native wake-word detection (`"Hey Vesper …"`), a 15-second armed follow-up window, a heads-up TTS display via `session.layouts.showTextWall` / `showReferenceCard`, and TTS echo suppression so the mic doesn't re-transcribe the glasses' own speaker.

## Configuration

| Env var | Default | Purpose |
|---------|---------|---------|
| `PORT` | `8089` | WebSocket port (both glasses and V3SP3R connect here) |
| `HTTP_PORT` | `8088` | HTTP `/health` endpoint |
| `MENTRA_API_KEY` | — | Enables MentraOS native mode |
| `MENTRA_PACKAGE_NAME` | `com.vesper.glasses` | MentraOS package identifier |
| `MENTRA_PORT` | `3000` | Port the MentraOS `AppServer` listens on |

## Setup

```bash
cd mentra-bridge
npm install
npm run build
npm start
```

For local development against real glasses, expose the port with `ngrok http 8089` and configure the URL in Vesper Settings → **Smart Glasses**.

Deployment targets that have been tested: Railway, Fly.io, Render, and local + ngrok for dev.

## Wire Protocol

All frames are JSON matching this shape (see `src/index.ts`, `GlassesMessage`):

```ts
{
  type: "VOICE_TRANSCRIPTION" | "VOICE_COMMAND" | "CAMERA_PHOTO"
      | "AI_RESPONSE" | "STATUS_UPDATE" | "CONFIG" | "CAPTURE_REQUEST";
  text?: string;           // transcription, command, or spoken reply
  imageBase64?: string;    // CAMERA_PHOTO only
  imageMimeType?: string;  // CAMERA_PHOTO only, defaults to image/jpeg
  displayText?: string;    // optional HUD text (AI_RESPONSE)
  isFinal?: boolean;       // transcription finality
  metadata?: Record<string, string>;  // source, sessionId, wake_word, etc.
}
```

### Direction and semantics

| Type | Direction | Meaning |
|------|-----------|---------|
| `VOICE_TRANSCRIPTION` | glasses → V3SP3R | Passive transcription (only forwarded when auto-send is on in the app) |
| `VOICE_COMMAND` | glasses → V3SP3R | Explicit command — wake-word hit or "Hey Vesper, …" one-shot |
| `CAMERA_PHOTO` | glasses → V3SP3R | Photo capture (base64), typically in response to a `CAPTURE_REQUEST` or a vision trigger |
| `CAPTURE_REQUEST` | either direction | V3SP3R asks glasses to take a photo (agent-issued `REQUEST_PHOTO`), or the bridge asks the glasses when it detects a vision trigger phrase |
| `AI_RESPONSE` | V3SP3R → glasses | Spoken reply (`text`) and optional HUD reference card (`displayText`) |
| `STATUS_UPDATE` | either direction | Short status text; the bridge also uses this on connect to report `glasses_connected` / `vesper_connected` counts |
| `CONFIG` | V3SP3R → bridge | Toggle bridge-side flags: `sailor_mouth`, `muted` |

### `REQUEST_PHOTO` is intercepted at the agent layer

When the model emits an `execute_command({ action: "request_photo", ... })` tool call, `VesperAgent` intercepts it *before* it reaches `CommandExecutor`. The interception fans out to this bridge as a `CAPTURE_REQUEST`, which the bridge forwards to the glasses (native mode: via `session.camera.requestPhoto`; relay mode: broadcast to any connected glasses client). This is why `FlipperToolExecutor.kt` carries the comment *"REQUEST_PHOTO is intercepted by VesperAgent before reaching here"* — that intercept is invisible to the executor and worth surfacing here so the round-trip is documented in one place.

## Connection lifecycle

- **Client identification.** The Android app connects with the header `x-vesper-client: v3sp3r-android`. Anything else is initially typed `unknown` and reclassified on the first message it sends (glasses if it sends `VOICE_*` / `CAMERA_PHOTO`, vesper if it sends `AI_RESPONSE` / `STATUS_UPDATE` / `CAPTURE_REQUEST`).
- **Heartbeats.** The bridge pings every 25 s (safely under Cloudflare's 100 s idle timeout) and terminates unresponsive clients on the next tick.
- **MentraOS session handshake.** The SDK fires `onSession` before its glasses WebSocket is actually ready. `waitForSessionReady` polls `layouts.showTextWall` for up to 5 s before proceeding, so early sends don't throw "WebSocket connection not established" and kill the session.
- **Reconnect behaviour.** No explicit reconnect logic in the bridge — WebSocket clients simply reconnect. The Android app's `GlassesBridgeClient` owns retry/backoff on its side. There is no queued-message buffer: anything sent while a peer is disconnected is dropped, not replayed.

## Health check

```
GET http://<host>:$HTTP_PORT/health
→ { status, glasses, vesper, mentra, uptime }
```

Returns 200 with connection counts and whether MentraOS mode is active. Useful for uptime probes and for debugging "did the app actually connect?" questions without touching the WebSocket.
