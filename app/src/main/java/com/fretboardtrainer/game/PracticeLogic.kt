package com.fretboardtrainer.game

import kotlinx.serialization.Serializable
import kotlin.random.Random

data class Score(val hits: Int = 0, val rounds: Int = 0, val streak: Int = 0, val best: Int = 0) {
    fun hit(): Score = copy(hits = hits + 1, rounds = rounds + 1, streak = streak + 1, best = maxOf(best, streak + 1))
    fun miss(): Score = copy(rounds = rounds + 1, streak = 0)
    fun breakStreak(): Score = copy(streak = 0)
}

@Serializable
enum class TimingMode { UNTIMED, SECONDS, TEMPO }

/** How a session is paced and how long it lasts. */
@Serializable
data class SessionConfig(
    val timing: TimingMode = TimingMode.SECONDS,
    val seconds: Int = 6,
    val bpm: Int = 60,
    /** In tempo mode, each note gets this many beats. */
    val beatsPerNote: Int = 4,
    val metronome: Boolean = true,
    /** In tempo mode, +[SPEED_UP_BPM] BPM after every [SPEED_UP_EVERY] correct answers in a row. */
    val autoSpeedUp: Boolean = false,
    /** Notes per session; 0 = endless. */
    val length: Int = 20,
) {
    /** Time allowed for [notes] notes at [bpm], or null when untimed. */
    fun noteMillis(bpm: Int, notes: Int = 1): Long? = when (timing) {
        TimingMode.UNTIMED -> null
        TimingMode.SECONDS -> seconds * 1000L * notes
        TimingMode.TEMPO -> beatsPerNote * notes * 60_000L / bpm
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 240
        const val SPEED_UP_BPM = 5
        const val SPEED_UP_EVERY = 5
    }
}

interface ModeSettings {
    val session: SessionConfig
}

/** Picks from [pool] with probability proportional to [weight]. */
fun <T> Random.weightedPick(pool: List<T>, weight: (T) -> Double): T? {
    if (pool.isEmpty()) return null
    val weights = pool.map(weight)
    var pick = nextDouble() * weights.sum()
    for ((i, w) in weights.withIndex()) {
        pick -= w
        if (pick < 0) return pool[i]
    }
    return pool.last()
}

/**
 * Turns a noisy per-frame note stream into note events: a note is reported once,
 * after it has been detected for [framesRequired] consecutive frames. A single
 * dropped frame doesn't break a sustained note.
 */
class NoteStabilizer(private val framesRequired: Int = 4) {
    private var current: Int? = null
    private var count = 0
    private var silentFrames = 0

    fun feed(midi: Int?): Int? {
        if (midi == null) {
            if (++silentFrames >= 2) {
                current = null
                count = 0
            }
            return null
        }
        silentFrames = 0
        if (midi != current) {
            current = midi
            count = 0
        }
        count++
        return if (count == framesRequired) midi else null
    }
}
