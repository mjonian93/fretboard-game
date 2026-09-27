package com.fretboardtrainer.game

import com.fretboardtrainer.data.Stat
import com.fretboardtrainer.music.FretPosition
import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Interval
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun noteTimeFollowsTimingMode() {
        assertNull(SessionConfig(timing = TimingMode.UNTIMED).noteMillis(60))
        assertEquals(5000L, SessionConfig(timing = TimingMode.SECONDS, seconds = 5).noteMillis(60))
        assertEquals(15000L, SessionConfig(timing = TimingMode.SECONDS, seconds = 5).noteMillis(60, notes = 3))
        // 4 beats at 120 BPM = 2 s; at 60 BPM = 4 s.
        assertEquals(2000L, SessionConfig(timing = TimingMode.TEMPO, beatsPerNote = 4).noteMillis(120))
        assertEquals(4000L, SessionConfig(timing = TimingMode.TEMPO, beatsPerNote = 4).noteMillis(60))
    }

    @Test
    fun weakItemsArePickedMoreOften() {
        assertEquals(3.0, Stat().pickWeight, 1e-9)
        val known = (1..20).fold(Stat()) { s, _ -> s.record(true, 1000) }
        val weak = (1..20).fold(Stat()) { s, i -> s.record(i % 4 == 0, 1000) }
        assertTrue(known.pickWeight < 1.3)
        assertTrue(weak.pickWeight > 3.5)
        assertEquals(1000L, known.averageMillis)

        val random = Random(1)
        val picks = (1..2000).map { random.weightedPick(listOf("known", "weak")) { if (it == "known") known.pickWeight else weak.pickWeight } }
        assertTrue(picks.count { it == "weak" } > picks.count { it == "known" } * 2)
    }

    @Test
    fun nameNoteCandidatesRespectSettings() {
        val settings = NameNoteSettings(maxFret = 5, strings = setOf(5, 6), naturalsOnly = true)
        val all = nameNoteCandidates(settings, Tuning.STANDARD)
        assertTrue(all.all { it.string in settings.strings && it.fret in 0..5 })
        assertTrue(all.all { Notes.isNatural(Tuning.STANDARD.midiAt(it)) })
        assertEquals(12, nameNoteCandidates(settings.copy(naturalsOnly = false), Tuning.STANDARD).size)
    }

    @Test
    fun findTargetsOfferEveryOctaveWhenExact() {
        val any = findTargets(FindNoteSettings(position = 2, naturalsOnly = true), Tuning.STANDARD)
        assertEquals(7, any.size)
        assertTrue(any.all { it.midi == null })
        assertEquals(12, findTargets(FindNoteSettings(position = 2), Tuning.STANDARD).size)

        val exact = findTargets(FindNoteSettings(position = 2, exactOctave = true), Tuning.STANDARD)
        val e = exact.filter { it.pitchClass == 4 }
        assertEquals(listOf(52, 64), e.map { it.midi }) // E3 (D string fret 2), E4 (B string fret 5)
        assertEquals((0 until e.size).toList(), e.map { it.octaveIndex })
    }

    @Test
    fun judgingAnAttempt() {
        val region = HandPosition(2).region(Tuning.STANDARD)
        val octaves = region.octavesOf(4) // E
        val higher = FindTarget(4, octaves[1], 1, octaves.size)
        assertEquals(Attempt.CORRECT, higher.judge(octaves[1], region))
        assertEquals(Attempt.WRONG_OCTAVE, higher.judge(octaves[0], region))
        assertEquals(Attempt.WRONG_NOTE, higher.judge(octaves[1] + 1, region))
        assertEquals(Attempt.OUTSIDE_REGION, higher.judge(88, region))

        val anyE = FindTarget(4, null, -1, octaves.size)
        octaves.forEach { assertEquals(Attempt.CORRECT, anyE.judge(it, region)) }
    }

    @Test
    fun findAllRegionIsAPositionOrTheWholeNeck() {
        assertEquals(0..12, FindAllSettings(position = 0, maxFret = 12).region(Tuning.STANDARD).frets)
        assertEquals(4..9, FindAllSettings(position = 5).region(Tuning.STANDARD).frets)
    }

    @Test
    fun intervals() {
        // Root A2 (string 6 fret 5), major 3rd above = C#3.
        val q = IntervalQuestion(FretPosition(6, 5), 45, Interval.MAJOR_3RD)
        assertEquals("C#3", Notes.name(q.targetMidi))
        assertTrue(q.accepts(49, anyOctave = false))
        assertFalse(q.accepts(61, anyOctave = false))
        assertTrue(q.accepts(61, anyOctave = true))
        // C#3 near fret 5: string 6 fret 9 and string 5 fret 4.
        assertEquals(setOf(FretPosition(6, 9), FretPosition(5, 4)), q.answerPositions(Tuning.STANDARD).toSet())
    }

    @Test
    fun calibrationPicksAGateBetweenNoiseAndNote() {
        val gate = calibratedGateDb(noiseDb = -70f, noteDb = -30f)!!
        assertTrue(gate > -70f + 5 && gate < -30f - 10)
        assertNull(calibratedGateDb(noiseDb = -40f, noteDb = -35f))
    }

    @Test
    fun tunerFindsTheNearestString() {
        val (string, cents) = nearestString(Tuning.STANDARD, 110.0 * Math.pow(2.0, 10 / 1200.0))
        assertEquals(5, string)
        assertEquals(10.0, cents, 0.01)
        assertEquals(6, nearestString(Tuning.DROP_D, 73.42).first)
    }
}
