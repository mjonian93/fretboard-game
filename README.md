# Fretboard Trainer

Android app for learning the notes on the guitar neck (standard tuning, 16 frets, American note names).

## Modes

1. **Name the note** (no guitar): a fret lights up on the fretboard; tap its note name.
2. **Find the note** (guitar + mic): choose a hand position (position N = index finger on fret N,
   covering frets N..N+3 plus one-fret stretches). A note name is shown; play it, the app listens.
   With *Specific octave* on, dots under the name say which occurrence in the position is wanted:
   `●○○` lowest, `○●○` middle, `○○●` highest. After each note, every place it lives in the position is shown.

Pitch detection is the YIN algorithm (`app/src/main/java/com/fretboardtrainer/audio/YinPitchDetector.kt`),
44.1 kHz, 4096-sample window; a note must hold for ~4 frames (~190 ms) to count.

## Toolchain

Everything lives in `/mnt/SSD2/devtools` (JDK 21, Android SDK, Gradle cache, emulator images).
Load it in each shell:

```sh
. /mnt/SSD2/devtools/env.sh
```

## Build, test, run

```sh
./gradlew testDebugUnitTest      # unit tests (notes, positions, pitch detection, game logic)
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/

emulator -avd fretboard -allow-host-audio &   # start the emulator (uses the PC microphone)
./gradlew installDebug
adb shell am start -n com.fretboardtrainer/.MainActivity
```

If the emulator doesn't hear the PC mic, enable it in the emulator's *Extended controls → Microphone →
"Virtual microphone uses host audio input"*, or run `adb emu avd hostmicon`.
