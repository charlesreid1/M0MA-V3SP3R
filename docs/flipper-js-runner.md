# Flipper JavaScript runner

> The Momentum JavaScript execution environment: what it is, what APIs
> it exposes, how it differs from writing a `.fap` in C.

## What it is

The **Flipper JS runner** is a JavaScript interpreter that ships as an
in-tree app on **Momentum firmware** (also present in Xtreme-lineage
forks). It lets you write scripted Flipper behavior in ordinary
JavaScript, drop the `.js` file on the SD, and run it — no compilation,
no `fbt`/uFBT, no C.

The engine underneath is **mJS** or Elk-like (small embeddable JS) —
verify the current implementation in Momentum's
`applications/external/js_app/`. It's a subset of ECMAScript with
Flipper-specific host APIs bolted on.

**Not present on Official firmware.** Attempting to run a `.js`
script on stock Flipper-Devices firmware fails silently. See
`firmware-families.md`.

## How it works

### File layout

- Scripts live under `/ext/apps_data/js_app/` or `/ext/js/`
  (varies by Momentum version — check the app's file browser).
- Extension `.js`.
- Plain text, LF-terminated.
- No transpilation, no bundling — the runtime interprets directly.

### Launching

Three routes:

- **GUI:** JS Runner app → browse to your `.js` → Run.
- **CLI:** `js /ext/apps_data/js_app/myscript.js` (available on
  Momentum builds with the `js` CLI verb — some releases lack it, in
  which case use GUI).
- **From another app:** rare; the runtime exposes internal APIs for
  chaining.

### Exposed host APIs

Momentum's `js` runtime exposes a curated set of module-shaped
bindings. Common ones (verify against the current Momentum source
before making strong claims):

| Module          | What it does                                            | Example                              |
|-----------------|---------------------------------------------------------|--------------------------------------|
| `storage`       | Read/write files on `/ext` and `/int`.                 | `storage.readFile("/ext/foo")`       |
| `subghz`        | TX/RX SubGHz.                                          | `subghz.transmitFile("/ext/subghz/foo.sub")` |
| `notification`  | LED, vibro, sound cues.                                | `notification.blink("blue")`         |
| `gpio`          | Pin read/write on the GPIO header.                     | `gpio.setPin(7, 1)`                  |
| `math`          | Math functions (Math.sin, Math.floor, etc.).           | `Math.floor(x)`                      |
| `keyboard`      | On-screen keyboard for user input.                     | `keyboard.text("Enter a name:")`     |
| `dialog`        | Modal dialogs, message boxes.                          | `dialog.show("Message", "OK")`       |
| `submenu`       | Multi-choice menus.                                    | `submenu.show(["A","B","C"])`        |
| `gui`           | Direct canvas drawing.                                 | Varies; low-level.                   |
| `event_loop`    | Event-driven scheduling (some builds).                 | Advanced.                            |

The API names and shapes above are illustrative; consult the current
Momentum JS API reference at the time of writing.

### Runtime characteristics

- **Not standard JS.** Regex support, generators, and `async/await`
  are typically absent. Basic ES5-era syntax works.
- **No `require` in the CommonJS sense.** Modules are pre-bound; you
  reach them by their global names.
- **Numeric precision:** integer + float, JavaScript-standard IEEE 754.
- **String handling:** UTF-8 in Momentum builds (verify per release).
- **Blocking calls are OK** — the runtime is single-threaded per-script.
- **No network stack.** JS runner can't talk to Marauder or any other
  UART peer directly; it can `gpio.uart_write()` on some builds, but
  it's not a full protocol.

## How it differs from a `.fap`

| Axis                     | `.fap` (C)                                    | `.js` (JS runner)                              |
|--------------------------|-----------------------------------------------|------------------------------------------------|
| Language                 | C (mostly)                                    | JavaScript (subset)                            |
| Build                    | `uFBT` or `fbt`; compile step required        | None; edit + run                               |
| Startup time             | 200–500 ms (loader mmaps ELF)                 | Faster (~50–100 ms interpret)                  |
| API surface              | Full FURI, all HAL                            | Curated, higher-level, safer                   |
| Performance              | Native M4 code; fast                          | Interpreted; slow, but plenty for scripting    |
| Portability              | Firmware-family-specific ABI                  | Momentum-only; identical across Momentum builds |
| Debugging                | fap crashes hard; SWD needed                  | JS exceptions surface as dialogs               |
| Ecosystem                | FapHub (thousands of apps)                    | Smaller; user-shared scripts                   |
| Sandboxing               | None (see `flipper-fap-apps.md`)              | Runtime-enforced (bounded API)                 |

Use `.fap` when you need low-latency loops (bit-banging, real-time
protocol reversal), full API access, or want to publish to FapHub.
Use `.js` for scripting, one-off automation, and non-C developers.

## Capabilities and limits

- **File I/O:** read, write, append, delete on `/ext` and `/int`.
- **SubGHz:** transmit files and (on some builds) transmit raw
  payloads.
- **IR / NFC / RFID / iButton:** varies by release; SubGHz is best
  supported.
- **GPIO:** read/write digital pins; PWM on some builds; no SPI/I2C
  bit-banging without `.fap`.
- **Timing:** ~ms-scale precision; not RTOS-hard-real-time.
- **Memory:** modest per-script budget (a few KB heap); large data
  should be file-backed.
- **No filesystem "cd"** — always absolute paths.
- **Exceptions:** thrown errors surface as dialogs with the script
  aborted.

## Common tasks

- **Blink the LED:**
  ```javascript
  // example — not runnable here
  for (let i = 0; i < 5; i++) {
      notification.blink("blue");
      delay(200);
  }
  ```
- **Transmit a saved SubGHz capture:**
  ```javascript
  // example — not runnable here
  subghz.transmitFile("/ext/subghz/mycap.sub");
  ```
- **Read a file and print:**
  ```javascript
  // example — not runnable here
  let contents = storage.readFile("/ext/subghz/mycap.sub");
  dialog.show(contents.slice(0, 100), "OK");
  ```
- **Automate a menu-driven test rig:** JS is well-suited to "prompt
  user for a target, iterate through a list of test cases, log
  results" flows.

## Gotchas

- **The API is Momentum-only.** Sharing a JS script with an Official
  user won't work — they need Momentum first.
- **Momentum's JS API evolves.** Scripts written against a 2024 build
  may not run on a 2025 build. Pin your dependencies (in the file
  header) or test after firmware updates.
- **No `console.log` per se.** `print` or `dialog.show` are the
  visible outputs; some builds expose a scrolling log view via
  `log.info`.
- **Long-running scripts** block the UI. Add explicit yields or short
  delays.
- **Regex is often absent.** Rewrite pattern matching with string
  operations.
- **Async syntax rejected.** No `async function`, no `await`.
- **Memory leaks manifest as OOM-crash-the-script** with no
  destructor mechanism. Nil out large references when done.

## Legal & safety notes

The JS runner can command SubGHz TX, NFC emulate, and GPIO writes —
same as `.fap` apps and CLI verbs. The scripting layer offers no legal
distinction; the underlying capability is what matters. See
`legal-and-safety.md`.

## See also

- `firmware-momentum.md` — Momentum specifically ships this runtime.
- `firmware-families.md` — which forks include it.
- `flipper-fap-apps.md` — the C-based alternative.
- `flipper-storage.md` — where `.js` files live.
- `flipper-cli.md` — `js` CLI verb (Momentum only).
- `flipper-firmware-build.md` — where `js_app` lives in the tree.

---
*Attribution:* Momentum `applications/external/js_app/` sources,
Momentum wiki JS API reference. Retrieved 2025-Q3.
