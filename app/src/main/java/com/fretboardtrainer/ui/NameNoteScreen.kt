package com.fretboardtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fretboardtrainer.game.NameNoteRound
import com.fretboardtrainer.game.NameNoteSettings
import com.fretboardtrainer.game.NameResult
import com.fretboardtrainer.game.SessionState
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.STRING_COUNT
import com.fretboardtrainer.music.Tuning

@Composable
fun NameNoteContent(
    round: NameNoteRound,
    session: SessionState,
    settings: NameNoteSettings,
    tuning: Tuning,
    mirrored: Boolean,
    onAnswer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = round.target
    val result = round.result
    val targetClass = target?.let { Notes.pitchClass(tuning.midiAt(it)) }

    Column(modifier) {
        val markers = target?.let {
            listOf(
                FretMarker(
                    position = it,
                    color = if (result == null || result is NameResult.Correct) TargetGreen else MissOrange,
                    label = if (result != null) Notes.name(tuning.midiAt(it), withOctave = false) else null,
                    pulsing = result == null,
                    ring = if (result is NameResult.Wrong) WrongRed else null,
                ),
            )
        } ?: emptyList()
        Fretboard(FRET_COUNT, 0..settings.maxFret, markers, Modifier.weight(1f).fillMaxWidth(), mirrored)

        val (message, color) = when (result) {
            null -> (if (session.running) "Which note is the green one?" else "Press Start, then tap the name of each note that lights up.") to Color.White
            is NameResult.Correct -> "Correct! ${Notes.fullName(result.pitchClass, false)} in ${formatSeconds(result.millis)}" to TargetGreen
            is NameResult.Wrong -> "Not ${Notes.fullName(result.chosenPitchClass, false)}, it's ${targetClass?.let { Notes.fullName(it, false) }}" to WrongRed
            NameResult.TimeUp -> "Time's up! It was ${targetClass?.let { Notes.fullName(it, false) }}" to MissOrange
        }
        Text(message, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))

        NoteButtons(
            naturalsOnly = settings.naturalsOnly,
            enabled = session.running && target != null && result == null,
            highlight = { pc ->
                when {
                    result == null -> null
                    pc == targetClass -> if (result is NameResult.Correct) TargetGreen else MissOrange
                    result is NameResult.Wrong && pc == result.chosenPitchClass -> WrongRed
                    else -> null
                }
            },
            onAnswer = onAnswer,
        )
    }
}

/** Naturals on the bottom row; sharps above, sitting between their neighbours like piano keys. */
@Composable
private fun NoteButtons(naturalsOnly: Boolean, enabled: Boolean, highlight: (Int) -> Color?, onAnswer: (Int) -> Unit) {
    val naturals = listOf(0, 2, 4, 5, 7, 9, 11)
    val sharps = listOf(1, 3, null, 6, 8, 10)

    @Composable
    fun NoteButton(pc: Int, modifier: Modifier) {
        val color = highlight(pc)
        Button(
            onClick = { onAnswer(pc) },
            enabled = enabled || color != null,
            modifier = modifier.padding(horizontal = 3.dp).height(44.dp),
            colors = if (color != null) {
                ButtonDefaults.buttonColors(
                    containerColor = color, contentColor = Color.Black,
                    disabledContainerColor = color, disabledContentColor = Color.Black,
                )
            } else if (Notes.isNatural(pc)) {
                ButtonDefaults.buttonColors(containerColor = Color(0xFFEDEDED), contentColor = Color.Black)
            } else {
                ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B), contentColor = Color.White)
            },
        ) {
            Text(Notes.fullName(pc, withOctave = false), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!naturalsOnly) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(0.5f))
                for (pc in sharps) {
                    if (pc == null) Spacer(Modifier.weight(1f)) else NoteButton(pc, Modifier.weight(1f))
                }
                Spacer(Modifier.weight(0.5f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (pc in naturals) NoteButton(pc, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameNoteSettingsContent(settings: NameNoteSettings, tuning: Tuning, onChange: (NameNoteSettings) -> Unit) {
    SettingSlider("Frets: open to ${settings.maxFret}", settings.maxFret, 1..FRET_COUNT) {
        onChange(settings.copy(maxFret = it))
    }
    Text("Strings", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (string in 1..STRING_COUNT) {
            val selected = string in settings.strings
            FilterChip(
                selected = selected,
                onClick = {
                    val strings = if (selected) settings.strings - string else settings.strings + string
                    if (strings.isNotEmpty()) onChange(settings.copy(strings = strings))
                },
                label = { Text("$string (${tuning.stringLabel(string)})") },
            )
        }
    }
    SettingSwitch("Natural notes only", "Skip sharps and flats", settings.naturalsOnly) {
        onChange(settings.copy(naturalsOnly = it))
    }
    SettingSwitch("Play the note", "Hear each note when it lights up", settings.playSound) {
        onChange(settings.copy(playSound = it))
    }
    SessionSettings(settings.session, { onChange(settings.copy(session = it)) })
}
