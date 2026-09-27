package com.fretboardtrainer.game

import android.app.Application
import com.fretboardtrainer.data.StatKeys
import com.fretboardtrainer.music.FretRegion
import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** Mode 2: a note name is shown, you find and play it within a hand position. */
@Serializable
data class FindNoteSettings(
    val position: Int = 2,
    val naturalsOnly: Boolean = false,
    /** When true you must play one specific octave, shown by the dots under the name. */
    val exactOctave: Boolean = false,
    override val session: SessionConfig = SessionConfig(seconds = 8),
) : ModeSettings

/**
 * The note to find. [midi] is null when any octave inside the position counts.
 * [octaveIndex] (0 = lowest) of [octaveCount] says which occurrence is wanted.
 */
data class FindTarget(val pitchClass: Int, val midi: Int?, val octaveIndex: Int, val octaveCount: Int)

enum class Attempt { CORRECT, WRONG_OCTAVE, OUTSIDE_REGION, WRONG_NOTE }

fun FindTarget.judge(playedMidi: Int, region: FretRegion): Attempt = when {
    Notes.pitchClass(playedMidi) != pitchClass -> Attempt.WRONG_NOTE
    playedMidi !in region.octavesOf(pitchClass) -> Attempt.OUTSIDE_REGION
    midi != null && playedMidi != midi -> Attempt.WRONG_OCTAVE
    else -> Attempt.CORRECT
}

fun findTargets(settings: FindNoteSettings, tuning: Tuning): List<FindTarget> {
    val region = HandPosition(settings.position).region(tuning)
    return (0 until 12).filter { !settings.naturalsOnly || Notes.isNatural(it) }.flatMap { pc ->
        val octaves = region.octavesOf(pc)
        when {
            octaves.isEmpty() -> emptyList()
            settings.exactOctave -> octaves.mapIndexed { i, midi -> FindTarget(pc, midi, i, octaves.size) }
            else -> listOf(FindTarget(pc, null, -1, octaves.size))
        }
    }
}

sealed interface FindFeedback {
    data object Listening : FindFeedback
    data class Wrong(val playedMidi: Int, val attempt: Attempt) : FindFeedback
    data class Correct(val playedMidi: Int, val millis: Long) : FindFeedback
    data object TimeUp : FindFeedback
}

data class FindNoteRound(val target: FindTarget? = null, val feedback: FindFeedback? = null)

class FindNoteViewModel(app: Application) :
    GameViewModel<FindNoteSettings>(app, "mode.find", FindNoteSettings(), FindNoteSettings.serializer(), usesMic = true) {

    private val _round = MutableStateFlow(FindNoteRound())
    val round: StateFlow<FindNoteRound> = _round.asStateFlow()
    private var previous: FindTarget? = null

    override fun onStopped() {
        _round.value = FindNoteRound()
        previous = null
    }

    override suspend fun playRound(): RoundResult {
        val settings = settings.value
        val region = HandPosition(settings.position).region(tuning)
        val all = findTargets(settings, tuning)
        // Never the same note name twice in a row, so a still-ringing string can't answer.
        val pool = all.filter { it.pitchClass != previous?.pitchClass }.ifEmpty { all }
        val target = random.weightedPick(pool) { graph.stats[StatKeys.note(it.pitchClass)].pickWeight }!!
        previous = target
        _round.value = FindNoteRound(target, FindFeedback.Listening)

        var mistake = false
        val started = now()
        val hit = awaitAnswer {
            playedNotes.first { played ->
                val attempt = target.judge(played, region)
                if (attempt != Attempt.CORRECT) {
                    mistake = true
                    breakStreak()
                    _round.update { it.copy(feedback = FindFeedback.Wrong(played, attempt)) }
                }
                attempt == Attempt.CORRECT
            }
        }
        val millis = now() - started
        _round.update { it.copy(feedback = if (hit != null) FindFeedback.Correct(hit, millis) else FindFeedback.TimeUp) }
        val label = Notes.fullName(target.pitchClass, withOctave = false)
        return RoundResult(StatKeys.note(target.pitchClass), label, hit != null, hit != null && !mistake, millis.takeIf { hit != null })
    }

    override fun pauseMillis(correct: Boolean): Long =
        if (settings.value.session.timing == TimingMode.TEMPO) super.pauseMillis(correct) else if (correct) 1500 else 3000
}
