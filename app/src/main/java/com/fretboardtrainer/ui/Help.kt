package com.fretboardtrainer.ui

/** How-to-play text for each mode, shown on first visit and from the ⓘ button. */
object Help {
    val NAME_NOTE = listOf(
        "A note lights up in green on the fretboard (and you hear it).",
        "Tap its name on the buttons below before time runs out.",
        "This trains reading the neck: fret → note name. No guitar needed.",
        "In ⚙ settings: frets, strings, sharps/flats, sound, timing and session length.",
    )

    val FIND_NOTE = listOf(
        "Pick a hand position: position 2 = index finger on fret 2. You play frets 2–5, stretching to 1 and 6.",
        "A note name appears (e.g. Eb). Find it inside the position and play it; the app listens through the mic.",
        "With \"Specific octave\" on, the dots under the name say which one: ●○○ lowest, ○●○ middle, ○○● highest in the position.",
        "After each note you see every place it lives in the position.",
        "The mic hears pitch, not strings, so it's up to you to stay in position.",
    )

    val FIND_ALL = listOf(
        "A note name appears. Play it in every octave you can reach: in a hand position, or across the whole neck.",
        "It counts octaves, not places: G in frets 0–12 lives in 7 places but only 3 octaves (G2, G3, G4).",
        "The mic hears pitch, not strings, so each octave counts once wherever you play it, and then all its places light up.",
        "The dots show the octaves, lowest to highest, filling in as you find them.",
        "The time allowed grows with the number of octaves to find.",
    )

    val INTERVALS = listOf(
        "A root note lights up on the fretboard, with its name.",
        "Play the interval asked for above it, e.g. \"major 3rd above A\" → C#.",
        "By default it must be the note just above the root; turn on \"Any octave\" to accept it anywhere.",
        "\"Chord tones\" (3rds, 5th, 7ths) is a great start: they're the notes that build chords.",
        "After each question, the places to play the answer near the root are shown.",
    )

    val TUNER = listOf(
        "Play one string at a time. The needle shows how sharp (+) or flat (−) you are; green means in tune.",
        "The strings of your chosen tuning are listed; the one you're playing is highlighted.",
        "Calibrate mic: stay quiet for 2 seconds, then play a note and let it ring. The app sets its sensitivity between your room noise and your guitar.",
    )
}
