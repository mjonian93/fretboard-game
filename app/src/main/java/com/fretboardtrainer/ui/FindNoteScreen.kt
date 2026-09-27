package com.fretboardtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fretboardtrainer.game.Attempt
import com.fretboardtrainer.game.FindFeedback
import com.fretboardtrainer.game.FindNoteRound
import com.fretboardtrainer.game.FindNoteSettings
import com.fretboardtrainer.game.FindTarget
import com.fretboardtrainer.game.SessionState
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.FretRegion
import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning

private val OCTAVE_WORDS = mapOf(
    1 to listOf("the only one"),
    2 to listOf("the lower one", "the higher one"),
    3 to listOf("the lowest one", "the middle one", "the highest one"),
)

private fun FindTarget.octaveWord(): String? =
    if (midi == null) null else OCTAVE_WORDS[octaveCount]?.getOrNull(octaveIndex) ?: "#${octaveIndex + 1} of $octaveCount"

@Composable
fun FindNoteContent(
    round: FindNoteRound,
    session: SessionState,
    settings: FindNoteSettings,
    tuning: Tuning,
    mirrored: Boolean,
    gateDb: Float,
    modifier: Modifier = Modifier,
) {
    val position = HandPosition(settings.position)
    val region = position.region(tuning)
    val target = round.target
    val feedback = round.feedback

    Column(modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val accent = when (feedback) {
                is FindFeedback.Correct -> TargetGreen
                FindFeedback.TimeUp -> MissOrange
                is FindFeedback.Wrong -> WrongRed
                else -> Color(0xFF3A3A3A)
            }
            NoteCard(
                name = target?.let { Notes.fullName(it.pitchClass, withOctave = false) },
                accent = accent,
                modifier = Modifier.width(190.dp).fillMaxHeight(),
            ) {
                if (target?.midi != null) {
                    OctaveDots(target.octaveCount, filled = setOf(target.octaveIndex))
                    Text(target.octaveWord() ?: "", color = MutedGray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                } else if (target != null) {
                    Text("any octave", color = MutedGray, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
                Text(
                    "Position ${position.index}: index finger on fret ${position.index} " +
                        "(frets ${position.frets.first}–${position.frets.last}) · ${tuning.name}",
                    color = MutedGray,
                )
                Fretboard(FRET_COUNT, position.frets, revealMarkers(target, feedback, region), Modifier.weight(1f).fillMaxWidth(), mirrored)
            }
        }
        val (message, color) = message(target, feedback)
        ListeningStatus(message, color, session, gateDb)
    }
}

/** Big note name card used by the listening modes. */
@Composable
fun NoteCard(name: String?, accent: Color, modifier: Modifier, extra: @Composable () -> Unit) {
    Column(
        modifier
            .background(Color(0xFF1F1F1F), RoundedCornerShape(16.dp))
            .border(3.dp, accent, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (name == null) {
            Text("?", fontSize = 72.sp, color = MutedGray)
        } else {
            Text(name, fontSize = if (name.length <= 2) 88.sp else 52.sp, fontWeight = FontWeight.Bold)
            extra()
        }
    }
}

/** ○●○: [count] dots, [filled] ones (0 = lowest) drawn solid. */
@Composable
fun OctaveDots(count: Int, filled: Set<Int>, fillColor: Color = Color.White) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(18.dp)
                    .background(if (i in filled) fillColor else Color.Transparent, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/**
 * After a round, show every place the note lives in the position; the wanted octave in color.
 * After a correct answer, the pitch you actually played gets a white ring.
 */
private fun revealMarkers(target: FindTarget?, feedback: FindFeedback?, region: FretRegion): List<FretMarker> {
    if (target == null || (feedback !is FindFeedback.Correct && feedback != FindFeedback.TimeUp)) return emptyList()
    val color = if (feedback is FindFeedback.Correct) TargetGreen else MissOrange
    val played = (feedback as? FindFeedback.Correct)?.playedMidi
    return region.positionsOf(target.pitchClass).map { pos ->
        val midi = region.tuning.midiAt(pos)
        val wanted = target.midi == null || target.midi == midi
        FretMarker(
            pos, if (wanted) color else Color(0xFF7A7A7A), Notes.name(midi, withOctave = false),
            ring = if (midi == played) Color.White else null,
        )
    }
}

private fun message(target: FindTarget?, feedback: FindFeedback?): Pair<String, Color> {
    val name = target?.let { Notes.fullName(it.pitchClass, withOctave = false) }
    val which = target?.octaveWord()?.let { " ($it)" } ?: ""
    return when (feedback) {
        null -> "Press Start, then play each note shown, staying inside the position." to Color.White
        FindFeedback.Listening -> "Find $name$which and play it!" to Color.White
        is FindFeedback.Wrong -> when (feedback.attempt) {
            Attempt.WRONG_OCTAVE -> "Right note, wrong octave. Look for $name$which"
            Attempt.OUTSIDE_REGION -> "That $name is outside this position."
            else -> "That was ${Notes.fullName(feedback.playedMidi, withOctave = false)}, keep looking!"
        } to WrongRed
        is FindFeedback.Correct ->
            "Correct in ${formatSeconds(feedback.millis)}! You played ${Notes.name(feedback.playedMidi)} (ringed)." to TargetGreen
        FindFeedback.TimeUp -> "Time's up! Here's where $name is." to MissOrange
    }
}

@Composable
fun FindNoteSettingsContent(settings: FindNoteSettings, onChange: (FindNoteSettings) -> Unit) {
    val position = HandPosition(settings.position)
    SettingSlider(
        "Position ${settings.position} (frets ${position.frets.first}–${position.frets.last})",
        settings.position, HandPosition.MIN..HandPosition.MAX,
    ) { onChange(settings.copy(position = it)) }
    SettingSwitch(
        "Specific octave",
        "Dots under the note tell you which one: ●○○ lowest, ○●○ middle, ○○● highest in the position",
        settings.exactOctave,
    ) { onChange(settings.copy(exactOctave = it)) }
    SettingSwitch("Natural notes only", "Skip sharps and flats", settings.naturalsOnly) {
        onChange(settings.copy(naturalsOnly = it))
    }
    SessionSettings(settings.session, { onChange(settings.copy(session = it)) })
}
