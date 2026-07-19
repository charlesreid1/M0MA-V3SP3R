---
name: signal-analysis
description: "RF signal analysis methodology — Sub-GHz capture, protocol identification, timing analysis, rolling vs static code detection, and IR protocol decoding"
---

# RF Signal Analysis Methodology

## Sub-GHz Signal Capture and Analysis

### Frequency Bands
| Band | Common Uses | Flipper Support |
|------|-------------|-----------------|
| 300-348 MHz | Garage doors (US), car remotes | Yes (CC1101) |
| 387-464 MHz | Car key fobs, home automation, weather stations | Yes |
| 779-928 MHz | ISM band, LoRa, Z-Wave, garage doors (EU) | Yes |
| 433.92 MHz | Most common — universal remotes, sensors, doorbells | Yes |
| 315 MHz | North American car fobs, garage doors | Yes |
| 868 MHz | European ISM, smart home, alarm systems | Yes |
| 915 MHz | US ISM, LoRa, smart meters | Yes |

### Capture Workflow
```
subghz_receive(frequency=433920000)
```

1. **Set frequency** — start with 433.92 MHz if unknown, then try 315 MHz, 868 MHz
2. **Trigger the target** — press the remote, trigger the sensor, open the door
3. **Capture the signal** — Flipper records the raw signal via `subghz rx`
4. **Capture again (same button)** — immediately trigger the target a second time and save a second capture
5. **Static-vs-rolling determination** — byte-compare captures 1 and 2 *before* investing in protocol ID (see [Rolling Code vs Static Code Detection](#rolling-code-vs-static-code-detection)):
   - Identical → static code, replay is viable, proceed to protocol ID and attack
   - Different → rolling/encrypted code, replay will fail; classify the family but do not attempt transmit
6. **Analyze the capture** — identify protocol, extract data bits with `subghz_decode_raw`

### Modulation Types

**OOK (On-Off Keying)** — most common for simple remotes
- Signal is either present (1) or absent (0)
- Easy to identify: clean on/off pattern in time domain
- Used by: garage doors, doorbells, weather stations, simple car fobs

**ASK (Amplitude Shift Keying)** — modulated amplitude
- Similar to OOK but with amplitude variation
- Flipper treats OOK and ASK similarly (AM modulation)

**FSK (Frequency Shift Keying)** — frequency changes encode data
- Two frequencies represent 0 and 1
- Used by: car key fobs (some), TPMS sensors, smart meters

**Manchester Encoding** — clock embedded in data
- Each bit has a transition in the middle: low→high = 1, high→low = 0
- Self-clocking — no separate clock signal needed
- Used by: many industrial protocols, some garage doors

## Protocol Identification

### By Timing Analysis

Capture the raw signal and measure:
- **Pulse width** (duration of high signal)
- **Gap width** (duration between pulses)
- **Bit rate** (pulses per second)
- **Preamble** (repeating pattern at start)
- **Sync pulse** (long pulse or gap separating preamble from data)

### Common Protocol Signatures

Values below are the exact constants used by the Flipper Zero SubGHz decoders (`lib/subghz/protocols/*.c`). Compare captured pulse widths against these with the listed `te_delta` tolerance.

**Princeton (PT2262/PT2264)**
- Modulation: OOK, PWM
- `te_short`: 390 μs · `te_long`: 1170 μs · ratio 1:3 · `te_delta`: ±300 μs
- Bit 0: 390 μs high + 1170 μs low · Bit 1: 1170 μs high + 390 μs low
- Bit count: 24 bits (min for detection)
- Guard/sync: 30 × `te_short` (≈11,700 μs) between packet repeats; range 15–72 × te_short
- Address: 8 tri-state bits, Data: 4 tri-state bits (chip-level view, before serialization)
- Frequency: 315 MHz or 433.92 MHz
- Used in: cheap remotes, doorbells, power outlets, cheap garage/gate keyfobs
- Match rule: `short ∈ [90, 690]` AND `long ∈ [870, 1470]` AND `long/short ∈ [2.5, 3.5]` AND ≥24 bits

**CAME (12-bit / 24-bit) and Prastel (25-bit)**
- Modulation: AM, PWM
- `te_short`: 320 μs · `te_long`: 640 μs · ratio 1:2 · `te_delta`: ±150 μs
- Bit 0: 640 μs low + 320 μs high · Bit 1: 320 μs low + 640 μs high
- Header/pilot (silence before packet):
  - CAME 12-bit: 47 × te_short ≈ 15,040 μs
  - CAME 24-bit: 76 × te_short ≈ 24,320 μs
  - Prastel 25-bit: 36 × te_short ≈ 11,520 μs
- Packet repeats 4× per press
- Frequency: 433.92 MHz (EU), 868 MHz on some models
- Used in: CAME gate openers, barriers, Prastel remotes
- Match rule: `short ∈ [170, 470]` AND `long ∈ [490, 790]` AND `long/short ∈ [1.7, 2.3]` AND bit count in {12, 24, 25}

**NICE FLO (fixed code)**
- Modulation: OOK/AM, PWM
- `te_short`: 700 μs · `te_long`: 1400 μs · ratio 1:2 · `te_delta`: ±200 μs
- Bit 0: 700 μs low + 1400 μs high · Bit 1: 1400 μs low + 700 μs high
- Header/pilot: 36 × te_short = 25,200 μs of silence, then start bit
- Bit count: 12 bits
- Frequency: 433.92 MHz, 315 MHz variants
- Used in: NICE gate openers (Italy, EU) — FLO1/FLO2/FLO4
- Match rule: `short ∈ [500, 900]` AND `long ∈ [1200, 1600]` AND `long/short ∈ [1.7, 2.3]` AND 12 bits AND header ≥ 22,000 μs
- Note: NICE FLOR-S (rolling code) shares brand but uses 52-bit encrypted transmission — do NOT confuse

**Linear (Multi-Code / Delta-3)**
- Modulation: AM, PWM (bit stream is inverted in the Flipper decoder — decoded values are `~raw`)
- `te_short`: 500 μs · `te_long`: 1500 μs · ratio 1:3 · `te_delta`: ±350 μs
- Bit 0: 500 μs high + 1500 μs low · Bit 1: 1500 μs high + 500 μs low
- Header/pilot: ≈ 42 × te_short ≈ 21,000 μs
- Bit count: 10 bits (DIP-switch encoded)
- Frequency: 300 MHz, 310 MHz, 315 MHz (US)
- Used in: older Linear / Stanley / Multi-Code garage doors, gate systems
- Match rule: `short ∈ [150, 850]` AND `long ∈ [1150, 1850]` AND `long/short ∈ [2.5, 3.5]` AND 10 bits

**Chamberlain / LiftMaster / Craftsman**
- **SECURITY+** (older) — 40-bit rolling code, trinary encoding
- **SECURITY+ 2.0** (current) — encrypted rolling code, longer transmission with mode/frequency hopping between 310/315/390 MHz
- Rolling code: CANNOT be replayed
- Frequency: 300–390 MHz range; 315 MHz and 390 MHz are the most common in US
- Detection: if bit count ≥ 40 and captures 1/2 differ → rolling; classify only, do not replay

**GE / Jasco (fixed code, PT2262-family)**
- Encoding: same PT2262 timing family (short=1u, long=3u), see Princeton match rule
- Bit count: 12 to 24 bits depending on model
- Frequency: 318 MHz or 433.92 MHz
- Used in: home automation switches, some carriage lights

### Identification Decision Tree

Run in this order — cheaper tests first, and the static/rolling gate from the Capture Workflow (step 5) has already been applied.

1. **Measure base timing.** From the raw capture, take the minimum pulse width `t_min` (in μs). Reject captures where `t_min < 150 μs` or `t_min > 2000 μs` — likely noise or a non-supported protocol.
2. **Compute `long/short` ratio** across the middle of the packet (skip first/last pulses).
   - Ratio ∈ [1.7, 2.3] → CAME or NICE FLO family
   - Ratio ∈ [2.5, 3.5] → Princeton / Linear / GE-Jasco family
   - No consistent ratio → Manchester or unsupported; check step 4
3. **Count decoded bits** (pulses / 2 for PWM). Combine with ratio:
   - 10 bits + 1:3 → Linear
   - 12 bits + 1:2, short ≈ 320 μs → CAME 12-bit
   - 12 bits + 1:2, short ≈ 700 μs → NICE FLO
   - 24 bits + 1:2, short ≈ 320 μs → CAME 24-bit
   - 24 bits + 1:3, short ≈ 390 μs → Princeton (PT2262/GE/Jasco)
   - 25 bits + 1:2, short ≈ 320 μs → Prastel
   - ≥40 bits → likely rolling code (Chamberlain Security+, KeeLoq, etc.); do not attempt replay
4. **Check for Manchester.** If step 2 shows *no* stable long/short ratio but pulses cluster at two durations (1t, 2t) with mid-bit transitions, treat as Manchester and inspect for CAME-style header.
5. **Measure header/pilot silence** (long low before data burst) to disambiguate same-bit-count protocols:
   - ≈11,500 μs → CAME 12-bit or Prastel
   - ≈15,000 μs → CAME 12-bit alt
   - ≈21,000 μs → Linear
   - ≈24,000 μs → CAME 24-bit
   - ≈25,000 μs → NICE FLO
6. **Confirm with Flipper's protocol decoder.** `subghz_decode_raw` will name the protocol if the pulse train fits. Disagreement between step 3–5 heuristics and the decoder → trust the decoder; the heuristics assume clean captures.

Do NOT attempt transmit as a classification step. The static/rolling gate in the Capture Workflow already tells you whether replay is meaningful; guessing-by-transmitting is a MEDIUM-risk active action and is not a diagnostic.

## Rolling Code vs Static Code Detection

### Static Codes — CAN Be Replayed
- Same button press always produces identical signal
- Capture once, replay unlimited times
- Common in: cheap remotes, doorbells, power outlets, older garage doors, fan remotes
- **Test**: capture two presses of same button, compare — identical = static

### Rolling Codes — CANNOT Be Replayed
- Each press generates a unique code from a synchronized counter
- Replay of a captured code will fail (counter has advanced)
- **Technologies:**
  - **KeeLoq (HCS301/HCS200)** — 66-bit transmission, 32-bit hopping code, 28-bit fixed serial
  - **AUT64** — used by some car manufacturers
  - **HITAG2** — older, known cryptographic weaknesses
  - **Chamberlain Security+ 2.0** — proprietary rolling code

**Detection method:**
1. Capture signal press 1
2. Capture signal press 2 (same button)
3. Compare the two captures
4. If identical → static code (replayable)
5. If different → rolling code (not replayable with simple replay)

**Rolling code attacks (advanced, often impractical):**
- **RollJam** — jam + capture two codes, replay the first (the second is still valid)
- **RollBack** — exploit counter resynchronization window
- These require specialized hardware and are outside normal Flipper capability

## IR Signal Analysis

### Common IR Protocols

**NEC (most common)**
- Carrier: 38 kHz
- Encoding: pulse distance
- Format: 8-bit address + 8-bit inverse + 8-bit command + 8-bit inverse = 32 bits
- Leader: 9ms burst + 4.5ms space
- Bit 0: 562.5μs burst + 562.5μs space
- Bit 1: 562.5μs burst + 1687.5μs space
- Repeat: 9ms burst + 2.25ms space + 562.5μs burst
- Used by: most consumer electronics, many Chinese-made devices

**RC5 (Philips)**
- Carrier: 36 kHz
- Encoding: Manchester (bi-phase)
- Format: 2 start bits + toggle + 5-bit address + 6-bit command = 14 bits
- Toggle bit flips each key press (distinguishes hold from re-press)
- Bit time: 1.778ms (889μs per half)
- Used by: Philips, Marantz, some European brands

**RC6 (Philips, evolved)**
- Carrier: 36 kHz
- Encoding: Manchester with header
- Leader: 2.666ms burst + 889μs space
- Mode bits + toggle + address + command
- Used by: Microsoft MCE remotes, Philips, Xbox 360 media remote

**Samsung32**
- Carrier: 38 kHz
- Encoding: pulse distance (similar to NEC)
- Format: 8-bit custom code (sent twice) + 8-bit data + 8-bit inverted data = 32 bits
- Leader: 4.5ms burst + 4.5ms space
- Used by: Samsung TVs, soundbars

**SIRC (Sony)**
- Carrier: 40 kHz
- Encoding: pulse width
- Format: 12-bit (7 command + 5 device), 15-bit, or 20-bit variants
- Leader: 2.4ms burst + 600μs space
- Bit 0: 600μs burst + 600μs space
- Bit 1: 1.2ms burst + 600μs space
- Message sent 3 times minimum
- Used by: Sony TVs, PlayStation, audio equipment

### IR Capture and Replay
```
ir_receive()                              # Learn raw signal (LOW)
ir_transmit(path="/ext/infrared/foo.ir")  # Replay captured signal (MEDIUM)
```

### IR Analysis Workflow
1. **Capture** the IR signal in raw mode
2. **Identify carrier frequency** — 36 kHz (Philips), 38 kHz (NEC/Samsung), 40 kHz (Sony)
3. **Measure leader pulse** — this uniquely identifies the protocol family
4. **Decode data bits** — apply protocol-specific bit encoding rules
5. **Extract address and command** — map to device functions
6. **Build command library** — capture all buttons, document address:command pairs

## Decoding Unknown Signals

When the Flipper doesn't auto-detect the protocol. All thresholds below are numeric so the AI can classify without heuristic judgement.

1. **Capture raw** — save the complete signal waveform as a `.sub` file (RAW mode).
2. **Extract the pulse train** — list of `(level, duration_μs)` pairs from the RAW_Data field.
3. **Measure base timing element `t_base`:**
   - Compute the histogram of pulse durations; `t_base` is the mode of the shortest cluster.
   - Reject if `t_base < 100 μs` (likely noise) or `t_base > 2000 μs` (likely a different modulation, e.g. LoRa/FSK — Flipper's raw capture is OOK-oriented).
4. **Cluster pulses into symbols.** Round each duration to the nearest integer multiple of `t_base`:
   - `n ∈ [0.7, 1.3]` → "short" (1 unit)
   - `n ∈ [1.7, 2.3]` → "medium" (2 units)
   - `n ∈ [2.7, 3.3]` → "long" (3 units)
   - Anything > 10 units at low level → header/inter-frame gap; segment the packet here
   - If > 15% of pulses do not fit any cluster → protocol is not pure PWM; consider Manchester (step 6) or FSK (out of scope for OOK raw)
5. **Compute derived features:**
   - `bit_rate = 1e6 / (t_base × pulses_per_bit)` bits per second (pulses_per_bit = 2 for PWM)
   - `packet_length = total_pulses / pulses_per_bit`
   - `header_duration_μs` = longest low interval before the first data burst
   - `repeat_count` = number of packet copies before the capture ends (typically 3–8 for fixed codes)
6. **Test for Manchester.** If step 4 shows only two duration classes (1t and 2t) AND every 2t pulse can be split into two 1t half-bits with a mandatory mid-bit transition → Manchester. Bit 0 = low→high, Bit 1 = high→low (IEEE 802.3 convention; some protocols invert).
7. **Look for structure across multiple captures:**
   - Same button, 2 captures → any bit differing → rolling portion. All bits identical → fully static.
   - Different buttons, same remote → bits that flip identify the command field.
   - Same button, different remotes → bits that flip identify the address/serial field.
8. **Locate the checksum.** For fixed-code protocols, the last 4–16 bits are often a checksum. Common patterns:
   - **Byte-inverse** (NEC-style): second byte is `~first byte`. XOR of adjacent bytes = `0xFF`.
   - **XOR checksum**: XOR of all preceding bytes equals the checksum byte.
   - **CRC-8** with polynomial `0x07` or `0x31` — try both against the payload; matching CRC identifies the algorithm.
   - If the trailing bits satisfy none of these, treat the payload as opaque and record it verbatim.

### Common Signal Structures
```
[Preamble] [Sync] [Address/ID] [Command/Data] [Checksum] [Stop]
```

- **Preamble**: 4–16 bits of alternating 1010… pattern for receiver AGC calibration. Duration typically 2–8 × `t_base` per bit.
- **Sync**: distinctive pulse/gap pattern separating preamble from data. In OOK-PWM, this is usually a long low ≥ 10 × `t_base` (Princeton ≈ 30 × t_base, NICE ≈ 36 × t_base, CAME 12-bit ≈ 47 × t_base, Linear ≈ 42 × t_base).
- **Address**: device identifier (fixed per remote); size 8–28 bits.
- **Command**: button/action identifier (varies per button); size 4–8 bits.
- **Checksum**: XOR, byte-inverse, or CRC-8 of data bits for error detection; size 4–16 bits.

## Operational Notes

- Always capture multiple presses of the same signal for comparison
- Note the distance and angle for successful capture — relevant for report
- Sub-GHz transmission power varies by region (FCC vs CE vs ARIB limits)
- Flipper's CC1101 radio sensitivity: approximately -110 dBm
- Maximum practical Sub-GHz range: 50-100m depending on environment
- IR is line-of-sight only; range typically 5-10m
- Document all captured signals with timestamps, descriptions, and replay success/failure

## Vesper action mapping

| Step | Action | Risk |
|------|--------|------|
| Sub-GHz passive capture | `subghz_receive(frequency=…)` | LOW |
| Decode captured signal file | `subghz_decode_raw(path="/ext/subghz/foo.sub")` | LOW |
| Sub-GHz transmit | `subghz_transmit(path="/ext/subghz/foo.sub")` | MEDIUM |
| IR passive capture | `ir_receive` | LOW |
| IR transmit | `ir_transmit(path=…)` or `ir_transmit_raw(frequency=…, duty_cycle=…, content=…)` | MEDIUM |

For deeper packet-level reverse engineering after capture, load `skill("protocol-analysis")` — it covers CRC detection and byte-level field mapping.
