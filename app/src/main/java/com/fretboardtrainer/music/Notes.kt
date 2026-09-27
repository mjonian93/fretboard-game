package com.fretboardtrainer.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Note helpers based on MIDI numbers (60 = C4, 69 = A4 = 440 Hz). */
object Notes {
    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    fun pitchClass(midi: Int): Int = Math.floorMod(midi, 12)

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun isNatural(midi: Int): Boolean = SHARP_NAMES[pitchClass(midi)].length == 1

    /** American notation, e.g. "A2", "C#4". */
    fun name(midi: Int, withOctave: Boolean = true): String =
        SHARP_NAMES[pitchClass(midi)] + if (withOctave) octave(midi).toString() else ""

    /** Both spellings for accidentals, e.g. "C#/Db"; naturals unchanged. */
    fun fullName(midi: Int, withOctave: Boolean = true): String {
        val pc = pitchClass(midi)
        val base = if (isNatural(midi)) SHARP_NAMES[pc] else "${SHARP_NAMES[pc]}/${FLAT_NAMES[pc]}"
        return base + if (withOctave) octave(midi).toString() else ""
    }

    fun frequencyToMidi(frequency: Double): Double = 69.0 + 12.0 * log2(frequency / 440.0)

    fun midiToFrequency(midi: Double): Double = 440.0 * 2.0.pow((midi - 69.0) / 12.0)
}

/** Frets drawn on the neck. */
const val FRET_COUNT = 16

/** A place on the neck. String 1 is the high E, string 6 the low E; fret 0 is the open string. */
data class FretPosition(val string: Int, val fret: Int)

object Tuning {
    /** Standard tuning, index 0 = string 1 (E4) ... index 5 = string 6 (E2). */
    private val STANDARD = intArrayOf(64, 59, 55, 50, 45, 40)

    const val STRING_COUNT = 6

    fun openMidi(string: Int): Int = STANDARD[string - 1]

    fun midiAt(position: FretPosition): Int = openMidi(position.string) + position.fret

    fun stringLabel(string: Int): String = Notes.name(openMidi(string), withOctave = false)
}

/** A detected pitch, snapped to the nearest equal-tempered note. */
data class PitchReading(val frequency: Double, val midi: Int, val cents: Double) {
    companion object {
        fun fromFrequency(frequency: Double): PitchReading {
            val exact = Notes.frequencyToMidi(frequency)
            val nearest = exact.roundToInt()
            return PitchReading(frequency, nearest, (exact - nearest) * 100.0)
        }
    }
}
