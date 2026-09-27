package com.fretboardtrainer.music

import kotlin.math.max

/**
 * A hand position named after the fret under the index finger. Position N covers
 * frets N..N+3 (one finger per fret) plus a one-fret stretch either side.
 */
data class HandPosition(val index: Int) {
    val frets: IntRange = max(0, index - 1)..(index + 4)

    fun positions(): List<FretPosition> =
        (1..Tuning.STRING_COUNT).flatMap { string -> frets.map { FretPosition(string, it) } }

    fun positionsOf(pitchClass: Int): List<FretPosition> =
        positions().filter { Notes.pitchClass(Tuning.midiAt(it)) == pitchClass }

    /** Distinct pitches of [pitchClass] reachable in this position, lowest first. */
    fun octavesOf(pitchClass: Int): List<Int> =
        positionsOf(pitchClass).map(Tuning::midiAt).distinct().sorted()

    companion object {
        const val MIN = 1
        const val MAX = FRET_COUNT - 4
    }
}
