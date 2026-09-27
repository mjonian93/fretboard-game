package com.fretboardtrainer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fretboardtrainer.game.FindAllFeedback
import com.fretboardtrainer.game.FindAllRound
import com.fretboardtrainer.game.FindAllSettings
import com.fretboardtrainer.game.SessionState
import com.fretboardtrainer.music.FRET_COUNT
import com.fretboardtrainer.music.HandPosition
import com.fretboardtrainer.music.Notes
import com.fretboardtrainer.music.Tuning

@Composable
fun FindAllContent(
    round: FindAllRound,
    session: SessionState,
    settings: FindAllSettings,
    tuning: Tuning,
    mirrored: Boolean,
    gateDb: Float,
    modifier: Modifier = Modifier,
) {
    val region = settings.region(tuning)
    val feedback = round.feedback
    val pc = round.pitchClass
    val finished = feedback is FindAllFeedback.Done || feedback == FindAllFeedback.TimeUp

    Column(modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val accent = when (feedback) {
                is FindAllFeedback.Done -> TargetGreen
                FindAllFeedback.TimeUp -> MissOrange
                is FindAllFeedback.Wrong -> WrongRed
                is FindAllFeedback.Found -> TargetGreen
                else -> Color(0xFF3A3A3A)
            }
            NoteCard(pc?.let { Notes.fullName(it, withOctave = false) }, accent, Modifier.width(190.dp).fillMaxHeight()) {
                val found = round.octaves.withIndex().filter { it.value in round.found }.map { it.index }.toSet()
                OctaveDots(round.octaves.size, found, TargetGreen)
                Text("${round.found.size} of ${round.octaves.size} found", color = MutedGray, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
                val scope = if (settings.position == 0) {
                    "Whole neck, frets 0–${settings.maxFret}"
                } else {
                    val p = HandPosition(settings.position)
                    "Position ${p.index} (frets ${p.frets.first}–${p.frets.last})"
                }
                Text("$scope · ${tuning.name}", color = MutedGray)
                // Found pitches light up as you go; at the end the missed ones are revealed in orange.
                val markers = if (pc == null) emptyList() else region.positionsOf(pc).mapNotNull { pos ->
                    val midi = tuning.midiAt(pos)
                    val label = Notes.name(midi, withOctave = false)
                    when {
                        midi in round.found -> FretMarker(pos, TargetGreen, label)
                        finished -> FretMarker(pos, MissOrange, label)
                        else -> null
                    }
                }
                Fretboard(FRET_COUNT, region.frets, markers, Modifier.weight(1f).fillMaxWidth(), mirrored)
            }
        }
        val name = pc?.let { Notes.fullName(it, withOctave = false) }
        val (message, color) = when (feedback) {
            null -> "Press Start, then play every octave of each note shown." to Color.White
            FindAllFeedback.Listening -> "Play every $name you can find!" to Color.White
            is FindAllFeedback.Found -> "${Notes.name(feedback.midi)} found! ${round.octaves.size - round.found.size} to go." to TargetGreen
            is FindAllFeedback.AlreadyFound -> "You already found ${Notes.name(feedback.midi)}; look for another octave." to MissOrange
            is FindAllFeedback.Wrong -> (if (feedback.outside) "That $name is outside the area." else "That was ${Notes.fullName(feedback.playedMidi, false)}.") to WrongRed
            is FindAllFeedback.Done -> "All found in ${formatSeconds(feedback.millis)}!" to TargetGreen
            FindAllFeedback.TimeUp -> "Time's up! The orange ones were missing." to MissOrange
        }
        ListeningStatus(message, color, session, gateDb)
    }
}

@Composable
fun FindAllSettingsContent(settings: FindAllSettings, onChange: (FindAllSettings) -> Unit) {
    SettingSwitch("Whole neck", "Off: practise inside one hand position", settings.position == 0) {
        onChange(settings.copy(position = if (it) 0 else 2))
    }
    if (settings.position == 0) {
        SettingSlider("Frets: open to ${settings.maxFret}", settings.maxFret, 3..FRET_COUNT) { onChange(settings.copy(maxFret = it)) }
    } else {
        val p = HandPosition(settings.position)
        SettingSlider("Position ${p.index} (frets ${p.frets.first}–${p.frets.last})", settings.position, HandPosition.MIN..HandPosition.MAX) {
            onChange(settings.copy(position = it))
        }
    }
    SettingSwitch("Natural notes only", "Skip sharps and flats", settings.naturalsOnly) {
        onChange(settings.copy(naturalsOnly = it))
    }
    SessionSettings(settings.session, { onChange(settings.copy(session = it)) }, perWhat = "octave")
}
