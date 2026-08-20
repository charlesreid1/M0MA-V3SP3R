# Flipper Zero — firmware build

> Building Flipper firmware from source: fbt, uFBT, target names,
> Momentum tree layout, contribution paths.

## What it is

Flipper Zero firmware is a C project built with the **Flipper Build
Tool** (`fbt`), a SCons-based wrapper over `arm-none-eabi-gcc`. Two
flavors of the tool:

- **`fbt`** — full-tree build. Lives inside the firmware repo. Handles
  firmware + all in-tree apps + assets + `.tgz` bundling.
- **`uFBT`** (micro-fbt) — a lightweight standalone wrapper for
  building **just apps** against a prebuilt SDK. Pip-installable.
  See `flipper-fap-apps.md`.

Every firmware family (Official, Momentum, Unleashed, RogueMaster) uses
the same tool. Their trees diverge in patches on top of the base.

## How it works

### Target names

The Flipper Zero is target **`f7`** (STM32WB55, mass-production
revs). Older engineering-sample revs used `f6` and are out of scope.

Some tooling accepts explicit target flags (`--target f7`); most just
default correctly.

### fbt commands

Common invocations (run from repo root):

- `./fbt` — build firmware + all in-tree apps.
- `./fbt updater_package` — build the `.tgz` update bundle.
- `./fbt flash` — flash via USB DFU (requires DFU-boot Flipper).
- `./fbt flash_usb` — build + flash via qFlipper.
- `./fbt firmware_clean` — clean firmware build products.
- `./fbt fap_clean` — clean app build products only.
- `./fbt copro_dist` — build the coprocessor image (usually not
  needed).
- `./fbt lint` — run linting.
- `./fbt vscode` — set up VS Code integration.
- `./fbt fap_name.fap` — build a specific in-tree app.

### uFBT commands

Standalone, no firmware repo needed. Install: `pip install ufbt`.

- `ufbt bootstrap` — download SDK for a target firmware.
- `ufbt` — build the current directory's app.
- `ufbt launch` — build + install + run on connected Flipper.
- `ufbt vscode` — VS Code integration.
- `ufbt update --branch=dev` — track a specific SDK branch.

To build against a specific firmware family:

```
# example — not runnable here
ufbt bootstrap --index-url=https://sdk.momentum-fw.dev/  # cite before use
```

Different firmware families publish their own SDK indexes.

### Submodule structure

The firmware repo uses git submodules for vendored libraries:

- **`lib/mbedtls/`** — mbedTLS for BLE crypto.
- **`lib/subghz/`** — SubGHz protocol library.
- **`lib/nfc/`** — NFC library.
- **`lib/microtar/`** — TAR (for `.tgz` bundles).
- **`lib/mlib/`** — utility library.
- **`lib/toolchain/`** — pinned toolchain (arm-none-eabi-gcc).
- **`lib/STM32CubeWB/`** — ST's HAL for STM32WB55.
- **`radio_device_*`** — coprocessor binaries.

Clone with `git clone --recursive` or run `git submodule update
--init --recursive` after clone.

## Momentum's tree layout

Momentum inherits the Official structure, then adds:

- **`applications/main/`** — patched versions of upstream apps.
- **`applications/external/`** — Momentum-specific apps not in
  upstream. This includes the Marauder companion, SubBrute, extra
  NFC / iButton tools, `js_app` (the JavaScript runner — see
  `flipper-js-runner.md`), and more.
- **`applications/system/`** — patched system apps.
- **`applications/plugins/`** — plugin apps (subghz decoders, NFC
  parsers, etc.).
- **`assets/dolphin/`** — extended animation packs.
- **`assets/backgrounds/`** — boot logos.
- **`assets/rgb/`** — RGB LED profiles.
- **`furi/`** — minimal patches to FreeRTOS core (Momentum tries to
  stay close to upstream).

For a Momentum-specific feature, `grep` under `applications/external/`
first — that's where most divergence lives.

## Common tasks

- **Build Official firmware from scratch:**
  ```
  # example — not runnable here
  git clone --recursive https://github.com/flipperdevices/flipperzero-firmware
  cd flipperzero-firmware
  ./fbt
  ```
  Produces `.dfu` and `.tgz` in `dist/f7-*/`.
- **Build Momentum from scratch:**
  ```
  # example — not runnable here
  git clone --recursive https://github.com/Next-Flip/Momentum-Firmware
  cd Momentum-Firmware
  ./fbt
  ```
  Same output shape.
- **Build a single in-tree app:** `./fbt fap_wifi_marauder.fap`.
- **Flash a fresh firmware:** `./fbt flash_usb` (via qFlipper) or
  `./fbt flash` (via DFU).
- **Clean rebuild:** `./fbt clean && ./fbt`.
- **Build a `.fap` app standalone (uFBT):**
  ```
  # example — not runnable here
  pip install ufbt
  ufbt bootstrap
  cd myapp
  ufbt
  ```

## Contributing

### Upstream Flipper-Devices

- Contribution guide at their GitHub — start with an issue,
  discussion-first for larger features.
- PR flow is well-established; core devs review actively.
- Bar for merging is high — expect several review rounds.

### To Momentum

- Community-run; PR reviewed by Momentum team.
- Weekly release train makes small features land quickly.
- Focus areas: SubGHz decoders, NFC parsers, JS runner APIs,
  UX improvements.
- Momentum tries to periodically upstream to Flipper-Devices; not all
  changes make it back.

### Publishing your own fork

Any of the forks (Momentum, Unleashed, RogueMaster) is itself a
public fork. To publish your own:

1. Fork Official (or a fork).
2. Patch what you want.
3. Publish release `.tgz`s tagged with your fork's identifier.
4. Optionally: add your fork to `FirmwareCompatibilityProfile.kt` in
   Vesper (see `firmware-compatibility-profile.md`) — extend the enum,
   add a profile row, ship a matching parser rule.

## Capabilities and limits

- **Toolchain is pinned** — the firmware ships with a specific
  arm-none-eabi-gcc version. Building with a system-installed gcc
  usually works but isn't supported.
- **Cross-platform** — fbt runs on Linux, macOS, and Windows (WSL
  recommended).
- **Build time** — full build ~2–5 minutes on modern hardware.
- **CI feasibility** — Flipper-Devices runs CI in-house; Momentum's
  release pipeline is public on GitHub Actions.

## Gotchas

- **Missing submodule init** produces cryptic "file not found" errors.
  `git submodule update --init --recursive` first.
- **System gcc mixup** — if you have both a system arm-none-eabi-gcc
  and fbt's pinned toolchain, PATH-order matters. Prefer fbt's.
- **Windows without WSL** — long-standing issues with path handling
  in SCons. Use WSL if possible.
- **Wrong SDK branch in uFBT** — building an app for the wrong firmware
  ABI is silent until install. `ufbt bootstrap --version=<expected>`
  first.
- **`copro_dist`** requires ST's Wireless Coprocessor binaries; these
  are vendored via a submodule but not always fetched by default.

## Legal & safety notes

Building firmware is not itself regulated. Publishing a fork that
removes region locks (or including region-unlocked builds in a
`.tgz`) may be legally sensitive in some jurisdictions. See
`legal-and-safety.md`.

## See also

- `firmware-families.md` — the four upstreams you'd fork from.
- `firmware-momentum.md` — Momentum's specific tree.
- `firmware-updating.md` — flashing `.tgz` / `.dfu` results.
- `flipper-fap-apps.md` — building individual apps with uFBT.
- `flipper-cli.md` — testing built firmware over USB.

---
*Attribution:* Flipper-Devices firmware repo README, uFBT
(<https://github.com/flipperdevices/flipperzero-ufbt>),
Momentum firmware repo README. Retrieved 2025-Q3.
