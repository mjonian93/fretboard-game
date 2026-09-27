package com.fretboardtrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.fretboardtrainer.music.Notes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays synthesized notes and metronome clicks without blocking the caller. */
class SoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val accentClick by lazy { Synth.click(accent = true) }
    private val beatClick by lazy { Synth.click(accent = false) }
    private val plucks = mutableMapOf<Int, ShortArray>()

    fun pluck(midi: Int) {
        scope.launch {
            val pcm = synchronized(plucks) {
                plucks.getOrPut(midi) { Synth.pluck(Notes.midiToFrequency(midi.toDouble())) }
            }
            play(pcm)
        }
    }

    fun click(accent: Boolean) {
        scope.launch { play(if (accent) accentClick else beatClick) }
    }

    private suspend fun play(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Synth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(pcm.size * 1000L / Synth.SAMPLE_RATE + 100)
        } finally {
            track.release()
        }
    }
}
