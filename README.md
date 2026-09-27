# Fretboard Trainer

Android app for learning the notes on the guitar neck: 16 frets, American note names,
standard and alternate tunings, right- or left-handed.

## Modes

1. **Name the note** (no guitar): a fret lights up (and sounds); tap its note name.
2. **Find the note** (guitar + mic): choose a hand position (position N = index finger on fret N,
   covering frets N..N+3 plus one-fret stretches). A note name is shown; play it.
   With *Specific octave* on, dots under the name say which occurrence in the position is wanted:
   `●○○` lowest, `○●○` middle, `○○●` highest. Afterwards every place the note lives is shown.
3. **Find them all** (guitar + mic): play every octave of a note, in a position or across the neck.
4. **Intervals** (guitar + mic): a root lights up; play the requested interval above it
   (chord tones by default: 3rds, 5th, 7ths).

Plus a **Tuner** (with mic calibration) and a **Progress** screen: fretboard accuracy heatmap and
per-note / per-interval accuracy and answer time.

Every mode has:
- **Timing**: untimed, seconds per note, or **tempo** (BPM × beats per note) with metronome,
  live −/+ BPM control and optional auto speed-up (+5 BPM every 5 correct in a row).
- **Sessions** of 10/20/50 notes (or endless) ending with a summary: accuracy, average time,
  best streak, tempo reached, and the notes to practise more.
- **Adaptive practice**: lifetime stats are saved, and notes you miss come up more often.

## How it works

- Pitch detection: YIN (`audio/YinPitchDetector.kt`), 44.1 kHz, 4096-sample window, with a
  noise gate set by the mic sensitivity (or calibration). A note must hold ~190 ms to count.
- The mic hears pitch, not strings: the same pitch on two strings is indistinguishable.
- `game/GameViewModel.kt` is the shared engine (timing, tempo, sessions, stats, mic);
  each mode only implements `playRound()`.

## Toolchain

Everything lives in `/mnt/SSD2/devtools` (JDK 21, Android SDK, Gradle cache, emulator images).
Load it in each shell:

```sh
. /mnt/SSD2/devtools/env.sh
```

## Build, test, run

```sh
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/

emulator -avd fretboard -allow-host-audio &   # start the emulator (uses the PC microphone)
./gradlew installDebug
adb shell am start -n com.fretboardtrainer/.MainActivity
```

If the emulator doesn't hear the PC mic, run `adb emu avd hostmicon`, or enable it in the
emulator's *Extended controls → Microphone → "Virtual microphone uses host audio input"*.
The mode screens show a mic level meter; the white tick is the sensitivity threshold.
