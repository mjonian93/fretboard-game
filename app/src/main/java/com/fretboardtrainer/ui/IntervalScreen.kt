package com.fretboardtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fretboardtrainer.game.IntervalFeedback
import com.fretboardtrainer.game.IntervalRound
import com.fretboardtrainer.game.IntervalSettings
import com.fretboardtrainer.game.SessionState
import com.fretboardtrainer.game.answerPositions
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.Interval
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning

@Composable
fun IntervalContent(
    round: IntervalRound,
    session: SessionState,
    settings: IntervalSettings,
    tuning: Tuning,
    mirrored: Boolean,
    gateDb: Float,
    modifier: Modifier = Modifier,
) {
    val question = round.question
    val feedback = round.feedback
    val finished = feedback is IntervalFeedback.Correct || feedback == IntervalFeedback.TimeUp
    val rootName = question?.let { Notes.fullName(it.rootMidi, withOctave = false) }
    val answerName = question?.let { Notes.fullName(it.targetMidi, withOctave = false) }

    Column(modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val accent = when (feedback) {
                is IntervalFeedback.Correct -> TargetGreen
                IntervalFeedback.TimeUp -> MissOrange
                is IntervalFeedback.Wrong -> WrongRed
                else -> Color(0xFF3A3A3A)
            }
            NoteCard(question?.interval?.shortName, accent, Modifier.width(190.dp).fillMaxHeight()) {
                Text(
                    "${question?.interval?.longName} above $rootName",
                    color = MutedGray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
                Text(if (settings.anyOctave) "Any octave counts · ${tuning.name}" else "Play it just above the root · ${tuning.name}", color = MutedGray)
                val markers = buildList {
                    question?.let { q ->
                        add(FretMarker(q.root, Color(0xFF5DADE2), Notes.name(q.rootMidi, withOctave = false)))
                        if (finished) {
                            val color = if (feedback is IntervalFeedback.Correct) TargetGreen else MissOrange
                            q.answerPositions(tuning).forEach { add(FretMarker(it, color, Notes.name(q.targetMidi, withOctave = false))) }
                        }
                    }
                }
                Fretboard(FRET_COUNT, 0..settings.maxFret, markers, Modifier.weight(1f).fillMaxWidth(), mirrored)
            }
        }
        val (message, color) = when (feedback) {
            null -> "Press Start. The blue note is the root: play the interval asked for above it." to Color.White
            IntervalFeedback.Listening -> "Play the ${question?.interval?.longName} above $rootName!" to Color.White
            is IntervalFeedback.Wrong -> "That was ${Notes.name(feedback.playedMidi)}. Try again!" to WrongRed
            is IntervalFeedback.Correct -> "Correct! $answerName is the ${question?.interval?.longName} above $rootName (${formatSeconds(feedback.millis)})" to TargetGreen
            IntervalFeedback.TimeUp -> "Time's up! It was $answerName (${question?.let { Notes.name(it.targetMidi) }})." to MissOrange
        }
        ListeningStatus(message, color, session, gateDb)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalSettingsContent(settings: IntervalSettings, onChange: (IntervalSettings) -> Unit) {
    Text("Intervals", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { onChange(settings.copy(intervals = Interval.CHORD_TONES)) }, label = { Text("Chord tones") })
        AssistChip(onClick = { onChange(settings.copy(intervals = Interval.entries.toSet())) }, label = { Text("All") })
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (interval in Interval.entries) {
            val selected = interval in settings.intervals
            FilterChip(
                selected = selected,
                onClick = {
                    val set = if (selected) settings.intervals - interval else settings.intervals + interval
                    if (set.isNotEmpty()) onChange(settings.copy(intervals = set))
                },
                label = { Text(interval.shortName) },
            )
        }
    }
    Text(
        Interval.entries.filter { it in settings.intervals }.joinToString(", ") { it.longName },
        style = MaterialTheme.typography.bodySmall, color = MutedGray,
    )
    SettingSlider("Root frets: open to ${settings.maxFret}", settings.maxFret, 3..FRET_COUNT) { onChange(settings.copy(maxFret = it)) }
    SettingSwitch("Any octave", "Accept the answer note in any octave, not only just above the root", settings.anyOctave) {
        onChange(settings.copy(anyOctave = it))
    }
    SessionSettings(settings.session, { onChange(settings.copy(session = it)) })
}
