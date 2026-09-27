package com.fretboardtrainer.game

import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameLogicTest {
    @Test
    fun stabilizerReportsANoteOnceAfterItSettles() {
        val s = NoteStabilizer(framesRequired = 3)
        assertNull(s.feed(45))
        assertNull(s.feed(57)) // attack transient an octave up
        assertNull(s.feed(45))
        assertNull(s.feed(45))
        assertEquals(45, s.feed(45))
        assertNull(s.feed(null)) // one dropped frame keeps the note
        assertNull(s.feed(45))
        assertNull(s.feed(45))
    }

    @Test
    fun stabilizerReportsTheSameNoteAgainAfterSilence() {
        val s = NoteStabilizer(framesRequired = 2)
        s.feed(45)
        assertEquals(45, s.feed(45))
        s.feed(null)
        s.feed(null)
        s.feed(45)
        assertEquals(45, s.feed(45))
    }

    @Test
    fun scoreTracksStreaks() {
        val score = Score().hit().hit().miss().hit()
        assertEquals(3, score.hits)
        assertEquals(4, score.rounds)
        assertEquals(1, score.streak)
        assertEquals(2, score.best)
    }

    @Test
    fun fretPickerRespectsSettingsAndNeverRepeatsANoteName() {
        val settings = NameNoteSettings(maxFret = 5, strings = setOf(5, 6), naturalsOnly = true)
        val picker = FretTargetPicker(Random(3))
        var previous = picker.next(settings, null)!!
        repeat(200) {
            val next = picker.next(settings, previous)!!
            assertTrue(next.string in settings.strings && next.fret in 0..5)
            assertTrue(Notes.isNatural(Tuning.midiAt(next)))
            assertNotEquals(Notes.pitchClass(Tuning.midiAt(previous)), Notes.pitchClass(Tuning.midiAt(next)))
            previous = next
        }
    }

    @Test
    fun findPickerOffersEveryOctaveWhenExact() {
        val any = FindTargetPicker().candidates(FindNoteSettings(position = 2, naturalsOnly = true))
        assertEquals(7, any.size)
        assertTrue(any.all { it.midi == null })

        val exact = FindTargetPicker().candidates(FindNoteSettings(position = 2, naturalsOnly = true, exactOctave = true))
        val e = exact.filter { it.pitchClass == 4 }
        assertEquals(listOf(52, 64), e.map { it.midi }) // E3 (D string fret 2), E4 (B string fret 5)
        assertEquals(e.size, e.first().octaveCount)
        assertEquals((0 until e.size).toList(), e.map { it.octaveIndex })
    }

    @Test
    fun judgingAnAttempt() {
        val position = HandPosition(2)
        val octaves = position.octavesOf(4) // E
        val middle = FindTarget(4, octaves[1], 1, octaves.size)
        assertEquals(Attempt.Correct, middle.judge(octaves[1], position))
        assertEquals(Attempt.WrongOctave, middle.judge(octaves[0], position))
        assertEquals(Attempt.WrongNote, middle.judge(octaves[1] + 1, position))
        assertEquals(Attempt.OutsidePosition, middle.judge(88, position))

        val anyE = FindTarget(4, null, -1, octaves.size)
        octaves.forEach { assertEquals(Attempt.Correct, anyE.judge(it, position)) }
    }
}
