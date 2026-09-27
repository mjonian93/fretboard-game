package com.fretboardtrainer.audio

import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.PitchReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class YinPitchDetectorTest {
    private val sampleRate = 44100
    private val windowSize = 4096
    private val detector = YinPitchDetector(sampleRate, windowSize)

    /** Plucked-string-like tone: decaying harmonics plus a little noise. */
    private fun pluck(frequency: Double, harmonics: DoubleArray, noise: Double = 0.01, seed: Int = 1): FloatArray {
        val random = Random(seed)
        return FloatArray(windowSize) { i ->
            val t = i.toDouble() / sampleRate
            var v = 0.0
            harmonics.forEachIndexed { h, amp -> v += amp * sin(2 * PI * frequency * (h + 1) * t) }
            (0.3 * v * exp(-t * 3) + noise * (random.nextDouble() * 2 - 1)).toFloat()
        }
    }

    @Test
    fun detectsEveryNoteOnTheNeck() {
        // Low E (E2) through fret 16 of the high E string (G#5).
        for (midi in 40..80) {
            val f = Notes.midiToFrequency(midi.toDouble())
            val detected = detector.detect(pluck(f, doubleArrayOf(1.0, 0.6, 0.4, 0.25, 0.15)))
            val reading = PitchReading.fromFrequency(detected ?: error("no pitch for ${Notes.name(midi)}"))
            assertEquals("for ${Notes.name(midi)}", midi, reading.midi)
            assertEquals(0.0, reading.cents, 5.0)
        }
    }

    @Test
    fun weakFundamentalStillGivesTheRightOctave() {
        // Phone mics lose the low E fundamental; the 2nd harmonic dominates.
        for (midi in 40..52) {
            val f = Notes.midiToFrequency(midi.toDouble())
            val detected = detector.detect(pluck(f, doubleArrayOf(0.25, 1.0, 0.6, 0.4)))!!
            assertEquals("for ${Notes.name(midi)}", midi, PitchReading.fromFrequency(detected).midi)
        }
    }

    @Test
    fun silenceAndNoiseGiveNoPitch() {
        assertNull(detector.detect(FloatArray(windowSize)))
        val random = Random(7)
        assertNull(detector.detect(FloatArray(windowSize) { (random.nextDouble() * 2 - 1).toFloat() * 0.3f }))
    }
}
