package com.fretboardtrainer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandPositionTest {
    @Test
    fun positionTwoCoversFretsOneToSix() {
        assertEquals(1..6, HandPosition(2).frets)
        assertEquals(0..5, HandPosition(1).frets)
        assertEquals(11..16, HandPosition(HandPosition.MAX).frets)
    }

    @Test
    fun everyNoteIsReachableInEveryPositionOfEveryTuning() {
        for (tuning in Tuning.ALL) {
            for (index in HandPosition.MIN..HandPosition.MAX) {
                val region = HandPosition(index).region(tuning)
                for (pc in 0 until 12) {
                    val octaves = region.octavesOf(pc)
                    assertTrue("pc $pc in position $index, ${tuning.name}", octaves.isNotEmpty())
                    assertEquals(octaves.sorted(), octaves)
                }
            }
        }
    }

    @Test
    fun ebInPositionTwo() {
        // Eb3 (A string fret 6 / D string fret 1) and Eb4 (B string fret 4); Eb5 is fret 11 on high E: out.
        val eb = HandPosition(2).region(Tuning.STANDARD).octavesOf(3).map { Notes.name(it) }
        assertEquals(listOf("D#3", "D#4"), eb)
    }

    @Test
    fun wholeNeckHasEveryE() {
        val region = FretRegion(0..12, Tuning.STANDARD)
        assertEquals(listOf("E2", "E3", "E4", "E5"), region.octavesOf(4).map { Notes.name(it) })
        // E4 lives in 3 places: open high E, B string fret 5, G string fret 9.
        assertEquals(3, region.positionsOfMidi(64).size)
    }
}
