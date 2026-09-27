package com.fretboardtrainer.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fretboardtrainer.music.FretPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Mode 1: a fret lights up, you tap its note name. No guitar needed. */
data class NameNoteSettings(
    val maxFret: Int = 12,
    val strings: Set<Int> = (1..Tuning.STRING_COUNT).toSet(),
    val naturalsOnly: Boolean = true,
    val secondsPerNote: Int = 5,
)

sealed interface NameResult {
    data class Correct(val pitchClass: Int) : NameResult
    data class Wrong(val chosenPitchClass: Int) : NameResult
    data object TimeUp : NameResult
}

data class NameNoteState(
    val settings: NameNoteSettings,
    val running: Boolean = false,
    val target: FretPosition? = null,
    val result: NameResult? = null,
    val timeLeft: Float = 1f,
    val score: Score = Score(),
)

/** Consecutive targets never share a note name, so the answer can't be "same as last time". */
class FretTargetPicker(random: Random = Random.Default) {
    private val tracker = MissTracker<FretPosition>(random)

    fun candidates(settings: NameNoteSettings): List<FretPosition> =
        settings.strings.sorted().flatMap { string ->
            (0..settings.maxFret).map { fret -> FretPosition(string, fret) }
        }.filter { !settings.naturalsOnly || Notes.isNatural(Tuning.midiAt(it)) }

    fun next(settings: NameNoteSettings, previous: FretPosition?): FretPosition? {
        val all = candidates(settings)
        val previousClass = previous?.let { Notes.pitchClass(Tuning.midiAt(it)) }
        return tracker.pick(all.filter { Notes.pitchClass(Tuning.midiAt(it)) != previousClass }.ifEmpty { all })
    }

    fun recordMiss(position: FretPosition) = tracker.recordMiss(position)
    fun recordHit(position: FretPosition) = tracker.recordHit(position)
}

class NameNoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val _state = MutableStateFlow(NameNoteState(settings = store.loadNameNote()))
    val state: StateFlow<NameNoteState> = _state.asStateFlow()

    /** Taps are only observed while a round waits for an answer; taps during feedback are dropped. */
    private val answers = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val picker = FretTargetPicker()
    private var gameJob: Job? = null

    fun start() {
        if (gameJob?.isActive == true) return
        _state.update { it.copy(running = true, score = Score(), result = null) }
        gameJob = viewModelScope.launch { runRounds() }
    }

    fun stop() {
        gameJob?.cancel()
        _state.update { it.copy(running = false, target = null, result = null, timeLeft = 1f) }
    }

    fun answer(pitchClass: Int) {
        answers.tryEmit(pitchClass)
    }

    fun updateSettings(settings: NameNoteSettings) {
        store.saveNameNote(settings)
        _state.update { it.copy(settings = settings) }
    }

    private suspend fun runRounds() {
        var previous: FretPosition? = null
        while (true) {
            val settings = _state.value.settings
            val target = picker.next(settings, previous) ?: return
            previous = target
            val targetClass = Notes.pitchClass(Tuning.midiAt(target))
            _state.update { it.copy(target = target, result = null, timeLeft = 1f) }

            val chosen = timedRound(settings.secondsPerNote * 1000L, { t -> _state.update { it.copy(timeLeft = t) } }) {
                answers.first()
            }
            val result = when (chosen) {
                null -> NameResult.TimeUp
                targetClass -> NameResult.Correct(chosen)
                else -> NameResult.Wrong(chosen)
            }
            if (result is NameResult.Correct) picker.recordHit(target) else picker.recordMiss(target)
            _state.update {
                it.copy(
                    result = result,
                    score = if (result is NameResult.Correct) it.score.hit() else it.score.miss(),
                    timeLeft = if (result is NameResult.TimeUp) 0f else it.timeLeft,
                )
            }
            delay(if (result is NameResult.Correct) 700 else 1800)
        }
    }
}
