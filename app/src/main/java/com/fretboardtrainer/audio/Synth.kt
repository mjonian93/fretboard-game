package com.fretboardtrainer.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Sound generation, as 16-bit mono PCM. */
object Synth {
    const val SAMPLE_RATE = 44100

    /** Plucked string (Karplus-Strong): a noise burst circulating through a damped delay line. */
    fun pluck(frequency: Double, seconds: Double = 1.6, random: Random = Random(0)): ShortArray {
        val period = (SAMPLE_RATE / frequency).roundToInt().coerceAtLeast(2)
        val line = DoubleArray(period) { random.nextDouble() * 2 - 1 }
        val out = ShortArray((SAMPLE_RATE * seconds).toInt())
        for (i in out.indices) {
            val j = i % period
            val sample = line[j]
            line[j] = 0.996 * 0.5 * (sample + line[(j + 1) % period])
            val fadeOut = ((out.size - i) / (SAMPLE_RATE * 0.05)).coerceAtMost(1.0)
            out[i] = (sample * 0.5 * fadeOut * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Short woodblock-like tick for the metronome; accented beats are higher. */
    fun click(accent: Boolean): ShortArray {
        val frequency = if (accent) 2000.0 else 1400.0
        val out = ShortArray((SAMPLE_RATE * 0.03).toInt())
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            out[i] = (sin(2 * PI * frequency * t) * exp(-t * 180) * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
