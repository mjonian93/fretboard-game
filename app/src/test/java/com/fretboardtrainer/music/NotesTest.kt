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
        val t = Tuning.STANDARD
        assertEquals("E2", Notes.name(t.midiAt(FretPosition(6, 0))))
        assertEquals("E4", Notes.name(t.midiAt(FretPosition(1, 0))))
        // 5th fret of each string equals the next open string, except G -> B (4th fret).
        assertEquals(t.openMidi(5), t.midiAt(FretPosition(6, 5)))
        assertEquals(t.openMidi(2), t.midiAt(FretPosition(3, 4)))
        assertEquals("EADGBE", t.letters)
    }

    @Test
    fun alternateTunings() {
        assertEquals("DADGBE", Tuning.DROP_D.letters)
        assertEquals("DADGAD", Tuning.DADGAD.letters)
        assertEquals("DGDGBD", Tuning.OPEN_G.letters)
        assertEquals("Eb Ab Db Gb Bb Eb", Tuning.HALF_STEP_DOWN.letters)
        assertEquals(Tuning.STANDARD, Tuning.byId("nonsense"))
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
