package com.fretboardtrainer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    @Test
    fun namesUseAmericanNotation() {
        assertEquals("A4", Notes.name(69))
        assertEquals("E2", Notes.name(40))
        assertEquals("C#4", Notes.name(61))
        assertEquals("C#/Db", Notes.fullName(61, withOctave = false))
        assertEquals("E", Notes.fullName(64, withOctave = false))
    }

    @Test
    fun standardTuning() {
        assertEquals("E2", Notes.name(Tuning.midiAt(FretPosition(6, 0))))
        assertEquals("E4", Notes.name(Tuning.midiAt(FretPosition(1, 0))))
        // 5th fret of each string equals the next open string, except G -> B (4th fret).
        assertEquals(Tuning.openMidi(5), Tuning.midiAt(FretPosition(6, 5)))
        assertEquals(Tuning.openMidi(2), Tuning.midiAt(FretPosition(3, 4)))
    }

    @Test
    fun frequencyRoundTrip() {
        assertEquals(69.0, Notes.frequencyToMidi(440.0), 1e-9)
        assertEquals(82.41, Notes.midiToFrequency(40.0), 0.01)
        val reading = PitchReading.fromFrequency(445.0)
        assertEquals(69, reading.midi)
        assertTrue(reading.cents > 15 && reading.cents < 25)
    }

    @Test
    fun naturals() {
        assertTrue(Notes.isNatural(60))
        assertFalse(Notes.isNatural(63))
    }
}
