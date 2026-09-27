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
    fun everyNoteIsReachableInEveryPosition() {
        for (index in HandPosition.MIN..HandPosition.MAX) {
            val position = HandPosition(index)
            for (pc in 0 until 12) {
                val octaves = position.octavesOf(pc)
                assertTrue("pc $pc in position $index", octaves.size in 2..3)
                assertEquals(octaves.sorted(), octaves)
            }
        }
    }

    @Test
    fun ebInPositionTwo() {
        // Eb2 isn't on a guitar; Eb3 (A string fret 6, D string fret 1), Eb4 (B string fret 4), Eb5 is fret 11 on high E: out.
        val eb = HandPosition(2).octavesOf(3).map { Notes.name(it) }
        assertEquals(listOf("D#3", "D#4"), eb)
    }
}
