package com.fretboardtrainer.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fretboardtrainer.FretboardApp
import com.fretboardtrainer.audio.MicPitchSource
import com.fretboardtrainer.music.PitchReading
import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlin.random.Random

data class SessionSummary(
    val rounds: Int,
    val correct: Int,
    val averageMillis: Long?,
    val bestStreak: Int,
    /** Items missed most this session, worst first. */
    val weakest: List<String>,
    val finalBpm: Int?,
    /** False when stopped before the planned number of notes. */
    val completed: Boolean,
)

data class SessionState(
    val running: Boolean = false,
    val score: Score = Score(),
    /** Remaining time for the current note, 1 → 0; null when untimed. */
    val timeLeft: Float? = null,
    val round: Int = 0,
    val bpm: Int = 60,
    val summary: SessionSummary? = null,
    val error: String? = null,
    val micLevel: Float = 0f,
    val heard: PitchReading? = null,
)

/**
 * One finished question. [correct]: answered in time; [clean]: without wrong attempts
 * (only clean answers count as correct in the lifetime stats).
 */
data class RoundResult(val statKey: String, val label: String, val correct: Boolean, val clean: Boolean, val millis: Long?)

/**
 * Shared engine for all practice modes: settings persistence, session length, timing
 * (seconds or tempo with metronome and auto speed-up), score, lifetime stats and,
 * for modes that listen, the microphone.
 */
abstract class GameViewModel<S : ModeSettings>(
    app: Application,
    private val settingsKey: String,
    defaults: S,
    private val serializer: KSerializer<S>,
    private val usesMic: Boolean,
) : AndroidViewModel(app) {
    protected val graph = app as FretboardApp
    protected val random = Random.Default

    private val _settings = MutableStateFlow(graph.prefs.load(settingsKey, serializer) ?: defaults)
    val settings: StateFlow<S> = _settings.asStateFlow()

    private val _session = MutableStateFlow(SessionState(bpm = _settings.value.session.bpm))
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** Steady notes from the mic; only observed while a round waits for an answer. */
    private val _playedNotes = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    protected val playedNotes: SharedFlow<Int> = _playedNotes

    protected val tuning: Tuning get() = graph.appSettings.value.tuning

    private var micJob: Job? = null
    private var gameJob: Job? = null
    private val results = mutableListOf<RoundResult>()

    /** Ask one question and wait for the answer; call [awaitAnswer] for the timed part. */
    protected abstract suspend fun playRound(): RoundResult

    /** Clear per-round state when a session stops. */
    protected open fun onStopped() {}

    fun updateSettings(settings: S) {
        graph.prefs.save(settingsKey, serializer, settings)
        _settings.value = settings
        if (!_session.value.running) _session.update { it.copy(bpm = settings.session.bpm) }
    }

    fun start() {
        if (gameJob?.isActive == true) return
        results.clear()
        val config = settings.value.session
        _session.value = SessionState(running = true, bpm = config.bpm)
        if (usesMic) startMic()
        gameJob = viewModelScope.launch {
            var round = 0
            while (config.length == 0 || round < config.length) {
                round++
                _session.update { it.copy(round = round) }
                val result = playRound()
                record(result)
                delay(pauseMillis(result.correct))
            }
            stop(showSummary = true)
        }
    }

    /** Stops the session; [showSummary] shows the results so far (if any). */
    fun stop(showSummary: Boolean = false) {
        gameJob?.cancel()
        micJob?.cancel()
        val summary = if (showSummary && results.isNotEmpty()) summarize() else null
        _session.update {
            it.copy(running = false, timeLeft = null, heard = null, micLevel = 0f, summary = summary ?: it.summary)
        }
        onStopped()
    }

    fun dismissSummary() {
        _session.update { it.copy(summary = null) }
    }

    fun changeBpm(delta: Int) {
        _session.update {
            it.copy(bpm = (it.bpm + delta).coerceIn(SessionConfig.MIN_BPM, SessionConfig.MAX_BPM))
        }
    }

    fun onMicPermissionDenied() {
        _session.update { it.copy(error = "Microphone permission is needed to hear your guitar.") }
    }

    protected fun breakStreak() {
        _session.update { it.copy(score = it.score.breakStreak()) }
    }

    protected fun now(): Long = System.nanoTime() / 1_000_000

    /**
     * Runs [block] within the time allowed for [notes] notes, updating the countdown
     * and clicking the metronome in tempo mode. Returns null if time ran out.
     */
    protected suspend fun <T> awaitAnswer(notes: Int = 1, block: suspend () -> T): T? {
        val config = settings.value.session
        val bpm = _session.value.bpm
        val total = config.noteMillis(bpm, notes)
        if (total == null) {
            _session.update { it.copy(timeLeft = null) }
            return block()
        }
        val beatMillis = if (config.timing == TimingMode.TEMPO && config.metronome) 60_000L / bpm else null
        return coroutineScope {
            val start = now()
            val ticker = launch {
                var lastBeat = -1L
                while (true) {
                    val elapsed = now() - start
                    _session.update { it.copy(timeLeft = (1f - elapsed.toFloat() / total).coerceAtLeast(0f)) }
                    var wait = 50L
                    if (beatMillis != null) {
                        val beat = elapsed / beatMillis
                        if (beat != lastBeat && elapsed < total) {
                            lastBeat = beat
                            graph.sound.click(accent = beat % config.beatsPerNote == 0L)
                        }
                        wait = minOf(wait, beatMillis - elapsed % beatMillis)
                    }
                    delay(wait.coerceAtLeast(5))
                }
            }
            try {
                withTimeoutOrNull(total) { block() }
            } finally {
                ticker.cancel()
            }
        }
    }

    /** Pause to show feedback: in tempo mode it's counted in beats so the groove continues. */
    protected open fun pauseMillis(correct: Boolean): Long {
        val config = settings.value.session
        return if (config.timing == TimingMode.TEMPO) {
            (60_000L / _session.value.bpm) * (if (correct) 1 else 2)
        } else {
            if (correct) 900 else 2500
        }
    }

    private fun record(result: RoundResult) {
        results += result
        graph.stats.record(result.statKey, result.clean, result.millis)
        val config = settings.value.session
        _session.update {
            val score = if (result.correct) it.score.hit() else it.score.miss()
            val speedUp = result.correct && config.timing == TimingMode.TEMPO && config.autoSpeedUp &&
                score.streak > 0 && score.streak % SessionConfig.SPEED_UP_EVERY == 0
            val bpm = if (speedUp) (it.bpm + SessionConfig.SPEED_UP_BPM).coerceAtMost(SessionConfig.MAX_BPM) else it.bpm
            it.copy(score = score, bpm = bpm)
        }
    }

    private fun summarize(): SessionSummary {
        val state = _session.value
        val times = results.filter { it.correct }.mapNotNull { it.millis }
        return SessionSummary(
            rounds = results.size,
            correct = results.count { it.correct },
            averageMillis = if (times.isEmpty()) null else times.average().toLong(),
            bestStreak = state.score.best,
            weakest = results.filter { !it.clean }.groupingBy { it.label }.eachCount()
                .entries.sortedByDescending { it.value }.take(3).map { it.key },
            finalBpm = if (settings.value.session.timing == TimingMode.TEMPO) state.bpm else null,
            completed = settings.value.session.length in 1..results.size,
        )
    }

    private fun startMic() {
        micJob = viewModelScope.launch {
            val stabilizer = NoteStabilizer()
            try {
                MicPitchSource(gateRms = graph.appSettings.value.micGateRms).frames().collect { frame ->
                    _session.update { it.copy(heard = frame.pitch, micLevel = frame.level.toFloat()) }
                    stabilizer.feed(frame.pitch?.midi)?.let { _playedNotes.emit(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stop()
                _session.update { it.copy(error = e.message ?: "Microphone error") }
            }
        }
    }
}
