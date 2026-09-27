package com.fretboardtrainer.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fretboardtrainer.audio.MicPitchSource
import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.PitchReading
import kotlinx.coroutines.CancellationException
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

/** Mode 2: a note name is shown, you find and play it within a hand position. */
data class FindNoteSettings(
    val position: Int = 2,
    val naturalsOnly: Boolean = true,
    /** When true you must play one specific octave, shown by the dots under the name. */
    val exactOctave: Boolean = false,
    val secondsPerNote: Int = 8,
)

/**
 * The note to find. [midi] is null when any octave inside the position counts.
 * [octaveIndex] (0 = lowest) of [octaveCount] says which occurrence is wanted.
 */
data class FindTarget(val pitchClass: Int, val midi: Int?, val octaveIndex: Int, val octaveCount: Int)

sealed interface Attempt {
    data object Correct : Attempt
    data object WrongOctave : Attempt
    data object OutsidePosition : Attempt
    data object WrongNote : Attempt
}

fun FindTarget.judge(playedMidi: Int, position: HandPosition): Attempt = when {
    Notes.pitchClass(playedMidi) != pitchClass -> Attempt.WrongNote
    playedMidi !in position.octavesOf(pitchClass) -> Attempt.OutsidePosition
    midi != null && playedMidi != midi -> Attempt.WrongOctave
    else -> Attempt.Correct
}

sealed interface FindFeedback {
    data object Listening : FindFeedback
    data class Wrong(val playedMidi: Int, val attempt: Attempt) : FindFeedback
    data class Correct(val playedMidi: Int) : FindFeedback
    data object TimeUp : FindFeedback
}

data class FindNoteState(
    val settings: FindNoteSettings,
    val running: Boolean = false,
    val target: FindTarget? = null,
    val feedback: FindFeedback? = null,
    val timeLeft: Float = 1f,
    val score: Score = Score(),
    val heard: PitchReading? = null,
    /** Microphone loudness, 0..1 (RMS). */
    val micLevel: Float = 0f,
    val error: String? = null,
)

/** Consecutive targets never share a note name, so a still-ringing string can't answer the next one. */
class FindTargetPicker(random: Random = Random.Default) {
    private val tracker = MissTracker<FindTarget>(random)

    fun candidates(settings: FindNoteSettings): List<FindTarget> {
        val position = HandPosition(settings.position)
        return (0 until 12).filter { !settings.naturalsOnly || Notes.isNatural(it) }.flatMap { pc ->
            val octaves = position.octavesOf(pc)
            when {
                octaves.isEmpty() -> emptyList()
                settings.exactOctave -> octaves.mapIndexed { i, midi -> FindTarget(pc, midi, i, octaves.size) }
                else -> listOf(FindTarget(pc, null, -1, octaves.size))
            }
        }
    }

    fun next(settings: FindNoteSettings, previous: FindTarget?): FindTarget? {
        val all = candidates(settings)
        return tracker.pick(all.filter { it.pitchClass != previous?.pitchClass }.ifEmpty { all })
    }

    fun recordMiss(target: FindTarget) = tracker.recordMiss(target)
    fun recordHit(target: FindTarget) = tracker.recordHit(target)
}

class FindNoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val _state = MutableStateFlow(FindNoteState(settings = store.loadFindNote()))
    val state: StateFlow<FindNoteState> = _state.asStateFlow()

    /** Notes held steadily; only observed while a round waits for an answer. */
    private val playedNotes = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val picker = FindTargetPicker()
    private var micJob: Job? = null
    private var gameJob: Job? = null

    fun start() {
        if (gameJob?.isActive == true) return
        _state.update { it.copy(running = true, score = Score(), error = null, feedback = null) }
        micJob = viewModelScope.launch {
            val stabilizer = NoteStabilizer()
            try {
                MicPitchSource().frames().collect { frame ->
                    _state.update { it.copy(heard = frame.pitch, micLevel = frame.level.toFloat()) }
                    stabilizer.feed(frame.pitch?.midi)?.let { playedNotes.emit(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stop()
                _state.update { it.copy(error = e.message ?: "Microphone error") }
            }
        }
        gameJob = viewModelScope.launch { runRounds() }
    }

    fun stop() {
        gameJob?.cancel()
        micJob?.cancel()
        _state.update { it.copy(running = false, target = null, feedback = null, timeLeft = 1f, heard = null, micLevel = 0f) }
    }

    fun updateSettings(settings: FindNoteSettings) {
        store.saveFindNote(settings)
        _state.update { it.copy(settings = settings) }
    }

    fun onMicPermissionDenied() {
        _state.update { it.copy(error = "Microphone permission is needed to hear your guitar.") }
    }

    private suspend fun runRounds() {
        var previous: FindTarget? = null
        while (true) {
            val settings = _state.value.settings
            val position = HandPosition(settings.position)
            val target = picker.next(settings, previous) ?: return
            previous = target
            _state.update { it.copy(target = target, feedback = FindFeedback.Listening, timeLeft = 1f) }

            var mistake = false
            val hit = timedRound(settings.secondsPerNote * 1000L, { t -> _state.update { it.copy(timeLeft = t) } }) {
                playedNotes.first { played ->
                    val attempt = target.judge(played, position)
                    if (attempt != Attempt.Correct) {
                        mistake = true
                        _state.update { it.copy(feedback = FindFeedback.Wrong(played, attempt), score = it.score.breakStreak()) }
                    }
                    attempt == Attempt.Correct
                }
            }

            if (hit != null) {
                if (mistake) picker.recordMiss(target) else picker.recordHit(target)
                _state.update { it.copy(feedback = FindFeedback.Correct(hit), score = it.score.hit()) }
                delay(1500)
            } else {
                picker.recordMiss(target)
                _state.update { it.copy(feedback = FindFeedback.TimeUp, score = it.score.miss(), timeLeft = 0f) }
                delay(3000)
            }
        }
    }
}
