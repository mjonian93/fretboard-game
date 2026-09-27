package com.fretboardtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fretboardtrainer.game.FindNoteSettings
import com.fretboardtrainer.game.FindNoteState
import com.fretboardtrainer.game.FindTarget
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.PitchReading
import com.fretboardtrainer.music.Tuning
import kotlin.math.log10
import kotlin.math.roundToInt

private val OCTAVE_WORDS = mapOf(
    1 to listOf("the only one"),
    2 to listOf("the lower one", "the higher one"),
    3 to listOf("the lowest one", "the middle one", "the highest one"),
)

private fun FindTarget.octaveWord(): String? =
    if (midi == null) null else OCTAVE_WORDS[octaveCount]?.getOrNull(octaveIndex) ?: "#${octaveIndex + 1} of $octaveCount"

@Composable
fun FindNoteScreen(
    state: FindNoteState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val position = HandPosition(state.settings.position)
    val target = state.target
    val feedback = state.feedback

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        GameHeader("Find the note", state.score, state.running, state.timeLeft, onBack, onStart, onStop, onOpenSettings)

        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NoteCard(target, feedback, Modifier.width(190.dp).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
                Text(
                    "Position ${position.index}: index finger on fret ${position.index} " +
                        "(frets ${position.frets.first}–${position.frets.last})",
                    color = MutedGray,
                )
                Fretboard(FRET_COUNT, position.frets, revealMarkers(target, feedback, position), Modifier.weight(1f).fillMaxWidth())
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val (message, color) = message(state, target, feedback)
            Text(message, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.running) {
                MicLevelMeter(state.micLevel, Modifier.padding(end = 12.dp))
                Text(heardText(state.heard), color = Color(0xFFB0B0B0))
            }
        }
    }
}

/** Mic loudness on a -60..0 dB scale, so you can see the app hears you. */
@Composable
private fun MicLevelMeter(level: Float, modifier: Modifier) {
    val db = if (level > 0f) 20 * log10(level) else -60f
    val fraction = ((db + 60f) / 60f).coerceIn(0f, 1f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Mic ", color = MutedGray)
        Box(Modifier.width(90.dp).height(10.dp).background(Color(0xFF333333), RoundedCornerShape(5.dp))) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction)
                    .background(if (db > -45f) TargetGreen else MutedGray, RoundedCornerShape(5.dp)),
            )
        }
    }
}

/** The big note name, with dots for which octave is wanted: ○●○ = the middle one. */
@Composable
private fun NoteCard(target: FindTarget?, feedback: FindFeedback?, modifier: Modifier) {
    val accent = when (feedback) {
        is FindFeedback.Correct -> TargetGreen
        FindFeedback.TimeUp -> MissOrange
        is FindFeedback.Wrong -> WrongRed
        else -> Color(0xFF3A3A3A)
    }
    Column(
        modifier
            .background(Color(0xFF1F1F1F), RoundedCornerShape(16.dp))
            .border(3.dp, accent, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (target == null) {
            Text("?", fontSize = 72.sp, color = MutedGray)
            return@Column
        }
        Text(Notes.fullName(target.pitchClass, withOctave = false), fontSize = if (Notes.isNatural(target.pitchClass)) 88.sp else 52.sp, fontWeight = FontWeight.Bold)
        if (target.midi != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                repeat(target.octaveCount) { i ->
                    val filled = i == target.octaveIndex
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(if (filled) Color.White else Color.Transparent, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
            }
            Text(target.octaveWord() ?: "", color = MutedGray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("any octave", color = MutedGray, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** After a round, show every place the note lives in the position; the wanted octave in color. */
private fun revealMarkers(target: FindTarget?, feedback: FindFeedback?, position: HandPosition): List<FretMarker> {
    if (target == null || (feedback !is FindFeedback.Correct && feedback != FindFeedback.TimeUp)) return emptyList()
    val color = if (feedback is FindFeedback.Correct) TargetGreen else MissOrange
    return position.positionsOf(target.pitchClass).map { pos ->
        val midi = Tuning.midiAt(pos)
        val wanted = target.midi == null || target.midi == midi
        FretMarker(pos, if (wanted) color else Color(0xFF7A7A7A), Notes.name(midi, withOctave = false))
    }
}

private fun message(state: FindNoteState, target: FindTarget?, feedback: FindFeedback?): Pair<String, Color> {
    state.error?.let { return it to WrongRed }
    val name = target?.let { Notes.fullName(it.pitchClass, withOctave = false) }
    val which = target?.octaveWord()?.let { " ($it)" } ?: ""
    return when (feedback) {
        null -> "Press Start, then play each note shown, staying inside the position." to Color.White
        FindFeedback.Listening -> "Find $name$which and play it!" to Color.White
        is FindFeedback.Wrong -> when (feedback.attempt) {
            Attempt.WrongOctave -> "Right note, wrong octave. Look for $name$which"
            Attempt.OutsidePosition -> "That $name is outside this position."
            else -> "That was ${Notes.fullName(feedback.playedMidi, withOctave = false)}, keep looking!"
        } to WrongRed
        is FindFeedback.Correct -> "Correct! Here's every $name in this position." to TargetGreen
        FindFeedback.TimeUp -> "Time's up! Here's where $name is." to MissOrange
    }
}

private fun heardText(reading: PitchReading?): String {
    if (reading == null) return "Hearing: …"
    val cents = reading.cents.roundToInt()
    return "Hearing: ${Notes.name(reading.midi)} (${if (cents >= 0) "+" else ""}$cents¢)"
}

@Composable
fun FindNoteSettingsDialog(settings: FindNoteSettings, onChange: (FindNoteSettings) -> Unit, onDismiss: () -> Unit) {
    val position = HandPosition(settings.position)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Find the note: settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                SettingSlider("Time per note: ${settings.secondsPerNote} s", settings.secondsPerNote, 3..20) {
                    onChange(settings.copy(secondsPerNote = it))
                }
            }
        },
    )
}
