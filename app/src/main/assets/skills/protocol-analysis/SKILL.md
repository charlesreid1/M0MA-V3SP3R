---
name: protocol-analysis
description: "Reverse-engineer binary protocols — CRC detection, CRC calculation, packet structure analysis"
---

# Protocol Analysis

Reverse-engineer checksums (CRC) and analyze binary protocol structures from captured packets. This skill is pure methodology — no host-side Python libraries. Apply it to hex dumps that came from `subghz_decode_raw`, `ble_read_char`, `ble_subscribe`, or `nfc_detect`.

## When to reach for this skill

Load `protocol-analysis` when you have:

- Multiple captured packets that share a structure (e.g., BLE notifications from a device, RF frames from a remote, NFC responses).
- Suspected checksum bytes at a known offset — usually the last 1, 2, or 4 bytes.
- Variable fields between captures (a counter, a command byte, or a data payload) with the rest constant.

Sub-GHz capture ID first: often you can read the .sub file header (`subghz_decode_raw`) to identify a known protocol (Princeton, CAME, NICE, KeeLoq…) before falling back to raw analysis. Do that first; only reach for CRC hunting when the protocol is proprietary.

## CRC detection

CRCs are almost always at the end of the packet. Try 1-byte (CRC-8), 2-byte (CRC-16), and 4-byte (CRC-32) tails.

### Method

1. Split each captured packet into `data | crc` at the tail offset.
2. Assume a CRC family and try common parameters. If a candidate polynomial + init + xor combination produces every captured `crc` from its corresponding `data`, that's the algorithm.
3. Verify: forge a new packet with a modified byte, compute the CRC, transmit, observe whether the target accepts it.

### Common polynomials to try first

| Width | Polynomial | Init | XorOut | Reflect in/out | Where you see it |
|-------|------------|------|--------|-----------------|--------------------|
| CRC-32 | `0x04C11DB7` | `0xFFFFFFFF` | `0xFFFFFFFF` | true / true | Ethernet, ZIP, MPEG-2 |
| CRC-16/CCITT | `0x1021` | `0xFFFF` | `0x0000` | false / false | Bluetooth, X.25, XMODEM |
| CRC-16/Modbus | `0x8005` | `0xFFFF` | `0x0000` | true / true | Modbus, USB |
| CRC-16/ARC | `0x8005` | `0x0000` | `0x0000` | true / true | LHA, ARC |
| CRC-8/MAXIM | `0x31` | `0x00` | `0x00` | true / true | Dallas 1-Wire, iButton |
| CRC-8/CCITT | `0x07` | `0x00` | `0x00` | false / false | ATM HEC |

### Ambiguity

You need at least 2, preferably 3+, packets that share structure but differ in the CRC-covered region. If two packets produce the same `data` and differ only in `crc`, you have a data collision, not a CRC. If the CRC never changes even when data does, you're either splitting the packet wrong or that field isn't a CRC.

### CRC at the head, not the tail

Some protocols (NFC-A tag responses, older RF pagers) put a header CRC at the start. If tail-CRC hunting fails on structurally-similar packets, try `crc | data` splits at offsets 1, 2, 4.

## CRC calculation once known

Once you know the algorithm, forging valid packets is deterministic — take the new data bytes, run them through the same polynomial + init + xor + reflection, append the result.

Watch endianness. Many RF protocols transmit CRC little-endian even when the algorithm computes big-endian. Compare the raw byte order in the capture (`subghz_decode_raw` output) to the computed CRC in both orderings; whichever matches is the on-wire order.

## Packet structure analysis

For a mystery packet, work in this order:

### 1. Byte-by-byte inspection

Print each byte in hex, decimal, and ASCII. Look for:

- **Repeating headers** — the first 1–4 bytes are usually a preamble or sync word (e.g., `0xAA`, `0xAA55`, `0xDEADBEEF`).
- **Length fields** — a byte or two whose value matches `packet_length - N` for some small N (header + length_field + data + crc → length_field = data + crc bytes).
- **Printable ASCII** — device names, protocol strings ("HELLO", "OK", "ACK"), sometimes plaintext model numbers.

### 2. Field decoding with a hypothesized format

Once you suspect a layout (e.g., `header:1, length:1, flags:1, addr:4, counter:2, data:N, crc:2`), lay the bytes out with that spec and see if the interpreted values make sense:

- Counter fields should increment across sequential captures.
- Address fields should be identical across captures from the same device.
- Flags should have low Hamming distance across similar operations.

### 3. Compare captures

The most powerful move: capture the same operation twice under slightly different conditions and diff the packets byte-by-byte. Fields that never change are structural (headers, addresses, static config). Fields that change with the operation are commands or data. Fields that increment monotonically are counters or timestamps.

For rolling-code RF: capture the same button press twice. Static portions are the fixed serial + button code; rolling portions are the encrypted counter (KeeLoq) or full-message rolling code (Security+ 2.0).

## Common IoT signal structures

```
[Preamble] [Sync] [Address/ID] [Command/Data] [Counter?] [Checksum]
```

- **Preamble**: 4–16 bits of alternating pattern (`0x55`, `0xAA`) for receiver AGC calibration.
- **Sync**: distinctive pulse/gap pattern or byte (`0x2DD4` in NRF24) separating preamble from data.
- **Address**: device identifier, typically 24–32 bits, fixed per remote.
- **Command/Data**: button ID or payload, varies per button press.
- **Counter**: monotonically increasing on each press (rolling-code protocols).
- **Checksum**: CRC or XOR across data, last 1–2 bytes usually.

## Vesper action mapping

| Step | Action | Risk |
|------|--------|------|
| Grab a captured Sub-GHz signal from Flipper storage | `read_file(path="/ext/subghz/foo.sub")` | LOW |
| Decode a raw .sub capture at the Flipper level | `subghz_decode_raw(path="/ext/subghz/foo.sub")` | LOW |
| Grab a BLE notification burst | `ble_subscribe(address=…, uuid=…, duration=5)` | MEDIUM |
| Read a BLE characteristic once | `ble_read_char(address=…, uuid=…)` | MEDIUM |
| Detect NFC / read RFID tag data | `nfc_detect`, `rfid_read` | LOW |

The reverse-engineering itself is pure reasoning — no tool call. Record any confirmed protocol weakness (broken CRC, unauthenticated command channel, replayable frame) via `vuln_submit`; useful `vuln_type` values include `writable_ble`, `writable_characteristic`, `unencrypted_protocol`, or `weak_encryption`.

## Notes

- All protocol reasoning is LOW risk — computation only, no device interaction until you decide to forge and transmit.
- Common IoT CRC polynomials (repeat from above for quick reference):
  - CRC-32: `0x04C11DB7` (Ethernet, ZIP)
  - CRC-16/CCITT: `0x1021`
  - CRC-16/Modbus: `0x8005`
  - CRC-8/MAXIM: `0x31`
- If CRC hunting fails on structurally-similar packets, the "checksum" bytes may not be a CRC at all. Common alternatives: XOR of preceding bytes, LRC (longitudinal redundancy check), or a simple additive checksum.
