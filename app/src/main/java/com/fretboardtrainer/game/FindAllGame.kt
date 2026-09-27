package com.fretboardtrainer.game

import android.app.Application
import com.fretboardtrainer.data.StatKeys
import com.fretboardtrainer.music.FRET_COUNT
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

/**
 * Mode 3: play every octave of a note inside a hand position or across the neck.
 * The mic can't tell strings apart, so each distinct pitch counts once.
 */
@Serializable
data class FindAllSettings(
    /** Hand position to practise in; 0 = the whole neck up to [maxFret]. */
    val position: Int = 0,
    val maxFret: Int = 12,
    val naturalsOnly: Boolean = false,
    /** Timing is per octave to find. */
    override val session: SessionConfig = SessionConfig(seconds = 6, length = 10),
) : ModeSettings {
    fun region(tuning: Tuning): FretRegion =
        if (position == 0) FretRegion(0..minOf(maxFret, FRET_COUNT), tuning) else HandPosition(position).region(tuning)
}

sealed interface FindAllFeedback {
    data object Listening : FindAllFeedback
    data class Found(val midi: Int) : FindAllFeedback
    data class AlreadyFound(val midi: Int) : FindAllFeedback
    data class Wrong(val playedMidi: Int, val outside: Boolean) : FindAllFeedback
    data class Done(val millis: Long) : FindAllFeedback
    data object TimeUp : FindAllFeedback
}

data class FindAllRound(
    val pitchClass: Int? = null,
    /** All pitches to find, lowest first. */
    val octaves: List<Int> = emptyList(),
    val found: Set<Int> = emptySet(),
    val feedback: FindAllFeedback? = null,
)

class FindAllViewModel(app: Application) :
    GameViewModel<FindAllSettings>(app, "mode.findall", FindAllSettings(), FindAllSettings.serializer(), usesMic = true) {

    private val _round = MutableStateFlow(FindAllRound())
    val round: StateFlow<FindAllRound> = _round.asStateFlow()
    private var previousClass: Int? = null

    override fun onStopped() {
        _round.value = FindAllRound()
        previousClass = null
    }

    override suspend fun playRound(): RoundResult {
        val settings = settings.value
        val region = settings.region(tuning)
        val classes = (0 until 12).filter { (!settings.naturalsOnly || Notes.isNatural(it)) && region.octavesOf(it).isNotEmpty() }
        val pool = classes.filter { it != previousClass }.ifEmpty { classes }
        val pc = random.weightedPick(pool) { graph.stats[StatKeys.note(it)].pickWeight }!!
        previousClass = pc
        val octaves = region.octavesOf(pc)
        _round.value = FindAllRound(pc, octaves, emptySet(), FindAllFeedback.Listening)

        var mistake = false
        val started = now()
        val done = awaitAnswer(notes = octaves.size) {
            playedNotes.first { played ->
                val found = _round.value.found
                val feedback = when {
                    played in found -> FindAllFeedback.AlreadyFound(played)
                    played in octaves -> FindAllFeedback.Found(played)
                    else -> {
                        mistake = true
                        breakStreak()
                        FindAllFeedback.Wrong(played, outside = Notes.pitchClass(played) == pc)
                    }
                }
                val newFound = if (feedback is FindAllFeedback.Found) found + played else found
                _round.update { it.copy(found = newFound, feedback = feedback) }
                newFound.size == octaves.size
            }
        }
        val millis = now() - started
        _round.update { it.copy(feedback = if (done != null) FindAllFeedback.Done(millis) else FindAllFeedback.TimeUp) }
        return RoundResult(
            StatKeys.note(pc), Notes.fullName(pc, withOctave = false),
            correct = done != null, clean = done != null && !mistake, millis = millis.takeIf { done != null },
        )
    }

    override fun pauseMillis(correct: Boolean): Long =
        if (settings.value.session.timing == TimingMode.TEMPO) super.pauseMillis(correct) else if (correct) 1500 else 3500
}
