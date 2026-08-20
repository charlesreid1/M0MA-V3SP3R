# Flipper `.fap` apps

> The Flipper Application Package format — how third-party apps run on
> FURI, the `.fap` file layout, build workflow with uFBT, install
> paths, sandboxing.

## What it is

A **`.fap` file** is a compiled, dynamically-loadable Flipper Zero
application. Extension `.fap` = "**F**lipper **A**pplication
**P**ackage." Format is an ELF-like relocatable object built with the
firmware's build system (`fbt` / `uFBT`) targeting the STM32WB55's
Cortex-M4 core.

`.fap` apps run inside FURI (Flipper's real-time OS, based on
FreeRTOS). They can:

- Draw the GUI (menus, canvas, custom rendering).
- Read and write the SD (`/int` and `/ext`).
- Talk to SubGHz, NFC, IR, RFID, iButton subsystems via firmware APIs.
- Drive the GPIO expansion header.
- Use FURI RTOS primitives (threads, mutexes, timers, message queues).

The catalog of community `.fap` apps lives at **FapHub**
(<https://lab.flipper.net/apps>) and its mirrors; official Flipper
apps are also published there. Momentum ships an on-device FapHub
browser that installs directly to `/ext/apps/`.

## How it works

### File format

`.fap` is a relocatable ELF-like blob with:

- **Text section:** M4 machine code.
- **Data section:** initialized data.
- **BSS section:** zero-init RAM.
- **Symbol table:** references to firmware-exported APIs.
- **Header:** app name, category, version, min-firmware-version.

The firmware's loader (see `loader` CLI verb in `flipper-cli.md`)
mmaps the `.fap`, resolves symbols against the firmware's ABI table,
and jumps to `app_main()`.

### FURI (Flipper's RTOS)

FreeRTOS-derived, plus:

- **`furi_thread`** — thread creation, join, priority.
- **`furi_mutex`** / **`furi_semaphore`** — synchronization.
- **`furi_message_queue`** — inter-thread messaging.
- **`furi_hal_*`** — hardware abstraction (GPIO, radios, timers).
- **`furi_record`** — global service registry (used to reach GUI,
  Storage, Notification, etc.).
- **`furi_timer`** — one-shot and periodic timers.
- **`furi_log`** — logging.

An app typically:

1. Registers with services via `furi_record_open("gui")` etc.
2. Runs an event loop pulling from a queue.
3. Cleans up on exit (`furi_record_close`, free memory).

### Building a `.fap` with uFBT

**uFBT** (micro Flipper Build Tool) is the standalone build toolchain
that pip-installs and doesn't require the full firmware repo. Workflow:

```
# example — not runnable here
pip install ufbt
ufbt bootstrap
mkdir myapp && cd myapp
ufbt vscode  # scaffolds VS Code integration
# ... write application.fam + src/myapp.c ...
ufbt        # builds .fap
ufbt launch # installs and runs on connected Flipper
```

- **`application.fam`** — Python-syntax manifest declaring name,
  entrypoint, category, resource dependencies.
- **`src/*.c`** — the C source.
- **Build output:** `.fap` in `dist/<target>/`.

`fbt` (full FBT) is the same tool but embedded in the firmware repo;
use it when developing patches to firmware itself or apps in-tree. See
`flipper-firmware-build.md`.

### Install paths

- **User-installed:** `/ext/apps/<category>/<name>.fap` on the SD.
- **Categories:** `Sub-GHz`, `NFC`, `Infrared`, `iButton`, `GPIO`,
  `Games`, `Tools`, `Bluetooth`, `USB`, `Media`, `Hardware`.
- **Assets:** if the app ships extra data, it goes to
  `/ext/apps_assets/<name>/`.
- **User writable data:** `/ext/apps_data/<name>/` (see
  `flipper-storage.md`).

### FURI vs the firmware ABI

Every `.fap` links against a *specific* firmware API version. Firmware
updates bump the ABI; recompilation is often required. FapHub versions
apps per firmware family + version.

- Momentum's ABI ≠ Official's ABI. A `.fap` compiled for one may fail
  on the other with an `Invalid file` error or crash at runtime.
- Firmware update warnings ("app requires firmware X") are the ABI
  check; heed them.

### Sandboxing (or lack thereof)

**There is no MMU-enforced sandbox.** `.fap` apps run with full
privilege inside FURI:

- Any app can read/write any file on `/ext` and `/int`.
- Any app can drive any radio.
- Any app can crash the firmware (rarely — apps are usually
  well-contained by convention).

Practical mitigations:

- FapHub review + signing (community trust, not cryptographic).
- The `.fap` category shown to the user before installation.
- Momentum's optional "warn on radio access" prompt for new apps.

Trust matters: install from FapHub / Momentum's browser, not random
Discord attachments.

## Capabilities and limits

- **Size:** apps typically 20–200 KB. Larger is possible but
  loader-slow.
- **RAM budget:** FURI leaves a few dozen KB for apps by default.
  Big apps must be memory-careful.
- **API surface:** most of FURI + high-level `flipper-*` APIs are
  exposed. Some low-level HAL bits are firmware-private.
- **Language:** C (canonical), C++ (community, requires extra `fam`
  config). Rust support exists in experimental branches.
- **Startup latency:** loading a `.fap` from SD takes ~200–500 ms.

## Common tasks

- **Install a `.fap`:** copy to `/ext/apps/<category>/<name>.fap` via
  qFlipper or SD reader. Restart the Loader menu; app appears.
- **Install via Momentum FapHub browser:** menu → Apps → browse
  category → Install.
- **Uninstall:** delete the `.fap` from `/ext/apps/`.
- **Launch from CLI:** `loader open <appname>` (see
  `flipper-cli.md`).
- **Build your own:** `ufbt bootstrap`, `ufbt`, `ufbt launch`.
- **Debug crashes:** logs at `log` level; hard-fault info via SWD
  (see `flipper-gpio-pinout.md`).

## Gotchas

- **Firmware ABI mismatch is the #1 install failure.** "Invalid file"
  usually means "compiled against a different firmware version."
- **Compiling for Official when running Momentum** (or vice versa)
  produces a `.fap` that installs but crashes. Match target firmware.
- **`.fap` apps that reserve resources** (e.g. open subghz for TX and
  don't close it) can leave the firmware in a bad state. Reboot after
  a crash.
- **Assets not copied** — if you drop `.fap` into `/ext/apps/` but the
  app expects `/ext/apps_assets/<name>/foo.png`, missing assets
  produce cryptic errors. Ship both.
- **Momentum's FapHub catalog** is larger than Official's; apps built
  for Momentum-specific APIs won't appear when browsing on Official.
- **Legacy JavaScript apps** (Momentum) are separate from `.fap` — see
  `flipper-js-runner.md`. Don't put `.js` files in `/ext/apps/`.

## Legal & safety notes

`.fap` apps can do things the built-in firmware can't. An app that
TXes SubGHz without region checks, emulates NFC without user prompt,
or brute-forces access credentials is legal to *possess* but the
usage is regulated the same as the built-in firmware's equivalent.
See `legal-and-safety.md`.

## See also

- `flipper-firmware-build.md` — building firmware; also builds core
  apps.
- `flipper-js-runner.md` — the alternative to `.fap` for scripting.
- `flipper-storage.md` — `/ext/apps/`, `/ext/apps_assets/`,
  `/ext/apps_data/` layout.
- `flipper-cli.md` — `loader open`, `loader list`.
- `firmware-families.md` — ABI compatibility per family.

---
*Attribution:* Flipper-Devices application build docs
(<https://docs.flipper.net/development>), uFBT
(<https://github.com/flipperdevices/flipperzero-ufbt>), FapHub
(<https://lab.flipper.net/apps>). Retrieved 2025-Q3.
