package com.fretboardtrainer.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fretboardtrainer.FretboardApp
import com.fretboardtrainer.audio.MicFrame
import com.fretboardtrainer.audio.MicPitchSource
import com.fretboardtrainer.data.AppSettings
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.PitchReading
import com.fretboardtrainer.music.STRING_COUNT
import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10

sealed interface Calibration {
    data class Quiet(val progress: Float) : Calibration
    data class Play(val progress: Float) : Calibration
    data class Done(val noiseDb: Float, val noteDb: Float, val gateDb: Float) : Calibration
    data class Failed(val message: String) : Calibration
}

data class TunerState(
    val listening: Boolean = false,
    val reading: PitchReading? = null,
    /** Smoothed deviation from [PitchReading.midi], in cents. */
    val cents: Double = 0.0,
    /** The open string closest to the heard pitch, and how far off it is in cents. */
    val nearestString: Int? = null,
    val stringCents: Double = 0.0,
    val micLevel: Float = 0f,
    val calibration: Calibration? = null,
    val error: String? = null,
)

/** Picks the calibrated gate: well above the room noise, well below the guitar. */
fun calibratedGateDb(noiseDb: Float, noteDb: Float): Float? {
    if (noteDb - noiseDb < 10f) return null
    return ((noiseDb + noteDb) / 2f).coerceIn(noiseDb + 6f, noteDb - 12f)
        .coerceIn(AppSettings.MIN_GATE_DB, AppSettings.MAX_GATE_DB)
}

/** The open string nearest to [frequency] and the deviation from it in cents. */
fun nearestString(tuning: Tuning, frequency: Double): Pair<Int, Double> {
    val midi = Notes.frequencyToMidi(frequency)
    val string = (1..STRING_COUNT).minBy { abs(midi - tuning.openMidi(it)) }
    return string to (midi - tuning.openMidi(string)) * 100.0
}

class TunerViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as FretboardApp
    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        listen(graph.appSettings.value.micGateRms) { frame -> onTunerFrame(frame) }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(listening = false, reading = null, micLevel = 0f, calibration = null) }
    }

    fun calibrate() {
        job?.cancel()
        val noise = mutableListOf<Double>()
        val note = mutableListOf<Double>()
        val quietFrames = 43 // ~2 s
        val playFrames = 65 // ~3 s
        var frames = 0
        _state.update { it.copy(calibration = Calibration.Quiet(0f), reading = null) }
        listen(gateRms = 0.0) { frame ->
            frames++
            _state.update { it.copy(micLevel = frame.level.toFloat()) }
            if (frames <= quietFrames) {
                noise += frame.level
                _state.update { it.copy(calibration = Calibration.Quiet(frames.toFloat() / quietFrames)) }
            } else {
                note += frame.level
                _state.update { it.copy(calibration = Calibration.Play((frames - quietFrames).toFloat() / playFrames)) }
            }
            if (frames == quietFrames + playFrames) {
                finishCalibration(noise, note)
                false
            } else {
                true
            }
        }
    }

    fun dismissCalibration() {
        _state.update { it.copy(calibration = null) }
        job?.cancel()
        start()
    }

    fun onMicPermissionDenied() {
        _state.update { it.copy(error = "Microphone permission is needed for the tuner.") }
    }

    private fun finishCalibration(noise: List<Double>, note: List<Double>) {
        fun db(x: Double) = (20 * log10(x.coerceAtLeast(1e-7))).toFloat()
        // Room noise: typical level while quiet. Note: its loud part (the 80th percentile).
        val noiseDb = db(noise.sorted()[noise.size / 2])
        val noteDb = db(note.sorted()[note.size * 4 / 5])
        val gate = calibratedGateDb(noiseDb, noteDb)
        _state.update {
            it.copy(
                calibration = if (gate == null) {
                    Calibration.Failed("Couldn't hear a note clearly over the background noise. Move closer to the mic and try again.")
                } else {
                    graph.appSettings.update(graph.appSettings.value.copy(micGateDb = gate))
                    Calibration.Done(noiseDb, noteDb, gate)
                },
            )
        }
    }

    private fun onTunerFrame(frame: MicFrame): Boolean {
        val reading = frame.pitch
        _state.update { s ->
            if (reading == null) {
                s.copy(micLevel = frame.level.toFloat(), reading = if (frame.level < graph.appSettings.value.micGateRms) null else s.reading)
            } else {
                // Smooth the needle while the note stays the same.
                val cents = if (s.reading?.midi == reading.midi) s.cents * 0.6 + reading.cents * 0.4 else reading.cents
                val (string, stringCents) = nearestString(graph.appSettings.value.tuning, reading.frequency)
                s.copy(reading = reading, cents = cents, nearestString = string, stringCents = stringCents, micLevel = frame.level.toFloat())
            }
        }
        return true
    }

    /** Collects mic frames until [onFrame] returns false or the job is cancelled. */
    private fun listen(gateRms: Double, onFrame: (MicFrame) -> Boolean) {
        job = viewModelScope.launch {
            _state.update { it.copy(listening = true, error = null) }
            try {
                MicPitchSource(gateRms = gateRms).frames().takeWhile { onFrame(it) }.collect {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Microphone error", listening = false) }
            }
        }
    }
}
