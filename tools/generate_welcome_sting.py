#!/usr/bin/env python3
"""Generates the launch sting at app/src/main/res/raw/welcome_sting.wav.

The audio is synthesised rather than sourced so the asset has no licensing
question attached to it and can be regenerated or retuned from this file.

The sound is a C major arpeggio -- C5, E5, G5 -- with each note struck a
beat after the last and left to ring. Each voice is a fundamental plus two
quieter harmonics with an exponential decay, which is roughly how a struck
bell behaves and reads as "bright" without being shrill. The whole thing is
1.5 seconds so it never delays someone who opened the app to log a set.

Run:  python tools/generate_welcome_sting.py
"""

import math
import os
import struct
import wave

# 22.05 kHz halves the file against CD rate and costs nothing here: the
# highest partial in the sting is G5's third harmonic at about 2.4 kHz, far
# below the 11 kHz this can represent.
SAMPLE_RATE = 22050
DURATION = 1.5
PEAK = 0.72  # headroom, so the mix never clips after summing voices

# (frequency in Hz, start time in seconds, decay constant)
# C5, E5, G5. The G is struck last and given the slowest decay so the chord
# resolves upward and the final note is the one left ringing.
NOTES = [
    (523.25, 0.00, 2.6),
    (659.25, 0.15, 2.4),
    (783.99, 0.30, 1.7),
]

# Relative amplitude of the fundamental and the next two partials. Dropping
# off steeply keeps it warm; equal partials would sound like a buzzer.
HARMONICS = [(1.0, 1.0), (2.0, 0.30), (3.0, 0.12)]

ATTACK = 0.008  # 8 ms, enough to avoid a click on note onset
RELEASE = 0.35  # final fade so the file ends in silence


def voice(t_since_onset, frequency, decay):
    """One struck note at t seconds after it was hit."""
    if t_since_onset < 0:
        return 0.0
    envelope = math.exp(-decay * t_since_onset)
    if t_since_onset < ATTACK:
        envelope *= t_since_onset / ATTACK
    sample = sum(
        amplitude * math.sin(2 * math.pi * frequency * ratio * t_since_onset)
        for ratio, amplitude in HARMONICS
    )
    return sample * envelope


def build():
    frames = bytearray()
    total = int(SAMPLE_RATE * DURATION)
    raw = []
    for index in range(total):
        t = index / SAMPLE_RATE
        value = sum(voice(t - start, freq, decay) for freq, start, decay in NOTES)
        # Tail fade, so the sting ends rather than being cut off.
        remaining = DURATION - t
        if remaining < RELEASE:
            value *= remaining / RELEASE
        raw.append(value)

    loudest = max(abs(v) for v in raw) or 1.0
    for value in raw:
        scaled = int(max(-1.0, min(1.0, value / loudest * PEAK)) * 32767)
        frames += struct.pack("<h", scaled)
    return bytes(frames)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(here, os.pardir, "app", "src", "main", "res", "raw", "welcome_sting.wav")
    target = os.path.normpath(target)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with wave.open(target, "w") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(build())
    print(f"wrote {target} ({os.path.getsize(target) / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
