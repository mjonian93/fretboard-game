package com.fretboardtrainer.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Monophonic pitch detector using the YIN algorithm
 * (de Cheveigné & Kawahara, 2002). Returns the fundamental frequency in Hz,
 * or null when the frame is too quiet or has no clear pitch.
 *
 * The default range covers a guitar in standard tuning: low E (82 Hz) up to
 * well past the 16th fret of the high E string (~660 Hz), with headroom.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val windowSize: Int,
    private val threshold: Double = 0.12,
    private val minFrequency: Double = 70.0,
    private val maxFrequency: Double = 1400.0,
    private val silenceRms: Double = 0.004,
) {
    private val half = windowSize / 2
    private val maxTau = min(half - 1, (sampleRate / minFrequency).toInt() + 1)
    private val minTau = max(2, (sampleRate / maxFrequency).toInt())
    private val diff = DoubleArray(maxTau + 1)

    init {
        require(sampleRate / minFrequency < half) {
            "windowSize $windowSize is too small to detect $minFrequency Hz at $sampleRate Hz"
        }
    }

    fun detect(samples: FloatArray): Double? {
        require(samples.size >= windowSize) { "Expected $windowSize samples, got ${samples.size}" }
        if (rms(samples, windowSize) < silenceRms) return null

        // Step 1-2: difference function.
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (i in 0 until half) {
                val d = samples[i] - samples[i + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        // Step 3: cumulative mean normalized difference.
        diff[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            runningSum += diff[tau]
            diff[tau] = if (runningSum == 0.0) 1.0 else diff[tau] * tau / runningSum
        }

        // Step 4: first dip below the threshold, followed to its local minimum.
        var tau = minTau
        var found = -1
        while (tau <= maxTau) {
            if (diff[tau] < threshold) {
                while (tau + 1 <= maxTau && diff[tau + 1] < diff[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) return null

        // Step 5: parabolic interpolation for sub-sample accuracy.
        val refined = if (found in 1 until maxTau) {
            val s0 = diff[found - 1]
            val s1 = diff[found]
            val s2 = diff[found + 1]
            val denom = s0 + s2 - 2 * s1
            if (denom != 0.0) found + (s0 - s2) / (2 * denom) else found.toDouble()
        } else {
            found.toDouble()
        }
        return sampleRate / refined
    }

    companion object {
        fun rms(samples: FloatArray, count: Int = samples.size): Double {
            var sum = 0.0
            for (i in 0 until count) sum += samples[i] * samples[i]
            return sqrt(sum / count)
        }
    }
}
