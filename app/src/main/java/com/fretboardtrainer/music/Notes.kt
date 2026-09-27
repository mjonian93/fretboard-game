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

const val STRING_COUNT = 6

/** A place on the neck. String 1 is the highest-pitched string, string 6 the lowest; fret 0 is open. */
data class FretPosition(val string: Int, val fret: Int)

/** Open-string pitches, index 0 = string 1 (highest) ... index 5 = string 6 (lowest). */
data class Tuning(val id: String, val name: String, val openMidi: List<Int>, private val customLetters: String? = null) {
    fun openMidi(string: Int): Int = openMidi[string - 1]

    fun midiAt(position: FretPosition): Int = openMidi(position.string) + position.fret

    fun stringLabel(string: Int): String = Notes.name(openMidi(string), withOctave = false)

    /** e.g. "EADGBE", low to high. */
    val letters: String get() = customLetters ?: openMidi.reversed().joinToString("") { Notes.name(it, withOctave = false) }

    companion object {
        val STANDARD = Tuning("standard", "Standard", listOf(64, 59, 55, 50, 45, 40))
        val HALF_STEP_DOWN = Tuning("half_down", "Eb standard", listOf(63, 58, 54, 49, 44, 39), "Eb Ab Db Gb Bb Eb")
        val DROP_D = Tuning("drop_d", "Drop D", listOf(64, 59, 55, 50, 45, 38))
        val DADGAD = Tuning("dadgad", "DADGAD", listOf(62, 57, 55, 50, 45, 38))
        val OPEN_G = Tuning("open_g", "Open G", listOf(62, 59, 55, 50, 43, 38))
        val OPEN_D = Tuning("open_d", "Open D", listOf(62, 57, 54, 50, 45, 38))

        val ALL = listOf(STANDARD, HALF_STEP_DOWN, DROP_D, DADGAD, OPEN_G, OPEN_D)

        fun byId(id: String): Tuning = ALL.firstOrNull { it.id == id } ?: STANDARD
    }
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

enum class Interval(val semitones: Int, val shortName: String, val longName: String) {
    MINOR_2ND(1, "m2", "minor 2nd"),
    MAJOR_2ND(2, "M2", "major 2nd"),
    MINOR_3RD(3, "m3", "minor 3rd"),
    MAJOR_3RD(4, "M3", "major 3rd"),
    PERFECT_4TH(5, "P4", "perfect 4th"),
    TRITONE(6, "TT", "tritone"),
    PERFECT_5TH(7, "P5", "perfect 5th"),
    MINOR_6TH(8, "m6", "minor 6th"),
    MAJOR_6TH(9, "M6", "major 6th"),
    MINOR_7TH(10, "m7", "minor 7th"),
    MAJOR_7TH(11, "M7", "major 7th"),
    OCTAVE(12, "P8", "octave");

    companion object {
        /** The notes that build 7th chords: 3rds, 5th, 7ths. */
        val CHORD_TONES = setOf(MINOR_3RD, MAJOR_3RD, PERFECT_5TH, MINOR_7TH, MAJOR_7TH)
    }
}
