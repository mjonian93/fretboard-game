package com.fretboardtrainer.game

import android.app.Application
import com.fretboardtrainer.data.StatKeys
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.FretPosition
import com.fretboardtrainer.music.FretRegion
import com.fretboardtrainer.music.Interval
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.STRING_COUNT
import com.fretboardtrainer.music.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** Mode 4: a root note lights up; play the requested interval above it. */
@Serializable
data class IntervalSettings(
    val intervals: Set<Interval> = Interval.CHORD_TONES,
    val maxFret: Int = 12,
    /** Accept the interval's note in any octave instead of exactly above the root. */
    val anyOctave: Boolean = false,
    override val session: SessionConfig = SessionConfig(seconds = 8),
) : ModeSettings

data class IntervalQuestion(val root: FretPosition, val rootMidi: Int, val interval: Interval) {
    val targetMidi: Int get() = rootMidi + interval.semitones
}

fun IntervalQuestion.accepts(playedMidi: Int, anyOctave: Boolean): Boolean =
    if (anyOctave) Notes.pitchClass(playedMidi) == Notes.pitchClass(targetMidi) else playedMidi == targetMidi

/** Where the answer can be played near the root: within 4 frets of it, exact pitch. */
fun IntervalQuestion.answerPositions(tuning: Tuning): List<FretPosition> =
    FretRegion(maxOf(0, root.fret - 4)..minOf(FRET_COUNT, root.fret + 4), tuning).positionsOfMidi(targetMidi)

sealed interface IntervalFeedback {
    data object Listening : IntervalFeedback
    data class Wrong(val playedMidi: Int) : IntervalFeedback
    data class Correct(val playedMidi: Int, val millis: Long) : IntervalFeedback
    data object TimeUp : IntervalFeedback
}

data class IntervalRound(val question: IntervalQuestion? = null, val feedback: IntervalFeedback? = null)

class IntervalViewModel(app: Application) :
    GameViewModel<IntervalSettings>(app, "mode.interval", IntervalSettings(), IntervalSettings.serializer(), usesMic = true) {

    private val _round = MutableStateFlow(IntervalRound())
    val round: StateFlow<IntervalRound> = _round.asStateFlow()
    private var previousClass: Int? = null

    override fun onStopped() {
        _round.value = IntervalRound()
        previousClass = null
    }

    override suspend fun playRound(): RoundResult {
        val settings = settings.value
        val tuning = tuning
        val interval = random.weightedPick(settings.intervals.ifEmpty { Interval.CHORD_TONES }.toList()) {
            graph.stats[StatKeys.interval(it.semitones)].pickWeight
        }!!
        // Roots on the lower strings leave room to play the interval above; skip ones whose answer
        // would repeat the previous answer's note name.
        val roots = (2..STRING_COUNT).flatMap { s -> (0..minOf(settings.maxFret, FRET_COUNT)).map { FretPosition(s, it) } }
        val pool = roots.filter { Notes.pitchClass(tuning.midiAt(it) + interval.semitones) != previousClass }
        val root = pool.random(random)
        val question = IntervalQuestion(root, tuning.midiAt(root), interval)
        previousClass = Notes.pitchClass(question.targetMidi)
        _round.value = IntervalRound(question, IntervalFeedback.Listening)

        var mistake = false
        val started = now()
        val hit = awaitAnswer {
            playedNotes.first { played ->
                val ok = question.accepts(played, settings.anyOctave)
                // The root itself ringing isn't a mistake.
                if (!ok && played != question.rootMidi) {
                    mistake = true
                    breakStreak()
                    _round.update { it.copy(feedback = IntervalFeedback.Wrong(played)) }
                }
                ok
            }
        }
        val millis = now() - started
        _round.update {
            it.copy(feedback = if (hit != null) IntervalFeedback.Correct(hit, millis) else IntervalFeedback.TimeUp)
        }
        return RoundResult(
            StatKeys.interval(interval.semitones), interval.longName,
            correct = hit != null, clean = hit != null && !mistake, millis = millis.takeIf { hit != null },
        )
    }

    override fun pauseMillis(correct: Boolean): Long =
        if (settings.value.session.timing == TimingMode.TEMPO) super.pauseMillis(correct) else if (correct) 1500 else 3000
}
