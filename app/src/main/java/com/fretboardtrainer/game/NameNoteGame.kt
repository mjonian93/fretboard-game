package com.fretboardtrainer.game

import android.app.Application
import com.fretboardtrainer.data.StatKeys
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.FretPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.STRING_COUNT
import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/** Mode 1: a fret lights up, you tap its note name. No guitar needed. */
@Serializable
data class NameNoteSettings(
    val maxFret: Int = 12,
    val strings: Set<Int> = (1..STRING_COUNT).toSet(),
    val naturalsOnly: Boolean = false,
    /** Play the note's sound when it lights up. */
    val playSound: Boolean = true,
    override val session: SessionConfig = SessionConfig(seconds = 5),
) : ModeSettings

sealed interface NameResult {
    data class Correct(val pitchClass: Int, val millis: Long) : NameResult
    data class Wrong(val chosenPitchClass: Int) : NameResult
    data object TimeUp : NameResult
}

data class NameNoteRound(val target: FretPosition? = null, val result: NameResult? = null)

fun nameNoteCandidates(settings: NameNoteSettings, tuning: Tuning): List<FretPosition> =
    settings.strings.sorted().flatMap { string ->
        (0..minOf(settings.maxFret, FRET_COUNT)).map { fret -> FretPosition(string, fret) }
    }.filter { !settings.naturalsOnly || Notes.isNatural(tuning.midiAt(it)) }

class NameNoteViewModel(app: Application) :
    GameViewModel<NameNoteSettings>(app, "mode.name", NameNoteSettings(), NameNoteSettings.serializer(), usesMic = false) {

    private val _round = MutableStateFlow(NameNoteRound())
    val round: StateFlow<NameNoteRound> = _round.asStateFlow()

    /** Taps are only observed while a round waits for an answer; taps during feedback are dropped. */
    private val answers = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private var previous: FretPosition? = null

    fun answer(pitchClass: Int) {
        answers.tryEmit(pitchClass)
    }

    override fun onStopped() {
        _round.value = NameNoteRound()
        previous = null
    }

    override suspend fun playRound(): RoundResult {
        val settings = settings.value
        val tuning = tuning
        val all = nameNoteCandidates(settings, tuning)
        // Never the same note name twice in a row.
        val previousClass = previous?.let { Notes.pitchClass(tuning.midiAt(it)) }
        val pool = all.filter { Notes.pitchClass(tuning.midiAt(it)) != previousClass }.ifEmpty { all }
        val target = random.weightedPick(pool) { graph.stats[key(tuning, it)].pickWeight }!!
        previous = target
        val targetMidi = tuning.midiAt(target)
        val targetClass = Notes.pitchClass(targetMidi)

        _round.value = NameNoteRound(target)
        if (settings.playSound) graph.sound.pluck(targetMidi)
        val started = now()
        val chosen = awaitAnswer { answers.first() }
        val millis = now() - started

        val result = when (chosen) {
            null -> NameResult.TimeUp
            targetClass -> NameResult.Correct(chosen, millis)
            else -> NameResult.Wrong(chosen)
        }
        _round.value = NameNoteRound(target, result)
        val correct = result is NameResult.Correct
        return RoundResult(key(tuning, target), Notes.fullName(targetMidi, withOctave = false), correct, correct, millis.takeIf { correct })
    }

    private fun key(tuning: Tuning, position: FretPosition) = StatKeys.fret(tuning.id, position.string, position.fret)
}
