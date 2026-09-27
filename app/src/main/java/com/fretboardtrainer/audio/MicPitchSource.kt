package com.fretboardtrainer.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.fretboardtrainer.music.PitchReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

/** One analysis frame: input loudness (RMS of the latest hop, 0..1) and the detected pitch, if any. */
data class MicFrame(val level: Double, val pitch: PitchReading?)

/**
 * Streams pitch readings from the microphone, one per hop (~46 ms at the defaults).
 * [MicFrame.pitch] is null for frames with no clear pitch. Caller must hold RECORD_AUDIO.
 */
class MicPitchSource(
    private val sampleRate: Int = 44100,
    private val windowSize: Int = 4096,
    private val hopSize: Int = 2048,
    /** Frames quieter than this (RMS, 0..1) are treated as silence. */
    private val gateRms: Double = 0.004,
) {
    @SuppressLint("MissingPermission")
    fun frames(): Flow<MicFrame> = flow {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        check(minBytes > 0) { "Microphone does not support $sampleRate Hz input" }

        // VOICE_RECOGNITION usually skips the noise suppression / AGC that mangles music.
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, channel, encoding,
            max(minBytes, hopSize * Float.SIZE_BYTES * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("Could not open the microphone")
        }

        val detector = YinPitchDetector(sampleRate, windowSize, silenceRms = gateRms)
        val window = FloatArray(windowSize)
        val hop = FloatArray(hopSize)
        record.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < hopSize) {
                    val n = record.read(hop, read, hopSize - read, AudioRecord.READ_BLOCKING)
                    check(n >= 0) { "Microphone read failed ($n)" }
                    read += n
                }
                System.arraycopy(window, hopSize, window, 0, windowSize - hopSize)
                System.arraycopy(hop, 0, window, windowSize - hopSize, hopSize)
                val frame = MicFrame(YinPitchDetector.rms(hop), detector.detect(window)?.let(PitchReading::fromFrequency))
                emit(frame)
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.Default)
}
