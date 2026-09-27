package com.fretboardtrainer.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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

        val detector = YinPitchDetector(sampleRate, windowSize)
        val window = FloatArray(windowSize)
        val hop = FloatArray(hopSize)
        record.startRecording()
        Log.d(TAG, "Recording at ${record.sampleRate} Hz, source=${record.audioSource}")
        var frameCount = 0
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
                if (++frameCount % 20 == 0) Log.d(TAG, "level=%.5f pitch=%s".format(frame.level, frame.pitch))
                emit(frame)
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.Default)
}

private const val TAG = "MicPitchSource"
