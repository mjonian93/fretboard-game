package com.fretboardtrainer.game

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

data class Score(val hits: Int = 0, val rounds: Int = 0, val streak: Int = 0, val best: Int = 0) {
    fun hit(): Score = copy(hits = hits + 1, rounds = rounds + 1, streak = streak + 1, best = maxOf(best, streak + 1))
    fun miss(): Score = copy(rounds = rounds + 1, streak = 0)
    fun breakStreak(): Score = copy(streak = 0)
}

/**
 * Remembers which targets you get wrong so they come up more often.
 * Each miss adds weight; each clean hit takes one back.
 */
class MissTracker<K>(private val random: Random = Random.Default) {
    private val misses = mutableMapOf<K, Int>()

    fun pick(pool: List<K>): K? {
        if (pool.isEmpty()) return null
        val weights = pool.map { 1 + 2 * (misses[it] ?: 0) }
        var pick = random.nextInt(weights.sum())
        for ((i, weight) in weights.withIndex()) {
            pick -= weight
            if (pick < 0) return pool[i]
        }
        return pool.last()
    }

    fun recordMiss(key: K) {
        misses[key] = (misses[key] ?: 0) + 1
    }

    fun recordHit(key: K) {
        val count = misses[key] ?: return
        if (count <= 1) misses.remove(key) else misses[key] = count - 1
    }
}

/**
 * Runs [block] with a deadline, reporting the remaining time (1 → 0) every 50 ms.
 * Returns null if time ran out.
 */
suspend fun <T> timedRound(totalMs: Long, onTick: (Float) -> Unit, block: suspend () -> T): T? = coroutineScope {
    val start = System.nanoTime()
    val ticker = launch {
        while (true) {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000f
            onTick((1f - elapsedMs / totalMs).coerceAtLeast(0f))
            delay(50)
        }
    }
    try {
        withTimeoutOrNull(totalMs) { block() }
    } finally {
        ticker.cancel()
    }
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
