# Firmware updating — flashing, DFU, brick recovery

> How firmware updates actually happen on a Flipper Zero: `.tgz`
> bundles, `/ext/update/` staging, the Updater, DFU recovery,
> family-switching tradeoffs.

## What it is

A Flipper Zero firmware update is a **`.tgz` bundle** (Official /
Momentum / Unleashed / RogueMaster all use the same format) that
contains:

- `firmware.dfu` — the ST32-format image for the Cortex-M4 core.
- `radio.bin` (sometimes) — the STM32WB55 wireless coprocessor binary.
- `resources/` — asset staging that the Updater copies to `/ext/`.
- `update.fuf` (or similar) — the update manifest.

The **on-device Updater** app consumes the bundle: verifies checksums,
flashes the M4 core, optionally reflashes the M0+ radio stack, copies
resources, reboots. If anything goes wrong before the flash actually
completes, the Flipper reboots into the old firmware and the update
just failed. If it goes wrong *during* the flash, DFU recovery over
USB is the escape hatch.

## How it works

### Three routes to flash

**1. qFlipper (recommended for most users):**

1. Connect USB, open qFlipper.
2. Update tab → select the `.tgz` you want (or use the "current release"
   button for Official).
3. qFlipper stages the bundle over USB storage into `/ext/update/`
   and reboots the Flipper into the Updater. Fully automated.

**2. Web installer (Momentum / Unleashed / RogueMaster):**

1. Each fork hosts a WebUSB installer. E.g. <https://momentum-fw.dev/>.
2. Chromium-family browser required (Firefox lacks WebUSB).
3. Same underlying flow — the web page drives USB, stages the `.tgz`,
   triggers the Updater.

**3. Manual `/ext/update/` drop:**

1. Copy the `.tgz` to `/ext/update/` via any file transfer method
   (qFlipper's storage tab, or SD card in a laptop).
2. From the Flipper GUI: Settings → Storage → Install from FUF
   (or the family-specific menu path).
3. On-device Updater takes over.

### DFU recovery (STM32 bootloader)

If the M4 firmware image is corrupted, the Updater failed mid-flash, or
the Flipper hangs on boot:

1. Power off the Flipper (hold BACK 10 s, or pop the battery via SD
   compartment on old revs).
2. Hold **LEFT + BACK** during power-on. The M4 comes up in the ST
   system bootloader.
3. USB enumerates as an STM32 DFU device.
4. Use `dfu-util` or ST's DfuSe tool to write a fresh `.dfu` image.

Example:
```
# example — not runnable here
dfu-util -a 0 -s 0x08000000 -D firmware.dfu
```

DFU flashes the M4 core only. The radio coprocessor (`radio.bin`) is
written from *inside* the M4 firmware — so if you DFU an image that
expects radio stack v1.15.0 and the Flipper has v1.12.0, first boot
of the DFU'd firmware will reflash the radio.

### Cross-family flashing

- **Same family, forward:** always works via the Updater. Standard
  path.
- **Same family, rollback:** works via the Updater. Some `/ext/`
  state may become orphaned (e.g. old settings JSON schema).
- **Cross-family via Updater:** **fails.** Each fork's Updater
  refuses `.tgz` bundles it doesn't recognize as its own. Official
  particularly strict.
- **Cross-family via DFU:** **works.** DFU bypasses the family check;
  it just flashes bytes. This is the escape hatch when swapping
  Official → Momentum or vice versa.
- **Cross-family via qFlipper:** qFlipper installs whatever `.tgz` you
  point it at. It uses DFU internally when the on-device Updater
  refuses.

### Radio stack updates

The Cortex-M0+ radio coprocessor runs a proprietary ST binary
("STM32WB Wireless Coprocessor"). It's updated less frequently than
the M4 firmware. When it changes, the `.tgz` includes a `radio.bin`
and the Updater flashes it after the M4 finishes.

A radio-stack update is the highest-risk step because a mid-flash
failure leaves the coprocessor in an unknown state. If BLE stops
working after an update, the radio stack is the first suspect — a
subsequent full flash usually recovers it.

## Capabilities and limits

- **Update integrity:** the Updater checksums the bundle. Corrupt
  `.tgz` = "Update failed, please retry" on boot.
- **No differential updates.** Every flash is a full write. On slow
  SD cards this is several seconds.
- **User data preserved.** `/ext/subghz`, `/ext/nfc`, `/ext/apps`,
  etc. survive updates. Only firmware-owned directories
  (`/ext/dolphin/` under some conditions) get overwritten.
- **DFU cannot flash the radio stack directly.** The radio update
  path is M4-only can-write. Use qFlipper for radio stack fixes.
- **Update size ~1-2 MB.** No practical size concern.

## When to switch families

Each fork's tradeoff:

- **Official** — most conservative, best support, mainstream. Choose
  when you need Flipper-Devices support, are on shared/loaner hardware,
  or don't care about region unlocks.
- **Momentum** ⭐ — richest community feature set, active dev, Vesper's
  primary target. Choose when you want the JS runner, extended NFC
  attacks, worldwide SubGHz. Assume weekly release cadence.
- **Unleashed** — region-unlock focused, more conservative than
  Momentum in scope. Choose if you want unlocks without the app bloat.
- **RogueMaster** — kitchen-sink build. Choose if you want everything
  turned on and are willing to tolerate more churn.

## Common tasks

- **Update to latest of current family:** qFlipper Update tab, one
  click.
- **Switch Official → Momentum:** back up `/ext/` (copy SD contents
  to laptop), then use Momentum's web installer *or* DFU-flash the
  Momentum `.dfu` directly.
- **Roll back Momentum → Official:** same as above with Official's
  `.tgz`.
- **Recover a soft-brick:** hold LEFT+BACK on power-on → DFU mode →
  `dfu-util` a known-good `firmware.dfu`.
- **Recover a hard-brick:** SWD via a DAP-Link on GPIO pins 10/11/12;
  `openocd` + full flash write. See `flipper-gpio-pinout.md`.
- **Restage a stuck update:** delete `/ext/update/*.tgz` and retry.

## Gotchas

- **Region-unlock removal happens in firmware.** Flashing Momentum
  *enables* worldwide TX at the firmware layer; nothing in the
  hardware or radio stack does. If someone thinks region unlock is a
  hardware modification — it's not.
- **Web installer requires WebUSB.** Not Safari, not Firefox. Chrome,
  Edge, Brave, Opera work.
- **DFU flashes clobber the radio-stack version pointer.** After DFU,
  first boot may spend an extra 10-20 s reflashing the radio
  coprocessor. This is normal; don't power-cycle mid-boot.
- **`/ext/update/*.tgz` can accumulate** if you interrupt the Updater
  repeatedly. Clean it out to avoid confusion.
- **qFlipper won't downgrade the radio stack** on some releases —
  Flipper-Devices treats radio downgrades as unsafe. If you need one,
  DFU.
- **Battery must be >20%** or the Updater refuses to start. Reasonable
  guardrail; can be bypassed via CLI on some firmwares, don't.

## Legal & safety notes

Choosing a firmware with region unlocks removed enables you to *break*
FCC/ETSI rules; flashing the firmware itself is not illegal. See
`legal-and-safety.md` for the "capability vs. legality" distinction.

## See also

- `firmware-families.md` — which one to pick.
- `firmware-momentum.md` — Momentum-specific install flow details.
- `firmware-compatibility-profile.md` — how Vesper reacts post-update.
- `flipper-storage.md` — `/ext/update/` layout.
- `flipper-hardware.md` — DFU boot procedure.
- `flipper-gpio-pinout.md` — SWD pins for hard-brick recovery.

---
*Attribution:* Flipper-Devices Updater docs
(<https://docs.flipper.net/basics/firmware-update>), Momentum
release notes, `dfu-util` documentation. Retrieved 2025-Q3.
