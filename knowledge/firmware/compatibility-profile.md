# FirmwareCompatibilityProfile — how Vesper picks a profile

> The bridge between "how the firmware works" and "how the app handles
> it." What `FirmwareCompatibilityProfile.kt` does on connect, how to
> add a new fork.

## What it is

`FirmwareCompatibilityProfile` is a Kotlin type in
`app/src/main/java/com/vesper/flipper/ble/FirmwareCompatibilityProfile.kt`.
On every BLE connect, Vesper's transport reads the Flipper's
`device_info` output, parses key/value pairs, and matches them against
a fixed table of known firmware fingerprints. The match becomes a
`FirmwareCompatibilityProfile` value, and every subsequent
`execute_command` invocation is routed based on that profile.

Without this layer, an assistant asking to run `js /ext/foo.js` would
succeed silently on Momentum, fail cryptically on Official, and behave
who-knows-how on Unleashed. The profile is what makes those failures
loud and useful.

## How it works

### The two enums

`FirmwareFamily`:

```
UNKNOWN | OFFICIAL | MOMENTUM | UNLEASHED | ROGUEMASTER | XTREME
```

`XTREME` exists because early Momentum builds still identified as
Xtreme in `device_info`. Fresh installs of Momentum-2024+ report as
`MOMENTUM`.

`FirmwareTransportMode`:

```
UNAVAILABLE | PROBING | CLI_ONLY | RPC_ONLY | CLI_AND_RPC
```

- `UNAVAILABLE` — the family can't be safely commanded (unknown
  fingerprint + defensive default).
- `PROBING` — Vesper is still fingerprinting; passthrough allowed
  optimistically.
- `CLI_ONLY` — the CLI over BLE/USB is the only sanctioned route.
- `RPC_ONLY` — RPC (Protobuf app bridge) is the only sanctioned route.
- `CLI_AND_RPC` — both are wired; router prefers RPC for TX-heavy verbs
  (see `prefersRpcBridge()`).

`FirmwareCommandRoute`:

```
DIRECT_CLI | RPC_APP_BRIDGE | UNSUPPORTED
```

### The data class

```
data class FirmwareCompatibilityProfile(
    val family: FirmwareFamily,
    val label: String,
    val transportMode: FirmwareTransportMode,
    val supportsCli: Boolean,
    val supportsRpc: Boolean,
    val supportsRpcAppBridge: Boolean,
    val confidence: Confidence,
    val notes: String,
)
```

`label` is the human-readable name shown in the app ("Momentum
(release 2025-08)"). `confidence` reflects fingerprint quality:
`HIGH` when `momentum_firmware_commit` or `unleashed_version` is
present, `MEDIUM` when only structural cues match, `LOW` when it's a
best guess.

### The routing decision

Every `execute_command` call passes through `assessCliCommand(profile,
command, hasRpcMapping)` in `FirmwareCompatibilityLayer`:

1. If `transportMode == PROBING` → pass through, prefer RPC bridge if
   `hasRpcMapping` is true.
2. If neither `supportsCli` nor `supportsRpc` → `UNSUPPORTED`.
3. If `transportMode == CLI_AND_RPC` and the verb matches
   `prefersRpcBridge(command)` → `RPC_APP_BRIDGE` (falls back to
   `DIRECT_CLI` if `hasRpcMapping` is false).
4. If `transportMode == CLI_ONLY` → `DIRECT_CLI`.
5. If `transportMode == RPC_ONLY` → `RPC_APP_BRIDGE` if `hasRpcMapping`
   else `UNSUPPORTED`.
6. If `transportMode == UNAVAILABLE` → `UNSUPPORTED`.

The result is a `FirmwareCommandCompatibility(supported, route,
message)` — `message` carries the user-visible reason when
`supported = false` (e.g. "Momentum required for JS runner").

### `prefersRpcBridge()` — the RPC-preferred verbs

A private helper. Any command matching one of these prefixes is
preferentially routed through the RPC app bridge on `CLI_AND_RPC`
firmwares (currently Momentum, Unleashed, RogueMaster):

- `badusb `
- `subghz tx `
- `subghz tx_from_file `
- `ir tx `
- `infrared tx `
- BLE-spam variants (`bt spam `, etc. — see the source for the exact
  list)
- `nfc emulate `
- `nfc emu `
- `rfid emulate `
- `ibutton emulate `

The intent: TX-side commands are riskier and get the higher-integrity
transport when available.

### Fingerprinting rules

The parser looks at `device_info` output for:

- `momentum_firmware_commit` present → `MOMENTUM`, confidence HIGH.
- `xtreme_firmware_commit` present → `XTREME` (treated as MOMENTUM for
  routing).
- `unleashed_version` present → `UNLEASHED`, confidence HIGH.
- `rogue_master_*` present → `ROGUEMASTER`, confidence HIGH.
- Only stock keys present → `OFFICIAL`, confidence HIGH.
- Nothing parseable → `UNKNOWN`, transport `UNAVAILABLE`.

Multiple matches (should never happen in practice) resolve in the
order above.

## Capabilities and limits

- **Fingerprinting is one-shot on connect.** If someone hot-flashes a
  new firmware over a live BLE connection (they can't, in practice —
  DFU requires USB), the profile stays stale until reconnect.
- **`UNAVAILABLE` fails fast.** Commands don't get dispatched at all;
  the app surfaces the mismatch. This is deliberate — silent
  no-ops are worse than errors.
- **`RPC_APP_BRIDGE` requires a mapping** in the RPC handler layer.
  Adding a new firmware family doesn't automatically wire RPC — that
  needs a follow-up on the transport side.
- **No dynamic capability probing** beyond `device_info`. Vesper does
  not test-execute a verb to see if it exists; it trusts the profile
  table.

## Common tasks

### Adding a new firmware family

1. Add the enum value to `FirmwareFamily`.
2. Add a fingerprint pattern to the parser — a `device_info` key that
   only that family emits.
3. Add a `FirmwareCompatibilityProfile` row in the profile table with
   an accurate `transportMode` and `supports*` flags.
4. If the family gets its own RPC handling, wire the mapping on the
   transport side (out of scope for the profile itself).
5. Add a unit test covering the fingerprint → profile mapping.

### Verifying what profile got picked

Connect the Flipper in the Vesper app; the connect toast reports the
matched family + confidence. Or in the Ops Center audit log, every
`execute_command` entry records the profile that routed it.

### Forcing a profile (for testing)

Set the debug-only override in `settings.local.json` (development builds
only). Not part of the production API.

## Gotchas

- **`UNKNOWN` is not the same as OFFICIAL.** If fingerprinting fails,
  Vesper defaults to `UNAVAILABLE`, not "assume Official". This is
  intentional — we'd rather refuse than corrupt a fork.
- **`XTREME` maps to Momentum's route policy.** They share the same
  extended verb set.
- **RPC preference is opinionated.** `subghz tx` prefers RPC because
  the app-bridged path enforces a per-frequency risk check
  (`RiskAssessor`) that CLI can bypass. Do not "optimize" this to CLI
  just because the RPC layer is slower.
- **New Momentum builds add `momentum_release_channel`.** Do not key
  fingerprint on the release channel — key on the commit hash's
  presence.

## Legal & safety notes

The profile itself is not a regulated artefact. The verbs it routes
often are; see `legal-and-safety.md`.

## See also

- `firmware-families.md` — the ecosystem overview.
- `firmware-momentum.md` — Momentum's fingerprint keys in detail.
- `flipper-cli.md` — the verbs `prefersRpcBridge` gates.
- `architecture.md` — where `FirmwareCompatibilityProfile` sits in the
  transport pipeline.

---
*Attribution:* `FirmwareCompatibilityProfile.kt` (this repo). Retrieved
from HEAD of `feature/expand-knowledge` branch, 2025-Q3.
