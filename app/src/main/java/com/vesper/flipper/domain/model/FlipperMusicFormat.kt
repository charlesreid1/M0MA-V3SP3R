package com.vesper.flipper.domain.model

/**
 * Ported from FlipperAgent's `music/formatter.py` — returned to the model when it calls
 * `music_get_format` so it knows how to author a song for `music_play`.
 */
internal const val FLIPPER_MUSIC_FORMAT_SPEC: String = """FMF (Flipper Music Format) Specification (Flipper Music Player)
==========================================

FMF is a simple line-based key/value format used by Flipper Zero's Music Player app.

FILE FORMAT (FMF v0)
-------------------
An FMF file is plain text with these header lines:

  Filetype: Flipper Music Format
  Version: 0
  BPM: <bpm>
  Duration: <duration>
  Octave: <octave>
  Notes: <notes>

Where:
  - BPM: Beats per minute (tempo), typically 60-200
  - Duration: Default note duration (1=whole, 2=half, 4=quarter, 8=eighth, 16=sixteenth)
  - Octave: Default octave (3-7, where 4 is middle C)
  - Notes: Comma-separated note tokens

NOTE FORMAT
-----------
Notes are comma-separated. Each token is:

  [DURATION]<NOTE>[ACCIDENTAL][OCTAVE]

Where:
  - DURATION: optional duration override (1, 2, 4, 8, 16)
  - NOTE: A, B, C, D, E, F, G or P (pause)
  - ACCIDENTAL: optional '#' (sharp) or 'b' (flat)
  - OCTAVE: optional octave override (3-7)

Examples from a known-good device file:
  E6, P, 4P, F#, B4, 8A#5

NOTES
-----
- Notes are separated by commas (spaces after commas are allowed)
- Rests use 'P'
- Sharps use '#' and flats use 'b'
- When a note has no explicit duration, it uses the file's Duration header
- When a note has no explicit octave, it uses the file's Octave header

COMPLETE EXAMPLE
----------------
Filetype: Flipper Music Format
Version: 0
BPM: 120
Duration: 4
Octave: 4
Notes: 4C, 4C, 8C, 4D, 4E, 4C, 4E, 4D

TIPS
----
- Start with simple melodies using default octave/duration
- Use rests (P) for pauses between phrases
- Adjust BPM to match the song's natural tempo
- Test with short melodies first

Legacy compatibility:
---------------------
This project will also accept the legacy single-line format:

  BPM=120:DURATION=4:OCTAVE=4: 4C 4D 4E

...and will normalize it to the FMF v0 format when saving to the device."""
