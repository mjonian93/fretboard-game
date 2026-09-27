package com.fretboardtrainer.audio

import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.PitchReading
import org.junit.Assert.assertEquals
import org.junit.Test

class SynthTest {
    /** The plucked sound is in tune: our own pitch detector hears the right note. */
    @Test
    fun pluckedNotesAreInTune() {
        val detector = YinPitchDetector(Synth.SAMPLE_RATE, 4096)
        for (midi in listOf(40, 45, 52, 57, 64, 69, 76)) {
            val pcm = Synth.pluck(Notes.midiToFrequency(midi.toDouble()))
            val window = FloatArray(4096) { pcm[4410 + it] / 32768f }
            val reading = PitchReading.fromFrequency(detector.detect(window)!!)
            assertEquals(Notes.name(midi), Notes.name(reading.midi))
        }
    }
}
