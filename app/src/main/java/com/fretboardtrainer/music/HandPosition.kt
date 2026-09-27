package com.fretboardtrainer.music

import kotlin.math.max

/** A block of frets across all strings: a hand position or the whole neck. */
data class FretRegion(val frets: IntRange, val tuning: Tuning) {
    fun positions(): List<FretPosition> =
        (1..STRING_COUNT).flatMap { string -> frets.map { FretPosition(string, it) } }

    fun positionsOf(pitchClass: Int): List<FretPosition> =
        positions().filter { Notes.pitchClass(tuning.midiAt(it)) == pitchClass }

    fun positionsOfMidi(midi: Int): List<FretPosition> = positions().filter { tuning.midiAt(it) == midi }

    /** Distinct pitches of [pitchClass] reachable in this region, lowest first. */
    fun octavesOf(pitchClass: Int): List<Int> =
        positionsOf(pitchClass).map(tuning::midiAt).distinct().sorted()
}

/**
 * A hand position named after the fret under the index finger. Position N covers
 * frets N..N+3 (one finger per fret) plus a one-fret stretch either side.
 */
data class HandPosition(val index: Int) {
    val frets: IntRange = max(0, index - 1)..(index + 4)

    fun region(tuning: Tuning) = FretRegion(frets, tuning)

    companion object {
        const val MIN = 1
        const val MAX = FRET_COUNT - 4
    }
}
